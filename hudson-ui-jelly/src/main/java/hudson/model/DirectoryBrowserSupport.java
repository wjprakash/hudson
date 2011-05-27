/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.FilePathExt;
import hudson.util.IOException2;
import hudson.FilePathExt.FileCallable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.HttpResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;

/**
 * Has convenience methods to serve file system.
 *
 * <p>
 * This object can be used in a mix-in style to provide a directory browsing capability
 * to a {@link ModelObject}. 
 *
 * @author Kohsuke Kawaguchi
 */
public final class DirectoryBrowserSupport extends DirectoryBrowserSupportExt implements HttpResponse {


    /**
     * @deprecated as of 1.297
     *      Use {@link #DirectoryBrowserSupportExt(ModelObject, FilePathExt, String, String, boolean)}
     */
    public DirectoryBrowserSupport(ModelObject owner, String title) {
        super(owner, title);
    }

    /**
     * @param owner
     *      The parent model object under which the directory browsing is added.
     * @param base
     *      The root of the directory that's bound to URL.
     * @param title
     *      Used in the HTML caption. 
     * @param icon
     *      The icon file name, like "folder.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     */
    public DirectoryBrowserSupport(ModelObject owner, FilePathExt base, String title, String icon, boolean serveDirIndex) {
         super(owner, base, title, icon, serveDirIndex);
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        try {
            serveFile(req,rsp, base, icon, serveDirIndex);
        } catch (InterruptedException e) {
            throw new IOException2("interrupted",e);
        }
    }

    private String getPath(StaplerRequest req) {
        String path = req.getRestOfPath();
        if(path.length()==0)
            path = "/";
        return path;
    }
    
    /**
     * Serves a file from the file system (Maps the URL to a directory in a file system.)
     *
     * @param icon
     *      The icon file name, like "folder-open.gif"
     * @param serveDirIndex
     *      True to generate the directory index.
     *      False to serve "index.html"
     * @deprecated as of 1.297
     *      Instead of calling this method explicitly, just return the {@link DirectoryBrowserSupportExt} object
     *      from the {@code doXYZ} method and let Stapler generate a response for you.
     */
    public void serveFile(StaplerRequest req, StaplerResponse rsp, FilePathExt root, String icon, boolean serveDirIndex) throws IOException, ServletException, InterruptedException {
        // handle form submission
        String pattern = req.getParameter("pattern");
        if(pattern==null)
            pattern = req.getParameter("path"); // compatibility with HudsonExt<1.129
        if(pattern!=null) {
            rsp.sendRedirect2(pattern);
            return;
        }

        String path = getPath(req);
        if(path.replace('\\','/').indexOf("/../")!=-1) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // split the path to the base directory portion "abc/def/ghi" which doesn't include any wildcard,
        // and the GLOB portion "**/*.xml" (the rest)
        StringBuilder _base = new StringBuilder();
        StringBuilder _rest = new StringBuilder();
        int restSize=-1; // number of ".." needed to go back to the 'base' level.
        boolean zip=false;  // if we are asked to serve a zip file bundle
        boolean plain = false; // if asked to serve a plain text directory listing
        {
            boolean inBase = true;
            StringTokenizer pathTokens = new StringTokenizer(path,"/");
            while(pathTokens.hasMoreTokens()) {
                String pathElement = pathTokens.nextToken();
                // Treat * and ? as wildcard unless they match a literal filename
                if((pathElement.contains("?") || pathElement.contains("*"))
                        && inBase && !(new FilePathExt(root, (_base.length() > 0 ? _base + "/" : "") + pathElement).exists()))
                    inBase = false;
                if(pathElement.equals("*zip*")) {
                    // the expected syntax is foo/bar/*zip*/bar.zip
                    // the last 'bar.zip' portion is to causes browses to set a good default file name.
                    // so the 'rest' portion ends here.
                    zip=true;
                    break;
                }
                if(pathElement.equals("*plain*")) {
                    plain = true;
                    break;
                }

                StringBuilder sb = inBase?_base:_rest;
                if(sb.length()>0)   sb.append('/');
                sb.append(pathElement);
                if(!inBase)
                    restSize++;
            }
        }
        restSize = Math.max(restSize,0);
        String base = _base.toString();
        String rest = _rest.toString();

        // this is the base file/directory
        FilePathExt baseFile = new FilePathExt(root,base);

        if(baseFile.isDirectory()) {
            if(zip) {
                rsp.setContentType("application/zip");
                baseFile.zip(rsp.getOutputStream(),rest);
                return;
            }
            if (plain) {
                rsp.setContentType("text/plain;charset=UTF-8");
                OutputStream os = rsp.getOutputStream();
                try {
                    for (String kid : baseFile.act(new SimpleChildList())) {
                        os.write(kid.getBytes("UTF-8"));
                        os.write('\n');
                    }
                    os.flush();
                } finally {
                    os.close();
                }
                return;
            }

            if(rest.length()==0) {
                // if the target page to be displayed is a directory and the path doesn't end with '/', redirect
                StringBuffer reqUrl = req.getRequestURL();
                if(reqUrl.charAt(reqUrl.length()-1)!='/') {
                    rsp.sendRedirect2(reqUrl.append('/').toString());
                    return;
                }
            }

            FileCallable<List<List<Path>>> glob = null;

            if(rest.length()>0) {
                // the rest is Ant glob pattern
                glob = new PatternScanner(rest,createBackRef(restSize));
            } else
            if(serveDirIndex) {
                // serve directory index
                glob = new ChildPathBuilder();
            }

            if(glob!=null) {
                // serve glob
                req.setAttribute("it", this);
                List<Path> parentPaths = buildParentPath(base,restSize);
                req.setAttribute("parentPath",parentPaths);
                req.setAttribute("backPath", createBackRef(restSize));
                req.setAttribute("topPath", createBackRef(parentPaths.size()+restSize));
                req.setAttribute("files", baseFile.act(glob));
                req.setAttribute("icon", icon);
                req.setAttribute("path", path);
                req.setAttribute("pattern",rest);
                req.setAttribute("dir", baseFile);
                req.getView(this,"dir.jelly").forward(req, rsp);
                return;
            }

            // convert a directory service request to a single file service request by serving
            // 'index.html'
            baseFile = baseFile.child(indexFileName);
        }

        //serve a single file
        if(!baseFile.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean view = rest.equals("*view*");

        if(rest.equals("*fingerprint*")) {
            rsp.forward(HudsonExt.getInstance().getFingerprint(baseFile.digest()),"/",req);
            return;
        }

        ContentInfo ci = baseFile.act(new ContentInfo());

        if(LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Serving "+baseFile+" with lastModified="+ci.lastModified+", contentLength="+ci.contentLength);

        InputStream in = baseFile.read();
        if (view) {
            // for binary files, provide the file name for download
            rsp.setHeader("Content-Disposition", "inline; filename=" + baseFile.getName());

            // pseudo file name to let the Stapler set text/plain
            rsp.serveFile(req, in, ci.lastModified, -1, ci.contentLength, "plain.txt");
        } else {
            rsp.serveFile(req, in, ci.lastModified, -1, ci.contentLength, baseFile.getName() );
        }
    }

    
}

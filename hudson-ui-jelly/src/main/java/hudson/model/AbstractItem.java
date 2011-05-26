/**
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@Oracle.com
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

import org.kohsuke.stapler.WebMethod;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.util.AtomicFileWriter;
import hudson.util.IOException2;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.kohsuke.stapler.HttpDeletable;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 *
 * @author Winston Prakash
 */
@ExportedBean
public abstract class AbstractItem extends AbstractItemExt implements HttpDeletable {

    protected AbstractItem(ItemGroup parent, String name) {
        super(parent, name);
        doSetName(name);
    }

    @Exported(visibility = 999)
    @Override
    public String getName() {
        return super.getName();
    }

    @Exported
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

    @Exported
    public String getDescription() {
        return super.getDescription();
    }

    public final String getUrl() {
        // try to stick to the current view if possible
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req, this);
            if (seed != null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length() + 1) + '/';
            }
        }

        // otherwise compute the path normally
        return getParent().getUrl() + getShortUrl();
    }

    @Exported(visibility = 999, name = "url")
    public final String getAbsoluteUrl() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request == null) {
            throw new IllegalStateException("Not processing a HTTP request");
        }
        return Util.encode(Hudson.getInstance().getRootUrl() + getUrl());
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     */
    @CLIMethod(name = "delete-job")
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        requirePOST();
        delete();
        if (rsp != null) // null for CLI
        {
            rsp.sendRedirect2(req.getContextPath() + "/" + getParent().getUrl());
        }
    }

    public void delete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        try {
            doDoDelete(req, rsp);
        } catch (InterruptedException e) {
            // TODO: allow this in Stapler
            throw new ServletException(e);
        }
    }

    /**
     * Accepts <tt>config.xml</tt> submission, as well as serve it.
     */
    @WebMethod(name = "config.xml")
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if (req.getMethod().equals("GET")) {
            // read
            checkPermission(EXTENDED_READ);
            rsp.setContentType("application/xml;charset=UTF-8");
            getConfigFile().writeRawTo(rsp.getWriter());
            return;
        }
        if (req.getMethod().equals("POST")) {
            // submission
            checkPermission(CONFIGURE);
            XmlFile configXmlFile = getConfigFile();
            AtomicFileWriter out = new AtomicFileWriter(configXmlFile.getFile());
            try {
                try {
                    // this allows us to use UTF-8 for storing data,
                    // plus it checks any well-formedness issue in the submitted
                    // data
                    Transformer t = TransformerFactory.newInstance().newTransformer();
                    t.transform(new StreamSource(req.getReader()),
                            new StreamResult(out));
                    out.close();
                } catch (TransformerException e) {
                    throw new IOException2("Failed to persist configuration.xml", e);
                }

                // try to reflect the changes by reloading
                new XmlFile(Items.XSTREAM, out.getTemporaryFile()).unmarshal(this);
                onLoad(getParent(), getRootDir().getName());

                // if everything went well, commit this new version
                out.commit();
            } finally {
                out.abort(); // don't leave anything behind
            }
            return;
        }

        // huh?
        rsp.sendError(SC_BAD_REQUEST);
    }
}

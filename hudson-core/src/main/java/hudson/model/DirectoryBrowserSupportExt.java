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
import hudson.UtilExt;
import hudson.FilePathExt.FileCallable;
import hudson.remoting.VirtualChannel;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.DirectoryScanner;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * Has convenience methods to serve file system.
 *
 * <p>
 * This object can be used in a mix-in style to provide a directory browsing capability
 * to a {@link ModelObject}. 
 *
 * @author Kohsuke Kawaguchi
 */
public class DirectoryBrowserSupportExt {

    public final ModelObject owner;
    
    public final String title;

    protected final FilePathExt base;
    protected final String icon;
    protected final boolean serveDirIndex;
    protected String indexFileName = "index.html";

    /**
     * @deprecated as of 1.297
     *      Use {@link #DirectoryBrowserSupportExt(ModelObject, FilePathExt, String, String, boolean)}
     */
    public DirectoryBrowserSupportExt(ModelObject owner, String title) {
        this(owner,null,title,null,false);
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
    public DirectoryBrowserSupportExt(ModelObject owner, FilePathExt base, String title, String icon, boolean serveDirIndex) {
        this.owner = owner;
        this.base = base;
        this.title = title;
        this.icon = icon;
        this.serveDirIndex = serveDirIndex;
    }


    /**
     * If the directory is requested but the directory listing is disabled, a file of this name
     * is served. By default it's "index.html".
     * @since 1.312
     */
    public void setIndexFileName(String fileName) {
        this.indexFileName = fileName;
    }

     

    protected static final class ContentInfo implements FileCallable<ContentInfo> {
        long contentLength;
        long lastModified;

        public ContentInfo invoke(File f, VirtualChannel channel) throws IOException {
            contentLength = f.length();
            lastModified = f.lastModified();
            return this;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Builds a list of {@link Path} that represents ancestors
     * from a string like "/foo/bar/zot".
     */
    protected List<Path> buildParentPath(String pathList, int restSize) {
        List<Path> r = new ArrayList<Path>();
        StringTokenizer tokens = new StringTokenizer(pathList, "/");
        int total = tokens.countTokens();
        int current=1;
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            r.add(new Path(createBackRef(total-current+restSize),token,true,0, true));
            current++;
        }
        return r;
    }

    protected static String createBackRef(int times) {
        if(times==0)    return "./";
        StringBuilder buf = new StringBuilder(3*times);
        for(int i=0; i<times; i++ )
            buf.append("../");
        return buf.toString();
    }

    /**
     * Represents information about one file or folder.
     */
    public static final class Path implements Serializable {
        /**
         * Relative URL to this path from the current page.
         */
        private final String href;
        /**
         * Name of this path. Just the file name portion.
         */
        private final String title;

        private final boolean isFolder;

        /**
         * File size, or null if this is not a file.
         */
        private final long size;
        
        /**
         * If the current user can read the file. 
         */
        private final boolean isReadable;

        public Path(String href, String title, boolean isFolder, long size, boolean isReadable) {
            this.href = href;
            this.title = title;
            this.isFolder = isFolder;
            this.size = size;
            this.isReadable = isReadable;
        }

        public boolean isFolder() {
            return isFolder;
        }
        
        public boolean isReadable() {
            return isReadable;
        }

        public String getHref() {
            return href;
        }

        public String getTitle() {
            return title;
        }

        public String getIconName() {
            if (isReadable)
                return isFolder?"folder.gif":"text.gif";
            else
                return isFolder?"folder-error.gif":"text-error.gif";
        }

        public long getSize() {
            return size;
        }

        private static final long serialVersionUID = 1L;
    }



    private static final class FileComparator implements Comparator<File> {
        public int compare(File lhs, File rhs) {
            // directories first, files next
            int r = dirRank(lhs)-dirRank(rhs);
            if(r!=0) return r;
            // otherwise alphabetical
            return lhs.getName().compareTo(rhs.getName());
        }

        private int dirRank(File f) {
            if(f.isDirectory())     return 0;
            else                    return 1;
        }
    }

    /**
     * Simple list of names of children of a folder.
     * Subfolders will have a trailing slash appended.
     */
    protected static final class SimpleChildList implements FileCallable<List<String>> {
        private static final long serialVersionUID = 1L;
        public List<String> invoke(File f, VirtualChannel channel) throws IOException {
            List<String> r = new ArrayList<String>();
            String[] kids = f.list(); // no need to sort
            for (String kid : kids) {
                if (new File(f, kid).isDirectory()) {
                    r.add(kid + "/");
                } else {
                    r.add(kid);
                }
            }
            return r;
        }
    }

    /**
     * Builds a list of list of {@link Path}. The inner
     * list of {@link Path} represents one child item to be shown
     * (this mechanism is used to skip empty intermediate directory.)
     */
    protected static final class ChildPathBuilder implements FileCallable<List<List<Path>>> {
        public List<List<Path>> invoke(File cur, VirtualChannel channel) throws IOException {
            List<List<Path>> r = new ArrayList<List<Path>>();

            File[] files = cur.listFiles();
            if (files != null) {
                Arrays.sort(files,new FileComparator());
    
                for( File f : files ) {
                    Path p = new Path(UtilExt.rawEncode(f.getName()),f.getName(),f.isDirectory(),f.length(), f.canRead());
                    if(!f.isDirectory()) {
                        r.add(Collections.singletonList(p));
                    } else {
                        // find all empty intermediate directory
                        List<Path> l = new ArrayList<Path>();
                        l.add(p);
                        String relPath = UtilExt.rawEncode(f.getName());
                        while(true) {
                            // files that don't start with '.' qualify for 'meaningful files', nor SCM related files
                            File[] sub = f.listFiles(new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    return !name.startsWith(".") && !name.equals("CVS") && !name.equals(".svn");
                                }
                            });
                            if(sub==null || sub.length!=1 || !sub[0].isDirectory())
                                break;
                            f = sub[0];
                            relPath += '/'+UtilExt.rawEncode(f.getName());
                            l.add(new Path(relPath,f.getName(),true,0, f.canRead()));
                        }
                        r.add(l);
                    }
                }
            }

            return r;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Runs ant GLOB against the current {@link FilePathExt} and returns matching
     * paths.
     */
    protected static class PatternScanner implements FileCallable<List<List<Path>>> {
        private final String pattern;
        /**
         * String like "../../../" that cancels the 'rest' portion. Can be "./"
         */
        private final String baseRef;

        public PatternScanner(String pattern,String baseRef) {
            this.pattern = pattern;
            this.baseRef = baseRef;
        }

        public List<List<Path>> invoke(File baseDir, VirtualChannel channel) throws IOException {
            FileSet fs = UtilExt.createFileSet(baseDir,pattern);
            DirectoryScanner ds = fs.getDirectoryScanner();
            String[] files = ds.getIncludedFiles();

            if (files.length > 0) {
                List<List<Path>> r = new ArrayList<List<Path>>(files.length);
                for (String match : files) {
                    List<Path> file = buildPathList(baseDir, new File(baseDir,match));
                    r.add(file);
                }
                return r;
            }

            return null;
        }

        /**
         * Builds a path list from the current workspace directory down to the specified file path.
         */
        private List<Path> buildPathList(File baseDir, File filePath) throws IOException {
            List<Path> pathList = new ArrayList<Path>();
            StringBuilder href = new StringBuilder(baseRef);

            buildPathList(baseDir, filePath, pathList, href);
            return pathList;
        }

        /**
         * Builds the path list and href recursively top-down.
         */
        private void buildPathList(File baseDir, File filePath, List<Path> pathList, StringBuilder href) throws IOException {
            File parent = filePath.getParentFile();
            if (!baseDir.equals(parent)) {
                buildPathList(baseDir, parent, pathList, href);
            }

            href.append(UtilExt.rawEncode(filePath.getName()));
            if (filePath.isDirectory()) {
                href.append("/");
            }

            Path path = new Path(href.toString(), filePath.getName(), filePath.isDirectory(), filePath.length(), filePath.canRead());
            pathList.add(path);
        }

        private static final long serialVersionUID = 1L;
    }

    protected static final Logger LOGGER = Logger.getLogger(DirectoryBrowserSupportExt.class.getName());
}

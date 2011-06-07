/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.TextFile;
import hudson.util.TimeUnit2;
import hudson.util.QuotedStringTokenizer;

import java.io.File;
import java.io.IOException;

import net.sf.json.JSONObject;


/**
 * Service for plugins to periodically retrieve update data files
 * (like the one in the update center) through browsers.
 *
 * <p>
 * Because the retrieval of the file goes through XmlHttpRequest,
 * we cannot reliably pass around binary.
 *
 * @author Kohsuke Kawaguchi
 */
//@Extension
public class DownloadServiceExt extends PageDecorator {
    public DownloadServiceExt() {
        super(DownloadServiceExt.class);
    }
    
    /**
     * Gets {@link DownloadableExt} by its ID.
     * Used to bind them to URL.
     */
    public DownloadableExt getById(String id) {
        for (DownloadableExt d : DownloadableExt.all())
            if(d.getId().equals(id))
                return d;
        return null;
    }
   

    /**
     * Represents a periodically updated JSON data file obtained from a remote URL.
     *
     * <p>
     * This mechanism is one of the basis of the update center, which involves fetching
     * up-to-date data file.
     *
     * @since 1.305
     */
    public static class DownloadableExt implements ExtensionPoint {
        protected final String id;
        private final String url;
        private final long interval;
        protected volatile long due=0;
        protected volatile long lastAttempt = Long.MIN_VALUE;

        /**
         *
         * @param url
         *      URL relative to {@link UpdateCenter#getUrl()}.
         *      So if this string is "foo.json", the ultimate URL will be
         *      something like "https://hudson.java.net/foo.json"
         *
         *      For security and privacy reasons, we don't allow the retrieval
         *      from random locations.
         */
        public DownloadableExt(String id, String url, long interval) {
            this.id = id;
            this.url = url;
            this.interval = interval;
        }

        /**
         * Uses the class name as an ID.
         */
        public DownloadableExt(Class id) {
            this(id.getName().replace('$','.'));
        }

        public DownloadableExt(String id) {
            this(id,id+".json");
        }

        public DownloadableExt(String id, String url) {
            this(id,url,TimeUnit2.DAYS.toMillis(1));
        }

        public String getId() {
            return id;
        }

        /**
         * URL to download.
         */
        public String getUrl() {
            return HudsonExt.getInstance().getUpdateCenter().getDefaultBaseUrl()+"updates/"+url;
        }

        /**
         * How often do we retrieve the new image?
         *
         * @return
         *      number of milliseconds between retrieval.
         */
        public long getInterval() {
            return interval;
        }

        /**
         * This is where the retrieved file will be stored.
         */
        public TextFile getDataFile() {
            return new TextFile(new File(HudsonExt.getInstance().getRootDir(),"updates/"+id));
        }

        /**
         * When shall we retrieve this file next time?
         */
        public long getDue() {
            if(due==0)
                // if the file doesn't exist, this code should result
                // in a very small (but >0) due value, which should trigger
                // the retrieval immediately.
                due = getDataFile().file.lastModified()+interval;
            return due;
        }

        /**
         * Loads the current file into JSON and returns it, or null
         * if no data exists.
         */
        public JSONObject getData() throws IOException {
            TextFile df = getDataFile();
            if(df.exists())
                return JSONObject.fromObject(df.read());
            return null;
        }

        /**
         * Returns all the registered {@link DownloadableExt}s.
         */
        public static ExtensionList<DownloadableExt> all() {
            return HudsonExt.getInstance().getExtensionList(DownloadableExt.class);
        }

        /**
         * Returns the {@link DownloadableExt} that has the given ID.
         */
        public static DownloadableExt get(String id) {
            for (DownloadableExt d : all()) {
                if(d.id.equals(id))
                    return d;
            }
            return null;
        }

    }

    public static boolean neverUpdate = Boolean.getBoolean(DownloadServiceExt.class.getName()+".never");
}


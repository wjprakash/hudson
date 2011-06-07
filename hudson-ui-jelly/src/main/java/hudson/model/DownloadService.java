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

import hudson.Extension;
import hudson.util.IOUtils;
import hudson.util.QuotedStringTokenizer;
import hudson.util.TextFile;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
@Extension
public class DownloadService extends DownloadServiceExt {
    
    
    /**
     * Builds up an HTML fragment that starts all the download jobs.
     */
    public String generateFragment() {
    	if (neverUpdate) return "";
    	
        StringBuilder buf = new StringBuilder();
        if(HudsonExt.getInstance().hasPermission(HudsonExt.READ)) {
            long now = System.currentTimeMillis();
            for (DownloadableExt d : DownloadableExt.all()) {
                if(d.getDue()<now && d.lastAttempt+10*1000<now) {
                    buf.append("<script>")
                       .append("Behaviour.addLoadEvent(function() {")
                       .append("  downloadService.download(")
                       .append(QuotedStringTokenizer.quote(d.getId()))
                       .append(',')
                       .append(QuotedStringTokenizer.quote(d.getUrl()))
                       .append(',')
                       .append("{version:")
                       .append(QuotedStringTokenizer.quote(HudsonExt.VERSION))
                       .append('}')
                       .append(',')
                       .append(QuotedStringTokenizer.quote(Stapler.getCurrentRequest().getContextPath()+'/'+getUrl()+"/byId/"+d.getId()+"/postBack"))
                       .append(',')
                       .append("null);")
                       .append("});")
                       .append("</script>");
                    d.lastAttempt = now;
                }
            }
        }
        return buf.toString();
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
    public static class Downloadable extends DownloadServiceExt.DownloadableExt {
         
        private static final Logger LOGGER = Logger.getLogger(Downloadable.class.getName());
         
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
        public Downloadable(String id, String url, long interval) {
            super(id, url, interval);
        }

        

        /**
         * This is where the browser sends us the data. 
         */
        public void doPostBack(StaplerRequest req, StaplerResponse rsp) throws IOException {
            long dataTimestamp = System.currentTimeMillis();
            TextFile df = getDataFile();
            df.write(IOUtils.toString(req.getInputStream(),"UTF-8"));
            df.file.setLastModified(dataTimestamp);
            due = dataTimestamp+getInterval();
            LOGGER.info("Obtained the updated data file for "+id);
            rsp.setContentType("text/plain");  // So browser won't try to parse response
        }
        
    }

}


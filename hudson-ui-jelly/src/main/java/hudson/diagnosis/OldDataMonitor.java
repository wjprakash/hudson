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
package hudson.diagnosis;

import hudson.Extension;
import hudson.model.Saveable;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Winston Prakash
 */
@Extension
public class OldDataMonitor extends OldDataMonitorExt{
    
     /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return new HttpRedirect("manage");
        }
    }

    /**
     * Save all or some of the files to persist data in the new forms.
     * Remove those items from the data map.
     */
    public synchronized HttpResponse doUpgrade(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String thruVerParam = req.getParameter("thruVer");
        VersionNumber thruVer = thruVerParam.equals("all") ? null : new VersionNumber(thruVerParam);
        updating = true;
        for (Iterator<Map.Entry<Saveable,VersionRange>> it = data.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Saveable,VersionRange> entry = it.next();
            VersionNumber version = entry.getValue().max;
            if (version != null && (thruVer == null || !version.isNewerThan(thruVer))) {
                entry.getKey().save();
                it.remove();
            }
        }
        updating = false;
        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Save all files containing only unreadable data (no data upgrades), which discards this data.
     * Remove those items from the data map.
     */
    public synchronized HttpResponse doDiscard(StaplerRequest req, StaplerResponse rsp) throws IOException {
        updating = true;
        for (Iterator<Map.Entry<Saveable,VersionRange>> it = data.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Saveable,VersionRange> entry = it.next();
            if (entry.getValue().max == null) {
                entry.getKey().save();
                it.remove();
            }
        }
        updating = false;
        return HttpResponses.forwardToPreviousPage();
    }

    public HttpResponse doIndex(StaplerResponse rsp) throws IOException {
        return new HttpRedirect("manage");
    }
    
}

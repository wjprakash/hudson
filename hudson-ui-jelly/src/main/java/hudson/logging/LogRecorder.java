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
package hudson.logging;

import hudson.UtilExt;
import hudson.XmlFile;
import hudson.model.HudsonExt;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Winston Prakash
 */
public class LogRecorder extends LogRecorderExt {

    public LogRecorder(String name) {
        super(name);
    }
    
     public static final class Target extends LogRecorderExt.TargetExt{
         
         public Target(String name, Level level) {
            this(name,level.intValue());
        }

        public Target(String name, int level) {
            super(name, level);
        }

        @DataBoundConstructor
        public Target(String name, String level) {
            this(name,Level.parse(level.toUpperCase(Locale.ENGLISH)));
        }
     }
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        JSONObject src = req.getSubmittedForm();

        String newName = src.getString("name"), redirect = ".";
        XmlFile oldFile = null;
        if(!name.equals(newName)) {
            HudsonExt.checkGoodName(newName);
            oldFile = getConfigFile();
            // rename
            getParent().logRecorders.remove(name);
            this.name = newName;
            getParent().logRecorders.put(name,this);
            redirect = "../" + UtilExt.rawEncode(newName) + '/';
        }

        List<Target> newTargets = req.bindJSONToList(Target.class, src.get("targets"));
        for (Target t : newTargets)
            t.enable();
        targets.replaceBy(newTargets);

        save();
        if (oldFile!=null) oldFile.delete();
        rsp.sendRedirect2(redirect);
    }
    
     /**
     * Deletes this recorder, then go back to the parent.
     */
    public synchronized void doDoDelete(StaplerResponse rsp) throws IOException, ServletException {
        requirePOST();
        getConfigFile().delete();
        getParent().logRecorders.remove(name);
        // Disable logging for all our targets,
        // then reenable all other loggers in case any also log the same targets
        for (TargetExt t : targets)
            t.getLogger().setLevel(null);
        for (LogRecorderExt log : getParent().logRecorders.values())
            for (TargetExt t : log.targets)
                t.enable();
        rsp.sendRedirect2("..");
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        LogRecorderManager.doRss(req,rsp,getLogRecords());
    }
}

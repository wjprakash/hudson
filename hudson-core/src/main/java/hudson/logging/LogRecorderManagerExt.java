/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.init.Initializer;
import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import hudson.model.AbstractModelObjectExt;
import hudson.model.HudsonExt;
import hudson.util.CopyOnWriteMap;
import org.apache.commons.io.filefilter.WildcardFileFilter;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;

/**
 * Owner of {@link LogRecorderExt}s, bound to "/log".
 *
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManagerExt extends AbstractModelObjectExt {
    /**
     * {@link LogRecorderExt}s.
     */
    public transient final Map<String,LogRecorderExt> logRecorders = new CopyOnWriteMap.Tree<String,LogRecorderExt>();

    public String getDisplayName() {
        return Messages.LogRecorderManager_DisplayName();
    }

    public String getSearchUrl() {
        return "/log";
    }

    public LogRecorderExt getDynamic(String token) {
        return getLogRecorder(token);
    }

    public LogRecorderExt getLogRecorder(String token) {
        return logRecorders.get(token);
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() throws IOException {
        logRecorders.clear();
        File dir = new File(HudsonExt.getInstance().getRootDir(), "log");
        File[] files = dir.listFiles((FileFilter)new WildcardFileFilter("*.xml"));
        if(files==null)     return;
        for (File child : files) {
            String name = child.getName();
            name = name.substring(0,name.length()-4);   // cut off ".xml"
            LogRecorderExt lr = new LogRecorderExt(name);
            lr.load();
            logRecorders.put(name,lr);
        }
    }

    @Initializer(before=PLUGINS_PREPARED)
    public static void init(HudsonExt h) throws IOException {
        h.getLog().load();
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman
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

import hudson.model.RunMap.Constructor;
import hudson.Extension;

import java.io.File;
import java.io.IOException;

/**
 * Job that runs outside HudsonExt whose result is submitted to HudsonExt
 * (either via web interface, or simply by placing files on the file system,
 * for compatibility.)
 *
 * @author Kohsuke Kawaguchi
 */
public class ExternalJobExt extends ViewJob<ExternalJobExt,ExternalRun> implements TopLevelItem {
    public ExternalJobExt(String name) {
        this(HudsonExt.getInstance(),name);
    }

    public ExternalJobExt(ItemGroup parent, String name) {
        super(parent,name);
    }

    @Override
    protected void reload() {
        this.runs.load(this,new Constructor<ExternalRun>() {
            public ExternalRun create(File dir) throws IOException {
                return new ExternalRun(ExternalJobExt.this,dir);
            }
        });
    }


    // keep track of the previous time we started a build
    private transient long lastBuildStartTime;

    /**
     * Creates a new build of this project for immediate execution.
     *
     * Needs to be synchronized so that two {@link #newBuild()} invocations serialize each other.
     */
    public synchronized ExternalRun newBuild() throws IOException {
        // make sure we don't start two builds in the same second
        // so the build directories will be different too
        long timeSinceLast = System.currentTimeMillis() - lastBuildStartTime;
        if (timeSinceLast < 1000) {
            try {
                Thread.sleep(1000 - timeSinceLast);
            } catch (InterruptedException e) {
            }
        }
        lastBuildStartTime = System.currentTimeMillis();

        ExternalRun run = new ExternalRun(this);
        runs.put(run);
        return run;
    }

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final TopLevelItemDescriptor DESCRIPTOR = new DescriptorImpl();

    @Override
    public String getPronoun() {
        return Messages.ExternalJob_Pronoun();
    }

//    public String getUrl() {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    public String getAbsoluteUrl() {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        public String getDisplayName() {
            return Messages.ExternalJob_DisplayName();
        }

        public ExternalJobExt newInstance(ItemGroup parent, String name) {
            return new ExternalJobExt(parent,name);
        }
    }
}

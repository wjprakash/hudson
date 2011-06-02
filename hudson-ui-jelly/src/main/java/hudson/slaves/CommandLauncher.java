/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.slaves;

import hudson.EnvVars;
import hudson.UtilExt;
import hudson.Extension;
import hudson.model.DescriptorExt;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link ComputerLauncher} through a remote login mechanism like ssh/rsh.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
 */
public class CommandLauncher extends CommandLauncherExt {

    @DataBoundConstructor
    public CommandLauncher(String command) {
        this(command, null);
    }

    public CommandLauncher(String command, EnvVars env) {
        super(command, env);
    }

    @Override
    public void launch(SlaveComputerExt computer, final TaskListener listener) {

        EnvVars envLoc = new EnvVars();
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {
            envLoc.put("HUDSON_URL", rootUrl);
            envLoc.put("SLAVEJAR_URL", rootUrl + "/jnlpJars/slave.jar");
        }

        performLaunch(computer, listener, envLoc);

    }
    private static final Logger LOGGER = Logger.getLogger(CommandLauncher.class.getName());

    @Extension
    public static class DescriptorImpl extends DescriptorExt<ComputerLauncher> {

        @Override
        public String getDisplayName() {
            return Messages.CommandLauncher_displayName();
        }

        public FormValidation doCheckCommand(@QueryParameter String value) {
            if (UtilExt.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Command is empty");
            } else {
                return FormValidation.ok();
            }
        }
    }
}

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
package hudson.model;

import hudson.util.FormValidation;
import hudson.Extension;
import hudson.tools.ToolProperty;

import java.io.File;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Information about JDKExt installation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JDK extends JDKExt {

    public JDK(String name, String javaHome) {
        super(name, javaHome);
    }

    @DataBoundConstructor
    public JDK(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    @Extension
    public static class DescriptorImpl extends JDKExt.DescriptorImpl {

        /**
         * Checks if the JAVA_HOME is a valid JAVA_HOME path.
         */
        public FormValidation doCheckHome(@QueryParameter File value) {
            // this can be used to check the existence of a file on the server, so needs to be protected
            HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

            if (value.getPath().equals("")) {
                return FormValidation.ok();
            }

            if (!value.isDirectory()) {
                return FormValidation.error(Messages.Hudson_NotADirectory(value));
            }

            File toolsJar = new File(value, "lib/tools.jar");
            File mac = new File(value, "lib/dt.jar");
            if (!toolsJar.exists() && !mac.exists()) {
                return FormValidation.error(Messages.Hudson_NotJDKDir(value));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}

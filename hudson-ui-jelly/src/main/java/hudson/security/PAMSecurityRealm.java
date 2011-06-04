/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.security;


import hudson.util.FormValidation;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link SecurityRealm} that uses Unix PAM authentication.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.282
 */
public class PAMSecurityRealm extends PAMSecurityRealmExt {

    @DataBoundConstructor
    public PAMSecurityRealm(String serviceName) {
         super(serviceName);
    }
    
    public static final class DescriptorImpl extends DescriptorImplExt {

        public FormValidation doTest() {
            try {
                String message = NativeUtils.getInstance().checkPamAuthentication();
                if (message.startsWith("Error:")) {
                    return FormValidation.error(message.replaceFirst("Error:", ""));
                } else {
                    return FormValidation.ok(message);
                }
            } catch (NativeAccessException exc) {
                return FormValidation.error("Native Support for PAM Authentication not available.");
            }
        }
    }
}

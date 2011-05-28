/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;


import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link AuthorizationStrategy} that grants full-control to authenticated user
 * (other than anonymous users.)
 *
 * @author Kohsuke Kawaguchi
 */
public class FullControlOnceLoggedInAuthorizationStrategy extends FullControlOnceLoggedInAuthorizationStrategyExt {

    @Extension
    public static final Descriptor<AuthorizationStrategyExt> DESCRIPTOR = new Descriptor<AuthorizationStrategyExt>() {
        @Override
        public String getDisplayName() {
            return Messages.FullControlOnceLoggedInAuthorizationStrategy_DisplayName();
        }

        @Override
        public AuthorizationStrategyExt newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new FullControlOnceLoggedInAuthorizationStrategy();
        }

        @Override
        public String getHelpFile() {
            return "/help/security/full-control-once-logged-in.html";
        }
    };
}

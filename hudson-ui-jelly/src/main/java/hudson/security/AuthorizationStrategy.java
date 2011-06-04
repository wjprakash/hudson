/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Tom Huybrechts
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

import hudson.DescriptorExtensionList;
import hudson.DescriptorExtensionListExt;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.util.DescriptorListExt;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Controls authorization throughout HudsonExt.
 *
 * <h2>Persistence</h2>
 * <p>
 * This object will be persisted along with {@link HudsonExt} object.
 * HudsonExt by itself won't put the ACL returned from {@link #getRootACL()} into the serialized object graph,
 * so if that object contains state and needs to be persisted, it's the responsibility of
 * {@link AuthorizationStrategy} to do so (by keeping them in an instance field.)
 *
 * <h2>Re-configuration</h2>
 * <p>
 * The corresponding {@link Describable} instance will be asked to create a new {@link AuthorizationStrategy}
 * every time the system configuration is updated. Implementations that keep more state in ACL beyond
 * the system configuration should use {@link HudsonExt#getAuthorizationStrategy()} to talk to the current
 * instance to carry over the state. 
 *
 * @author Kohsuke Kawaguchi
 * @see SecurityRealm
 */
public abstract class AuthorizationStrategy extends AuthorizationStrategyExt {

    /**
     * Implementation can choose to provide different ACL for different views.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns the ACL of the ViewGroup.
     *
     * @since 1.220
     */
    public ACL getACL(View item) {
        return item.getOwner().getACL();
    }
    
    public static final class Unsecured extends AuthorizationStrategyExt.Unsecured {

        @Extension
        public static final class DescriptorImpl extends Descriptor<AuthorizationStrategyExt> {

            @Override
            public String getDisplayName() {
                return Messages.AuthorizationStrategy_DisplayName();
            }

            @Override
            public AuthorizationStrategyExt newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return UNSECURED;
            }

            @Override
            public String getHelpFile() {
                return "/help/security/no-authorization.html";
            }
        }
    }
}

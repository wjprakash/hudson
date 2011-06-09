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

import hudson.DescriptorExtensionListExt;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.util.DescriptorListExt;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.ServletRequest;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;

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
public abstract class AuthorizationStrategyExt extends AbstractDescribableImpl<AuthorizationStrategyExt> implements ExtensionPoint {
    /**
     * Returns the instance of {@link ACL} where all the other {@link ACL} instances
     * for all the other model objects eventually delegate.
     * <p>
     * IOW, this ACL will have the ultimate say on the access control.
     */
    public abstract ACL getRootACL();

    /**
     * @deprecated since 1.277
     *      Override {@link #getACL(JobExt)} instead.
     */
    @Deprecated
    public ACL getACL(AbstractProjectExt<?,?> project) {
    	return getACL((JobExt)project);
    }

    public ACL getACL(JobExt<?,?> project) {
    	return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different items.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.220
     */
    public ACL getACL(AbstractItemExt item) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL per user.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.221
     */
    public ACL getACL(UserExt user) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different computers.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation delegates to {@link #getACL(NodeExt)}
     *
     * @since 1.220
     */
    public ACL getACL(ComputerExt computer) {
        return getACL(computer.getNode());
    }

    /**
     * Implementation can choose to provide different ACL for different {@link Cloud}s.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.252
     */
    public ACL getACL(Cloud cloud) {
        return getRootACL();
    }

    public ACL getACL(NodeExt node) {
        return getRootACL();
    }

    /**
     * Returns the list of all group/role names used in this authorization strategy,
     * and the ACL returned from the {@link #getRootACL()} method.
     * <p>
     * This method is used by {@link ContainerAuthentication} to work around the servlet API issue
     * that prevents us from enumerating roles that the user has.
     * <p>
     * If such enumeration is impossible, do the best to list as many as possible, then
     * return it. In the worst case, just return an empty list. Doing so would prevent
     * users from using role names as group names (see HUDSON-2716 for such one such report.)
     *
     * @return
     *      never null.
     */
    public abstract Collection<String> getGroups();

    /**
     * Returns all the registered {@link AuthorizationStrategy} descriptors.
     */
    public static DescriptorExtensionListExt<AuthorizationStrategyExt,DescriptorExt<AuthorizationStrategyExt>> all() {
        return HudsonExt.getInstance().<AuthorizationStrategyExt,DescriptorExt<AuthorizationStrategyExt>>getDescriptorList(AuthorizationStrategyExt.class);
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     *
     * @deprecated since 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    public static final DescriptorListExt<AuthorizationStrategyExt> LIST = new DescriptorListExt<AuthorizationStrategyExt>(AuthorizationStrategyExt.class);
    
    /**
     * {@link AuthorizationStrategy} that implements the semantics
     * of unsecured HudsonExt where everyone has full control.
     *
     * <p>
     * This singleton is safe because {@link Unsecured} is stateless.
     */
    public static final AuthorizationStrategyExt UNSECURED = new Unsecured();

    public static class Unsecured extends AuthorizationStrategyExt implements Serializable {
        /**
         * Maintains the singleton semantics.
         */
        private Object readResolve() {
            return UNSECURED;
        }

        @Override
        public ACL getRootACL() {
            return UNSECURED_ACL;
        }

        public Collection<String> getGroups() {
            return Collections.emptySet();
        }

        private static final ACL UNSECURED_ACL = new ACL() {
            public boolean hasPermission(Authentication a, Permission permission) {
                return true;
            }
        };

        @Extension
        public static final class DescriptorImpl extends DescriptorExt<AuthorizationStrategyExt> {

            @Override
            public String getDisplayName() {
                return Messages.AuthorizationStrategy_DisplayName();
            }

            @Override
            public AuthorizationStrategyExt newInstance(ServletRequest req, JSONObject formData) {
                return UNSECURED;
            }

            @Override
            public String getHelpFile() {
                return "/help/security/no-authorization.html";
            }
        }
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.model.HudsonExt;
import hudson.util.PluginServletFilter;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.userdetails.UserDetailsService;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import java.io.IOException;

/**
 * Pluggable security realm that connects external user database to HudsonExt.
 *
 * <p>
 * If additional views/URLs need to be exposed,
 * an active {@link SecurityRealm} is bound to <tt>CONTEXT_ROOT/securityRealm/</tt>
 * through {@link HudsonExt#getSecurityRealm()}, so you can define additional pages and
 * operations on your {@link SecurityRealm}.
 *
 * <h2>How do I implement this class?</h2>
 * <p>
 * For compatibility reasons, there are two somewhat different ways to implement a custom SecurityRealm.
 *
 * <p>
 * One is to override the {@link #createSecurityComponents()} and create key Acegi components
 * that control the authentication process.
 * The default {@link SecurityRealm#createFilter(FilterConfig)} implementation then assembles them
 * into a chain of {@link Filter}s. All the incoming requests to HudsonExt go through this filter chain,
 * and when the filter chain is done, {@link SecurityContext#getAuthentication()} would tell us
 * who the current user is.
 *
 * <p>
 * If your {@link SecurityRealm} needs to touch the default {@link Filter} chain configuration
 * (e.g., adding new ones), then you can also override {@link #createFilter(FilterConfig)} to do so.
 *
 * <p>
 * This model is expected to fit most {@link SecurityRealm} implementations.
 *
 *
 * <p>
 * The other way of doing this is to ignore {@link #createSecurityComponents()} completely (by returning
 * {@link SecurityComponents} created by the default constructor) and just concentrate on {@link #createFilter(FilterConfig)}.
 * As long as the resulting filter chain properly sets up {@link Authentication} object at the end of the processing,
 * HudsonExt doesn't really need you to fit the standard Acegi models like {@link AuthenticationManager} and
 * {@link UserDetailsService}.
 *
 * <p>
 * This model is for those "weird" implementations.
 *
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>loginLink.jelly</dt>
 * <dd>
 * This view renders the login link on the top right corner of every page, when the user
 * is anonymous. For {@link SecurityRealm}s that support user sign-up, this is a good place
 * to show a "sign up" link. See {@link HudsonPrivateSecurityRealm} implementation
 * for an example of this.
 *
 * <dt>config.jelly</dt>
 * <dd>
 * This view is used to render the configuration page in the system config screen.
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.160
 * @see PluginServletFilter
 */
public abstract class SecurityRealm extends SecurityRealmExt {

    /**
     * Generates a captcha image.
     */
    public final void doCaptcha(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (captchaSupport != null) {
            String id = req.getSession().getId();
            rsp.setContentType("image/png");
            rsp.addHeader("Cache-Control", "no-cache");
            captchaSupport.generateImage(id, rsp.getOutputStream());
        }
    }

     
}

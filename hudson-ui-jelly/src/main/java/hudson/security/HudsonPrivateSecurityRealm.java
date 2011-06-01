/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, David Calavera, Seiji Sogabe, Anton Kozak
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

import hudson.model.Descriptor.FormException;
import hudson.security.captcha.CaptchaSupport;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.Extension;
import hudson.UtilExt;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.*;
import hudson.security.FederatedLoginService.FederatedIdentity;
import hudson.tasks.Mailer;
import hudson.util.Protector;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.kohsuke.stapler.*;

import javax.servlet.*;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 * <p>
 * Implements {@link AccessControlled} to satisfy view rendering, but in reality the access control
 * is done against the {@link HudsonExt} object.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealm extends HudsonPrivateSecurityRealmExt {

    /**
     * @deprecated as of 2.0.1
     */
    @Deprecated
    public HudsonPrivateSecurityRealm(boolean allowsSignup) {
        this(allowsSignup, true, null);
    }

    @DataBoundConstructor
    public HudsonPrivateSecurityRealm(boolean allowsSignup, boolean enableCaptcha, CaptchaSupport captchaSupport) {
        super(allowsSignup, enableCaptcha, captchaSupport);
    }

    /**
     * Show the sign up page with the data from the identity.
     */
    public HttpResponse commenceSignup(final FederatedIdentity identity) {
        // store the identity in the session so that we can use this later
        Stapler.getCurrentRequest().getSession().setAttribute(FEDERATED_IDENTITY_SESSION_KEY, identity);
        return new ForwardToView(this, "signupWithFederatedIdentity.jelly") {

            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                SignupInfo si = new SignupInfo(identity);
                si.errorMessage = Messages.HudsonPrivateSecurityRealm_WouldYouLikeToSignUp(identity.getPronoun(), identity.getIdentifier());
                req.setAttribute("data", si);
                super.generateResponse(req, rsp, node);
            }
        };
    }

    /**
     * Creates an account and associates that with the given identity. Used in conjunction
     * with {@link #commenceSignup(FederatedIdentity)}.
     */
    public UserExt doCreateAccountWithFederatedIdentity(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        UserExt u = _doCreateAccount(req, rsp, "signupWithFederatedIdentity.jelly");
        if (u != null) {
            ((FederatedIdentity) req.getSession().getAttribute(FEDERATED_IDENTITY_SESSION_KEY)).addTo(u);
        }
        return u;
    }
    private static final String FEDERATED_IDENTITY_SESSION_KEY = HudsonPrivateSecurityRealm.class.getName() + ".federatedIdentity";

    /**
     * Creates an user account. Used for self-registration.
     */
    public UserExt doCreateAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        return _doCreateAccount(req, rsp, "signup.jelly");
    }

    private UserExt _doCreateAccount(StaplerRequest req, StaplerResponse rsp, String formView) throws ServletException, IOException {
        if (!allowsSignup()) {
            throw HttpResponses.error(SC_UNAUTHORIZED, new Exception("User sign up is prohibited"));
        }

        boolean firstUser = !hasSomeUser();
        UserExt u = createAccount(req, rsp, enableCaptcha, formView);
        if (u != null) {
            if (firstUser) {
                tryToMakeAdmin(u);  // the first user should be admin, or else there's a risk of lock out
            }
            loginAndTakeBack(req, rsp, u);
        }
        return u;
    }

    /**
     * Lets the current user silently login as the given user and report back accordingly.
     */
    private void loginAndTakeBack(StaplerRequest req, StaplerResponse rsp, UserExt u) throws ServletException, IOException {
        // ... and let him login
        Authentication a = new UsernamePasswordAuthenticationToken(u.getId(), req.getParameter("password1"));
        a = this.getSecurityComponents().manager.authenticate(a);
        SecurityContextHolder.getContext().setAuthentication(a);

        // then back to top
        req.getView(this, "success.jelly").forward(req, rsp);
    }

    /**
     * Creates an user account. Used by admins.
     *
     * This version behaves differently from {@link #doCreateAccount(StaplerRequest, StaplerResponse)} in that
     * this is someone creating another user.
     */
    public void doCreateAccountByAdmin(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(HudsonExt.ADMINISTER);
        if (createAccount(req, rsp, false, "addUser.jelly") != null) {
            rsp.sendRedirect(".");  // send the user back to the listing page
        }
    }

    /**
     * Creates a first admin user account.
     *
     * <p>
     * This can be run by anyone, but only to create the very first user account.
     */
    public void doCreateFirstAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (hasSomeUser()) {
            rsp.sendError(SC_UNAUTHORIZED, "First user was already created");
            return;
        }
        UserExt u = createAccount(req, rsp, false, "firstUser.jelly");
        if (u != null) {
            tryToMakeAdmin(u);
            loginAndTakeBack(req, rsp, u);
        }
    }

    /**
     * Try to make this user a super-user
     */
    private void tryToMakeAdmin(UserExt u) {
        AuthorizationStrategyExt as = HudsonExt.getInstance().getAuthorizationStrategy();
        if (as instanceof GlobalMatrixAuthorizationStrategyExt) {
            GlobalMatrixAuthorizationStrategyExt ma = (GlobalMatrixAuthorizationStrategyExt) as;
            ma.add(HudsonExt.ADMINISTER, u.getId());
        }
    }

    /**
     * @return
     *      null if failed. The browser is already redirected to retry by the time this method returns.
     *      a valid {@link User} object if the user creation was successful.
     */
    private UserExt createAccount(StaplerRequest req, StaplerResponse rsp, boolean selfRegistration, String formView) throws ServletException, IOException {
        // form field validation
        // this pattern needs to be generalized and moved to stapler
        SignupInfo si = new SignupInfo(req);

        String id = Stapler.getCurrentRequest().getSession().getId();

        if (selfRegistration && !validateCaptcha(si.captcha, id)) {
            si.errorMessage = "Text didn't match the word shown in the image";
        }

        if (si.password1 != null && !si.password1.equals(si.password2)) {
            si.errorMessage = "Password didn't match";
        }

        if (!(si.password1 != null && si.password1.length() != 0)) {
            si.errorMessage = "Password is required";
        }

        if (si.username == null || si.username.length() == 0) {
            si.errorMessage = "User name is required";
        } else {
            UserExt user = UserExt.get(si.username);
            if (user.getProperty(Details.class) != null) {
                si.errorMessage = "User name is already taken. Did you forget the password?";
            }
        }

        if (si.fullname == null || si.fullname.length() == 0) {
            si.fullname = si.username;
        }

        if (si.email == null || !si.email.contains("@")) {
            si.errorMessage = "Invalid e-mail address";
        }

        if (si.errorMessage != null) {
            // failed. ask the user to try again.
            req.setAttribute("data", si);
            req.getView(this, formView).forward(req, rsp);
            return null;
        }

        // register the user
        UserExt user = createAccount(si.username, si.password1);
        user.addProperty(new Mailer.UserProperty(si.email));
        user.setFullName(si.fullname);
        user.save();
        return user;
    }
    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {AUTHENTICATED_AUTHORITY};

    public static final class SignupInfo extends HudsonPrivateSecurityRealmExt.SignupInfo {

        public SignupInfo(FederatedIdentity i) {
            super(i);
        }

        public SignupInfo(StaplerRequest req) {
            req.bindParameters(this);
        }
    }

    /**
     * {@link UserProperty} that provides the {@link UserDetails} view of the User object.
     *
     * <p>
     * When a {@link User} object has this property on it, it means the user is configured
     * for log-in.
     *
     * <p>
     * When a {@link User} object is re-configured via the UI, the password
     * is sent to the hidden input field by using {@link Protector}, so that
     * the same password can be retained but without leaking information to the browser.
     */
    public static final class Details extends UserPropertyExt implements InvalidatableUserDetails {

        /**
         * Hashed password.
         */
        private /*almost final*/ String passwordHash;
        /**
         * @deprecated Scrambled password.
         * Field kept here to load old (pre 1.283) user records,
         * but now marked transient so field is no longer saved.
         */
        private transient String password;

        private Details(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        static Details fromHashedPassword(String hashed) {
            return new Details(hashed);
        }

        static Details fromPlainPassword(String rawPassword) {
            return new Details(PASSWORD_ENCODER.encodePassword(rawPassword, null));
        }

        public GrantedAuthority[] getAuthorities() {
            // TODO
            return TEST_AUTHORITY;
        }

        public String getPassword() {
            return passwordHash;
        }

        public String getProtectedPassword() {
            // put session Id in it to prevent a replay attack.
            return Protector.protect(Stapler.getCurrentRequest().getSession().getId() + ':' + getPassword());
        }

        public String getUsername() {
            return user.getId();
        }

        /*package*/ UserExt getUser() {
            return user;
        }

        public boolean isAccountNonExpired() {
            return true;
        }

        public boolean isAccountNonLocked() {
            return true;
        }

        public boolean isCredentialsNonExpired() {
            return true;
        }

        public boolean isEnabled() {
            return true;
        }

        public boolean isInvalid() {
            return user == null;
        }

        public static class ConverterImpl extends XStream2.PassthruConverter<Details> {

            public ConverterImpl(XStream2 xstream) {
                super(xstream);
            }

            @Override
            protected void callback(Details d, UnmarshallingContext context) {
                // Convert to hashed password and report to monitor if we load old data
                if (d.password != null && d.passwordHash == null) {
                    d.passwordHash = PASSWORD_ENCODER.encodePassword(Scrambler.descramble(d.password), null);
                    OldDataMonitorExt.report(context, "1.283");
                }
            }
        }

        @Extension
        public static final class DescriptorImpl extends UserPropertyDescriptor {

            public String getDisplayName() {
                // this feature is only when HudsonPrivateSecurityRealm is enabled
                if (isEnabled()) {
                    return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
                } else {
                    return null;
                }
            }

            public Details newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                String pwd = UtilExt.fixEmpty(req.getParameter("user.password"));
                String pwd2 = UtilExt.fixEmpty(req.getParameter("user.password2"));

                if (!UtilExt.fixNull(pwd).equals(UtilExt.fixNull(pwd2))) {
                    throw new FormException("Please confirm the password by typing it twice", "user.password2");
                }

                String data = Protector.unprotect(pwd);
                if (data != null) {
                    String prefix = Stapler.getCurrentRequest().getSession().getId() + ':';
                    if (data.startsWith(prefix)) {
                        return Details.fromHashedPassword(data.substring(prefix.length()));
                    }
                }
                return Details.fromPlainPassword(UtilExt.fixNull(pwd));
            }

            @Override
            public boolean isEnabled() {
                return HudsonExt.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
            }

            public UserPropertyExt newInstance(UserExt user) {
                return null;
            }
        }
    }
}

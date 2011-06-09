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

import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.model.UserExt;
import hudson.model.UserPropertyExt;
import hudson.tasks.MailerExt.UserProperty;
import org.acegisecurity.userdetails.User;
import hudson.security.captcha.CaptchaSupport;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.Extension;
import hudson.UtilExt;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.UserPropertyDescriptor;
import hudson.security.FederatedLoginService.FederatedIdentity;
import hudson.util.PluginServletFilter;
import hudson.util.Protector;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.encoding.PasswordEncoder;
import org.acegisecurity.providers.encoding.ShaPasswordEncoder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.sf.json.JSONObject;


/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 * <p>
 * Implements {@link AccessControlled} to satisfy view rendering, but in reality the access control
 * is done against the {@link HudsonExt} object.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealmExt extends AbstractPasswordBasedSecurityRealm implements ModelObject, AccessControlled {
    /**
     * If true, sign up is not allowed.
     * <p>
     * This is a negative switch so that the default value 'false' remains compatible with older installations. 
     */
    private final boolean disableSignup;

    /**
     * If true, captcha will be enabled.
     */
    protected final boolean enableCaptcha;
    
    
    /**
     * @deprecated as of 2.0.1
     */
    @Deprecated
    public HudsonPrivateSecurityRealmExt(boolean allowsSignup) {
        this(allowsSignup, true, null);
    }

    public HudsonPrivateSecurityRealmExt(boolean allowsSignup, boolean enableCaptcha, CaptchaSupport captchaSupport) {
        this.disableSignup = !allowsSignup;
        this.enableCaptcha = enableCaptcha;
        setCaptchaSupport(captchaSupport);

        if(!allowsSignup && !hasSomeUser()) {
            // if HudsonExt is newly set up with the security realm and there's no user account created yet,
            // insert a filter that asks the user to create one
            try {
                PluginServletFilter.addFilter(CREATE_FIRST_USER_FILTER);
            } catch (ServletException e) {
                throw new AssertionError(e); // never happen because our Filter.init is no-op
            }
        }
    }

    @Override
    public boolean allowsSignup() {
        return !disableSignup;
    }

    /**
     * Checks if captcha is enabled on signup.
     *
     * @return true if captcha is enabled on signup.
     */
    public boolean isEnableCaptcha() {
        return enableCaptcha;
    }
    
    /**
     * Computes if this HudsonExt has some user accounts configured.
     *
     * <p>
     * This is used to check for the initial
     */
    protected static boolean hasSomeUser() {
        for (UserExt u : UserExt.getAll())
            if(u.getProperty(DetailsExt.class)!=null)
                return true;
        return false;
    }

    /**
     * This implementation doesn't support groups.
     */
    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        throw new UsernameNotFoundException(groupname);
    }

    @Override
    public DetailsExt loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        UserExt u = UserExt.get(username,false);
        DetailsExt p = u!=null ? u.getProperty(DetailsExt.class) : null;
        if(p==null)
            throw new UsernameNotFoundException("Password is not set: "+username);
        if(p.getUser()==null)
            throw new AssertionError();
        return p;
    }

    @Override
    protected DetailsExt authenticate(String username, String password) throws AuthenticationException {
        DetailsExt u = loadUserByUsername(username);
        if (!PASSWORD_ENCODER.isPasswordValid(u.getPassword(),password,null))
            throw new BadCredentialsException("Failed to login as "+username);
        return u;
    }

    
    private static final String FEDERATED_IDENTITY_SESSION_KEY = HudsonPrivateSecurityRealmExt.class.getName()+".federatedIdentity";


    /**
     * Try to make this user a super-user
     */
    private void tryToMakeAdmin(UserExt u) {
        AuthorizationStrategyExt as = HudsonExt.getInstance().getAuthorizationStrategy();
        if (as instanceof GlobalMatrixAuthorizationStrategyExt) {
            GlobalMatrixAuthorizationStrategyExt ma = (GlobalMatrixAuthorizationStrategyExt) as;
            ma.add(HudsonExt.ADMINISTER,u.getId());
        }
    }


    /**
     * Creates a new user account by registering a password to the user.
     */
    public UserExt createAccount(String userName, String password) throws IOException {
        UserExt user = UserExt.get(userName);
        user.addProperty(DetailsExt.fromPlainPassword(password));
        return user;
    }

    /**
     * This is used primarily when the object is listed in the breadcrumb, in the user management screen.
     */
    public String getDisplayName() {
        return "User Database";
    }

    public ACL getACL() {
        return HudsonExt.getInstance().getACL();
    }

    public void checkPermission(Permission permission) {
        HudsonExt.getInstance().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return HudsonExt.getInstance().hasPermission(permission);
    }


    /**
     * All users who can login to the system.
     */
    public List<UserExt> getAllUsers() {
        List<UserExt> r = new ArrayList<UserExt>();
        for (UserExt u : UserExt.getAll()) {
            if(u.getProperty(DetailsExt.class)!=null)
                r.add(u);
        }
        Collections.sort(r);
        return r;
    }

    /**
     * This is to map users under the security realm URL.
     * This in turn helps us set up the right navigation breadcrumb.
     */
    public UserExt getUser(String id) {
        return UserExt.get(id);
    }

    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {AUTHENTICATED_AUTHORITY};

    public static class SignupInfo {
        public String username,password1,password2,fullname,email,captcha;

        /**
         * To display an error message, set it here.
         */
        public String errorMessage;

        public SignupInfo() {
        }

        public SignupInfo(FederatedIdentity i) {
            this.username = i.getNickname();
            this.fullname = i.getFullName();
            this.email = i.getEmailAddress();
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
    public static class DetailsExt extends UserPropertyExt implements InvalidatableUserDetails {
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

        protected DetailsExt(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        static DetailsExt fromHashedPassword(String hashed) {
            return new DetailsExt(hashed);
        }

        static DetailsExt fromPlainPassword(String rawPassword) {
            return new DetailsExt(PASSWORD_ENCODER.encodePassword(rawPassword,null));
        }

        public GrantedAuthority[] getAuthorities() {
            // TODO
            return TEST_AUTHORITY;
        }

        public String getPassword() {
            return passwordHash;
        }
        
        public String getProtectedPassword() {
            return protectPassword("");
            // put session Id in it to prevent a replay attack.
            //return Protector.protect(Stapler.getCurrentRequest().getSession().getId() + ':' + getPassword());
        }

        protected String protectPassword(String sessionId) {
            // put session Id in it to prevent a replay attack.
            return Protector.protect( sessionId + ':' + getPassword());
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
            return user==null;
        }
        
        @Extension
        public static class DescriptorImplExt extends UserPropertyDescriptor {

            public String getDisplayName() {
                // this feature is only when HudsonPrivateSecurityRealm is enabled
                if (isEnabled()) {
                    return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
                } else {
                    return null;
                }
            }

             

            public UserPropertyExt newInstance(UserExt user) {
                return null;
            }
        }

        public static class ConverterImpl extends XStream2.PassthruConverter<DetailsExt> {
            public ConverterImpl(XStream2 xstream) { super(xstream); }
            @Override protected void callback(DetailsExt d, UnmarshallingContext context) {
                // Convert to hashed password and report to monitor if we load old data
                if (d.password!=null && d.passwordHash==null) {
                    d.passwordHash = PASSWORD_ENCODER.encodePassword(Scrambler.descramble(d.password),null);
                    OldDataMonitorExt.report(context, "1.283");
                }
            }
        }
    }

    /**
     * Displays "manage users" link in the system config if {@link HudsonPrivateSecurityRealm}
     * is in effect.
     */
    @Extension
    public static final class ManageUserLinks extends ManagementLink {
        public String getIconFileName() {
            if(HudsonExt.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealmExt)
                return "user.gif";
            else
                return null;    // not applicable now
        }

        public String getUrlName() {
            return "securityRealm/";
        }

        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_Description();
        }
    }

    /**
     * {@link PasswordEncoder} based on SHA-256 and random salt generation.
     *
     * <p>
     * The salt is prepended to the hashed password and returned. So the encoded password is of the form
     * <tt>SALT ':' hash(PASSWORD,SALT)</tt>.
     *
     * <p>
     * This abbreviates the need to store the salt separately, which in turn allows us to hide the salt handling
     * in this little class. The rest of the Acegi thinks that we are not using salt.
     */
    public static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        private final PasswordEncoder passwordEncoder = new ShaPasswordEncoder(256);

        public String encodePassword(String rawPass, Object _) throws DataAccessException {
            return hash(rawPass);
        }

        public boolean isPasswordValid(String encPass, String rawPass, Object _) throws DataAccessException {
            // pull out the sale from the encoded password
            int i = encPass.indexOf(':');
            if(i<0) return false;
            String salt = encPass.substring(0,i);
            return encPass.substring(i+1).equals(passwordEncoder.encodePassword(rawPass,salt));
        }

        /**
         * Creates a hashed password by generating a random salt.
         */
        private String hash(String password) {
            String salt = generateSalt();
            return salt+':'+passwordEncoder.encodePassword(password,salt);
        }

        /**
         * Generates random salt.
         */
        private String generateSalt() {
            StringBuilder buf = new StringBuilder();
            SecureRandom sr = new SecureRandom();
            for( int i=0; i<6; i++ ) {// log2(52^6)=34.20... so, this is about 32bit strong.
                boolean upper = sr.nextBoolean();
                char ch = (char)(sr.nextInt(26) + 'a');
                if(upper)   ch=Character.toUpperCase(ch);
                buf.append(ch);
            }
            return buf.toString();
        }
    };

    @Extension
    public static final class DescriptorImpl extends DescriptorExt<SecurityRealmExt> {
        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/security/private-realm.html"; 
        }
    }

    private static final Filter CREATE_FIRST_USER_FILTER = new Filter() {
        public void init(FilterConfig config) throws ServletException {
        }

        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;

            if(req.getRequestURI().equals(req.getContextPath()+"/")) {
                if (needsToCreateFirstUser()) {
                    ((HttpServletResponse)response).sendRedirect("securityRealm/firstUser");
                } else {// the first user already created. the role of this filter is over.
                    PluginServletFilter.removeFilter(this);
                    chain.doFilter(request,response);
                }
            } else
                chain.doFilter(request,response);
        }

        private boolean needsToCreateFirstUser() {
            return !hasSomeUser()
                && HudsonExt.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealmExt;
        }

        public void destroy() {
        }
    };
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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
import hudson.model.HudsonExt;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
 *
 *
 * <h2>Key Object Classes</h2>
 *
 * <h4>Group Membership</h4>
 *
 * <p>
 * Two object classes seem to be relevant. These are in RFC 2256 and core.schema. These use DN for membership,
 * so it can create a group of anything. I don't know what the difference between these two are.
 * <pre>
attributetype ( 2.5.4.31 NAME 'member'
DESC 'RFC2256: member of a group'
SUP distinguishedName )

attributetype ( 2.5.4.50 NAME 'uniqueMember'
DESC 'RFC2256: unique member of a group'
EQUALITY uniqueMemberMatch
SYNTAX 1.3.6.1.4.1.1466.115.121.1.34 )

objectclass ( 2.5.6.9 NAME 'groupOfNames'
DESC 'RFC2256: a group of names (DNs)'
SUP top STRUCTURAL
MUST ( member $ cn )
MAY ( businessCategory $ seeAlso $ owner $ ou $ o $ description ) )

objectclass ( 2.5.6.17 NAME 'groupOfUniqueNames'
DESC 'RFC2256: a group of unique names (DN and Unique Identifier)'
SUP top STRUCTURAL
MUST ( uniqueMember $ cn )
MAY ( businessCategory $ seeAlso $ owner $ ou $ o $ description ) )
 * </pre>
 *
 * <p>
 * This one is from nis.schema, and appears to model POSIX group/user thing more closely.
 * <pre>
objectclass ( 1.3.6.1.1.1.2.2 NAME 'posixGroup'
DESC 'Abstraction of a group of accounts'
SUP top STRUCTURAL
MUST ( cn $ gidNumber )
MAY ( userPassword $ memberUid $ description ) )

attributetype ( 1.3.6.1.1.1.1.12 NAME 'memberUid'
EQUALITY caseExactIA5Match
SUBSTR caseExactIA5SubstringsMatch
SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 )

objectclass ( 1.3.6.1.1.1.2.0 NAME 'posixAccount'
DESC 'Abstraction of an account with POSIX attributes'
SUP top AUXILIARY
MUST ( cn $ uid $ uidNumber $ gidNumber $ homeDirectory )
MAY ( userPassword $ loginShell $ gecos $ description ) )

attributetype ( 1.3.6.1.1.1.1.0 NAME 'uidNumber'
DESC 'An integer uniquely identifying a user in an administrative domain'
EQUALITY integerMatch
SYNTAX 1.3.6.1.4.1.1466.115.121.1.27 SINGLE-VALUE )

attributetype ( 1.3.6.1.1.1.1.1 NAME 'gidNumber'
DESC 'An integer uniquely identifying a group in an administrative domain'
EQUALITY integerMatch
SYNTAX 1.3.6.1.4.1.1466.115.121.1.27 SINGLE-VALUE )
 * </pre>
 *
 * <p>
 * Active Directory specific schemas (from <a href="http://www.grotan.com/ldap/microsoft.schema">here</a>).
 * <pre>
objectclass ( 1.2.840.113556.1.5.8
NAME 'group'
SUP top
STRUCTURAL
MUST (groupType )
MAY (member $ nTGroupMembers $ operatorCount $ adminCount $
groupAttributes $ groupMembershipSAM $ controlAccessRights $
desktopProfile $ nonSecurityMember $ managedBy $
primaryGroupToken $ mail ) )

objectclass ( 1.2.840.113556.1.5.9
NAME 'user'
SUP organizationalPerson
STRUCTURAL
MAY (userCertificate $ networkAddress $ userAccountControl $
badPwdCount $ codePage $ homeDirectory $ homeDrive $
badPasswordTime $ lastLogoff $ lastLogon $ dBCSPwd $
localeID $ scriptPath $ logonHours $ logonWorkstation $
maxStorage $ userWorkstations $ unicodePwd $
otherLoginWorkstations $ ntPwdHistory $ pwdLastSet $
preferredOU $ primaryGroupID $ userParameters $
profilePath $ operatorCount $ adminCount $ accountExpires $
lmPwdHistory $ groupMembershipSAM $ logonCount $
controlAccessRights $ defaultClassStore $ groupsToIgnore $
groupPriority $ desktopProfile $ dynamicLDAPServer $
userPrincipalName $ lockoutTime $ userSharedFolder $
userSharedFolderOther $ servicePrincipalName $
aCSPolicyName $ terminalServer $ mSMQSignCertificates $
mSMQDigests $ mSMQDigestsMig $ mSMQSignCertificatesMig $
msNPAllowDialin $ msNPCallingStationID $
msNPSavedCallingStationID $ msRADIUSCallbackNumber $
msRADIUSFramedIPAddress $ msRADIUSFramedRoute $
msRADIUSServiceType $ msRASSavedCallbackNumber $
msRASSavedFramedIPAddress $ msRASSavedFramedRoute $
mS-DS-CreatorSID ) )
 * </pre>
 *
 *
 * <h2>References</h2>
 * <dl>
 * <dt><a href="http://www.openldap.org/doc/admin22/schema.html">Standard Schemas</a>
 * <dd>
 * The downloadable distribution contains schemas that define the structure of LDAP entries.
 * Because this is a standard, we expect most LDAP servers out there to use it, although
 * there are different objectClasses that can be used for similar purposes, and apparently
 * many deployments choose to use different objectClasses.
 *
 * <dt><a href="http://www.ietf.org/rfc/rfc2256.txt">RFC 2256</a>
 * <dd>
 * Defines the meaning of several key datatypes used in the schemas with some explanations. 
 *
 * <dt><a href="http://msdn.microsoft.com/en-us/library/ms675085(VS.85).aspx">Active Directory schema</a>
 * <dd>
 * More navigable schema list, including core and MS extensions specific to Active Directory.
 * </dl>
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.166
 */
public class LDAPSecurityRealm extends LDAPSecurityRealmExt {

    @DataBoundConstructor
    public LDAPSecurityRealm(String server, String rootDN, String userSearchBase, String userSearch, String groupSearchBase, String managerDN, String managerPassword) {
        super(server, rootDN, userSearchBase, userSearch, groupSearchBase, managerDN, managerPassword);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealmExt> {

        public String getDisplayName() {
            return Messages.LDAPSecurityRealm_DisplayName();
        }

        public FormValidation doServerCheck(
                @QueryParameter final String server,
                @QueryParameter final String managerDN,
                @QueryParameter final String managerPassword) {

            if (!HudsonExt.getInstance().hasPermission(HudsonExt.ADMINISTER)) {
                return FormValidation.ok();
            }

            try {
                Hashtable<String, String> props = new Hashtable<String, String>();
                if (managerDN != null && managerDN.trim().length() > 0 && !"undefined".equals(managerDN)) {
                    props.put(Context.SECURITY_PRINCIPAL, managerDN);
                }
                if (managerPassword != null && managerPassword.trim().length() > 0 && !"undefined".equals(managerPassword)) {
                    props.put(Context.SECURITY_CREDENTIALS, managerPassword);
                }
                props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                props.put(Context.PROVIDER_URL, addPrefix(server) + '/');

                DirContext ctx = new InitialDirContext(props);
                ctx.getAttributes("");
                return FormValidation.ok();   // connected
            } catch (NamingException e) {
                // trouble-shoot
                Matcher m = Pattern.compile("(ldaps://)?([^:]+)(?:\\:(\\d+))?").matcher(server.trim());
                if (!m.matches()) {
                    return FormValidation.error(Messages.LDAPSecurityRealm_SyntaxOfServerField());
                }

                try {
                    InetAddress adrs = InetAddress.getByName(m.group(2));
                    int port = m.group(1) != null ? 636 : 389;
                    if (m.group(3) != null) {
                        port = Integer.parseInt(m.group(3));
                    }
                    Socket s = new Socket(adrs, port);
                    s.close();
                } catch (UnknownHostException x) {
                    return FormValidation.error(Messages.LDAPSecurityRealm_UnknownHost(x.getMessage()));
                } catch (IOException x) {
                    return FormValidation.error(x, Messages.LDAPSecurityRealm_UnableToConnect(server, x.getMessage()));
                }

                // otherwise we don't know what caused it, so fall back to the general error report
                // getMessage() alone doesn't offer enough
                return FormValidation.error(e, Messages.LDAPSecurityRealm_UnableToConnect(server, e));
            } catch (NumberFormatException x) {
                // The getLdapCtxInstance method throws this if it fails to parse the port number
                return FormValidation.error(Messages.LDAPSecurityRealm_InvalidPortNumber());
            }
        }
    }
}

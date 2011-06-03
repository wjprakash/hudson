/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Erik Ramfelt, Jean-Baptiste Quenot, Luca Domenico Milanesio
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
package hudson.tasks;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.AbstractBuildExt;
import hudson.model.BuildListener;
import hudson.util.XStream2;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.Extension;
import hudson.RestrictedSince;
import hudson.UtilExt;
import hudson.model.AbstractProjectExt;
import hudson.model.HudsonExt;
import hudson.model.UserExt;
import hudson.model.UserPropertyDescriptor;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Session;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class MailerExt extends Notifier {

    protected static final Logger LOGGER = Logger.getLogger(MailerExt.class.getName());
    /**
     * Whitespace-separated list of e-mail addresses that represent recipients.
     */
    public String recipients;
    /**
     * If true, only the first unstable build will be reported.
     */
    public boolean dontNotifyEveryUnstableBuild;
    /**
     * If true, individuals will receive e-mails regarding who broke the build.
     */
    public boolean sendToIndividuals;
    // TODO: left so that XStream won't get angry. figure out how to set the error handling behavior
    // in XStream.  Deprecated since 2005-04-23.
    private transient String from;
    private transient String subject;
    private transient boolean failureOnly;

    @Override
    public boolean perform(AbstractBuildExt<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (debug) {
            listener.getLogger().println("Running mailer");
        }
        // substitute build parameters
        EnvVars env = build.getEnvironment(listener);
        String recip = env.expand(recipients);

        return new MailSenderImpl(recip, dontNotifyEveryUnstableBuild, sendToIndividuals, descriptor().charset).execute(build, listener);
    }

    /**
     * This class does explicit check pointing.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    /**
     * @deprecated as of 1.286
     *      Use {@link #descriptor()} to obtain the current instance.
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("1.355")
    public static DescriptorImplExt DESCRIPTOR;

    public static DescriptorImplExt descriptor() {
        return HudsonExt.getInstance().getDescriptorByType(MailerExt.DescriptorImplExt.class);
    }

    @Extension
    public static class DescriptorImplExt extends BuildStepDescriptor<Publisher> {

        /**
         * The default e-mail address suffix appended to the user name found from changelog,
         * to send e-mails. Null if not configured.
         */
        protected String defaultSuffix;
        /**
         * HudsonExt's own URL, to put into the e-mail.
         */
        protected String hudsonUrl;
        /**
         * If non-null, use SMTP-AUTH with these information.
         */
        protected String smtpAuthUsername;
        protected Secret smtpAuthPassword;
        /**
         * The e-mail address that HudsonExt puts to "From:" field in outgoing e-mails.
         * Null if not configured.
         */
        protected String adminAddress;
        /**
         * The SMTP server to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>localhost</tt>.
         */
        protected String smtpHost;
        /**
         * If true use SSL on port 465 (standard SMTPS) unless <code>smtpPort</code> is set.
         */
        protected boolean useSsl;
        /**
         * The SMTP port to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>25</tt>.
         */
        protected String smtpPort;
        /**
         * The charset to use for the text and subject.
         */
        protected String charset;
        /**
         * Used to keep track of number test e-mails.
         */
        protected static transient int testEmailCount = 0;

        public DescriptorImplExt() {
            load();
            DESCRIPTOR = this;
        }

        public String getDisplayName() {
            return Messages.Mailer_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        public String getDefaultSuffix() {
            return defaultSuffix;
        }

        /** JavaMail session. */
        public Session createSession() {
            return createSession(smtpHost, smtpPort, useSsl, smtpAuthUsername, smtpAuthPassword);
        }

        protected static Session createSession(String smtpHost, String smtpPort, boolean useSsl, String smtpAuthUserName, Secret smtpAuthPassword) {
            smtpPort = UtilExt.fixEmptyAndTrim(smtpPort);
            smtpAuthUserName = UtilExt.fixEmptyAndTrim(smtpAuthUserName);

            Properties props = new Properties(System.getProperties());
            if (UtilExt.fixEmptyAndTrim(smtpHost) != null) {
                props.put("mail.smtp.host", smtpHost);
            }
            if (smtpPort != null) {
                props.put("mail.smtp.port", smtpPort);
            }
            if (useSsl) {
                /* This allows the user to override settings by setting system properties but
                 * also allows us to use the default SMTPs port of 465 if no port is already set.
                 * It would be cleaner to use smtps, but that's done by calling session.getTransport()...
                 * and thats done in mail sender, and it would be a bit of a hack to get it all to
                 * coordinate, and we can make it work through setting mail.smtp properties.
                 */
                if (props.getProperty("mail.smtp.socketFactory.port") == null) {
                    String port = smtpPort == null ? "465" : smtpPort;
                    props.put("mail.smtp.port", port);
                    props.put("mail.smtp.socketFactory.port", port);
                }
                if (props.getProperty("mail.smtp.socketFactory.class") == null) {
                    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                }
                props.put("mail.smtp.socketFactory.fallback", "false");
            }
            if (smtpAuthUserName != null) {
                props.put("mail.smtp.auth", "true");
            }

            // avoid hang by setting some timeout. 
            props.put("mail.smtp.timeout", "60000");
            props.put("mail.smtp.connectiontimeout", "60000");

            return Session.getInstance(props, getAuthenticator(smtpAuthUserName, Secret.toString(smtpAuthPassword)));
        }

        private static Authenticator getAuthenticator(final String smtpAuthUserName, final String smtpAuthPassword) {
            if (smtpAuthUserName == null) {
                return null;
            }
            return new Authenticator() {

                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpAuthUserName, smtpAuthPassword);
                }
            };
        }

        protected String nullify(String v) {
            if (v != null && v.length() == 0) {
                v = null;
            }
            return v;
        }

        public String getSmtpServer() {
            return smtpHost;
        }

        public String getAdminAddress() {
            String v = adminAddress;
            if (v == null) {
                v = Messages.Mailer_Address_Not_Configured();
            }
            return v;
        }

        public String getUrl() {
            return hudsonUrl;
        }

        public String getSmtpAuthUserName() {
            return smtpAuthUsername;
        }

        public String getSmtpAuthPassword() {
            if (smtpAuthPassword == null) {
                return null;
            }
            return Secret.toString(smtpAuthPassword);
        }

        public boolean getUseSsl() {
            return useSsl;
        }

        public String getSmtpPort() {
            return smtpPort;
        }

        public String getCharset() {
            String c = charset;
            if (c == null || c.length() == 0) {
                c = "UTF-8";
            }
            return c;
        }

        public void setDefaultSuffix(String defaultSuffix) {
            this.defaultSuffix = defaultSuffix;
        }

        public void setHudsonUrl(String hudsonUrl) {
            this.hudsonUrl = hudsonUrl;
        }

        public void setAdminAddress(String adminAddress) {
            if (adminAddress.startsWith("\"") && adminAddress.endsWith("\"")) {
                // some users apparently quote the whole thing. Don't konw why
                // anyone does this, but it's a machine's job to forgive human mistake
                adminAddress = adminAddress.substring(1, adminAddress.length() - 1);
            }
            this.adminAddress = adminAddress;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public void setUseSsl(boolean useSsl) {
            this.useSsl = useSsl;
        }

        public void setSmtpPort(String smtpPort) {
            this.smtpPort = smtpPort;
        }

        public void setCharset(String chaset) {
            this.charset = chaset;
        }

        public void setSmtpAuth(String userName, String password) {
            this.smtpAuthUsername = userName;
            this.smtpAuthPassword = Secret.fromString(password);
        }

        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }
    }

    /**
     * Per user property that is e-mail address.
     */
    public static class UserProperty extends hudson.model.UserPropertyExt {

        /**
         * The user's e-mail address.
         * Null to leave it to default.
         */
        private final String emailAddress;

        public UserProperty(String emailAddress) {
            this.emailAddress = emailAddress;
        }

        public String getAddress() {
            if (emailAddress != null) {
                return emailAddress;
            }

            // try the inference logic
            return MailAddressResolver.resolve(user);
        }
        
        @Extension
        public static class DescriptorImplExt extends UserPropertyDescriptor {

            public String getDisplayName() {
                return Messages.Mailer_UserProperty_DisplayName();
            }

            public UserProperty newInstance(UserExt user) {
                return new UserProperty(null);
            }

        }
    }
    /**
     * Debug probe point to be activated by the scripting console.
     */
    public static boolean debug = false;

    public static class ConverterImpl extends XStream2.PassthruConverter<MailerExt> {

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(MailerExt m, UnmarshallingContext context) {
            if (m.from != null || m.subject != null || m.failureOnly || m.descriptor().charset != null) {
                OldDataMonitorExt.report(context, "1.10");
            }
        }
    }

    private static class MailSenderImpl extends MailSender {

        public MailSenderImpl(String recipients, boolean dontNotifyEveryUnstableBuild, boolean sendToIndividuals, String charset) {
            super(recipients, dontNotifyEveryUnstableBuild, sendToIndividuals, charset);
        }

        /** Check whether a path (/-separated) will be archived. */
        @Override
        public boolean artifactMatches(String path, AbstractBuildExt<?, ?> build) {
            ArtifactArchiverExt aa = build.getProject().getPublishersList().get(ArtifactArchiverExt.class);
            if (aa == null) {
                LOGGER.finer("No ArtifactArchiver found");
                return false;
            }
            String artifacts = aa.getArtifacts();
            for (String include : artifacts.split("[, ]+")) {
                String pattern = include.replace(File.separatorChar, '/');
                if (pattern.endsWith("/")) {
                    pattern += "**";
                }
                if (SelectorUtils.matchPath(pattern, path)) {
                    LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches true for {0} against {1}", new Object[]{path, pattern});
                    return true;
                }
            }
            LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches for {0} matched none of {1}", new Object[]{path, artifacts});
            return false;
        }
    }
}

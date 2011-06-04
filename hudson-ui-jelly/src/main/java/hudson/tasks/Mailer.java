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

import hudson.Extension;
import hudson.Functions;
import hudson.FunctionsExt;
import hudson.Util;
import hudson.UtilExt;

import hudson.model.Descriptor.FormException;
import hudson.util.FormValidation;
import hudson.util.SecretExt;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Date;
import java.net.InetAddress;
import java.net.UnknownHostException;

import net.sf.json.JSONObject;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends MailerExt {

    @Extension
    public static final class DescriptorImpl extends DescriptorImplExt {

        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // this code is brain dead
            smtpHost = nullify(json.getString("smtpServer"));
            setAdminAddress(json.getString("adminAddress"));

            defaultSuffix = nullify(json.getString("defaultSuffix"));
            String url = nullify(json.getString("url"));
            if (url != null && !url.endsWith("/")) {
                url += '/';
            }
            hudsonUrl = url;

            if (json.has("useSMTPAuth")) {
                JSONObject auth = json.getJSONObject("useSMTPAuth");
                smtpAuthUsername = nullify(auth.getString("smtpAuthUserName"));
                smtpAuthPassword = SecretExt.fromString(nullify(auth.getString("smtpAuthPassword")));
            } else {
                smtpAuthUsername = null;
                smtpAuthPassword = null;
            }
            smtpPort = nullify(json.getString("smtpPort"));
            useSsl = json.getBoolean("useSsl");
            charset = json.getString("charset");
            if (charset == null || charset.length() == 0) {
                charset = "UTF-8";
            }

            save();
            return true;
        }

        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
            Mailer m = new Mailer();
            req.bindParameters(m, "mailer_");
            m.dontNotifyEveryUnstableBuild = req.getParameter("mailer_notifyEveryUnstableBuild") == null;

            if (hudsonUrl == null) {
                // if HudsonExt URL is not configured yet, infer some default
                hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }

            return m;
        }

        /**
         * Checks the URL in <tt>global.jelly</tt>
         */
        public FormValidation doCheckUrl(@QueryParameter String value) {
            if (value.startsWith("http://localhost")) {
                return FormValidation.warning(Messages.Mailer_Localhost_Error());
            }
            return FormValidation.ok();
        }

        public FormValidation doAddressCheck(@QueryParameter String value) {
            try {
                new InternetAddress(value);
                return FormValidation.ok();
            } catch (AddressException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckSmtpServer(@QueryParameter String value) {
            try {
                if (Util.fixEmptyAndTrim(value) != null) {
                    InetAddress.getByName(value);
                }
                return FormValidation.ok();
            } catch (UnknownHostException e) {
                return FormValidation.error(Messages.Mailer_Unknown_Host_Name() + value);
            }
        }

        public FormValidation doCheckAdminAddress(@QueryParameter String value) {
            return doAddressCheck(value);
        }

        public FormValidation doCheckDefaultSuffix(@QueryParameter String value) {
            if (value.matches("@[A-Za-z0-9.\\-]+") || Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.Mailer_Suffix_Error());
            }
        }

        /**
         * Send an email to the admin address
         * @throws IOException
         * @throws ServletException
         * @throws InterruptedException
         */
        public FormValidation doSendTestMail(
                @QueryParameter String smtpServer, @QueryParameter String adminAddress, @QueryParameter boolean useSMTPAuth,
                @QueryParameter String smtpAuthUserName, @QueryParameter String smtpAuthPassword,
                @QueryParameter boolean useSsl, @QueryParameter String smtpPort) throws IOException, ServletException, InterruptedException {
            try {
                if (!useSMTPAuth) {
                    smtpAuthUserName = smtpAuthPassword = null;
                }

                MimeMessage msg = new MimeMessage(createSession(smtpServer, smtpPort, useSsl, smtpAuthUserName, SecretExt.fromString(smtpAuthPassword)));
                msg.setSubject("Test email #" + ++testEmailCount);
                msg.setContent("This is test email #" + testEmailCount + " sent from Hudson Continuous Integration server.", "text/plain");
                msg.setFrom(new InternetAddress(adminAddress));
                msg.setSentDate(new Date());
                msg.setRecipient(Message.RecipientType.TO, new InternetAddress(adminAddress));

                Transport.send(msg);

                return FormValidation.ok("Email was successfully sent");
            } catch (MessagingException e) {
                return FormValidation.errorWithMarkup("<p>Failed to send out e-mail</p><pre>" + UtilExt.escape(FunctionsExt.printThrowable(e)) + "</pre>");
            }
        }
    }

    /**
     * Per user property that is e-mail address.
     */
    public static class UserProperty extends MailerExt.UserProperty {

        public UserProperty(String emailAddress) {
            super(emailAddress);
        }

        @Exported
        @Override
        public String getAddress() {
            return super.getAddress();
        }

        @Extension
        public static final class DescriptorImpl extends DescriptorImplExt {

            public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new UserProperty(req.getParameter("email.address"));
            }
        }
    }
}

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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


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
    private transient String charset;

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    @Override
    public boolean perform(AbstractBuildExt<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (debug) {
            listener.getLogger().println("Running mailer");
        }
        // substitute build parameters
        EnvVars env = build.getEnvironment(listener);
        String recip = env.expand(recipients);

        return new MailSenderImpl(recip, dontNotifyEveryUnstableBuild, sendToIndividuals, charset).execute(build, listener);
    }
    

    /**
     * This class does explicit check pointing.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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
            if (m.from != null || m.subject != null || m.failureOnly || m.charset != null) {
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

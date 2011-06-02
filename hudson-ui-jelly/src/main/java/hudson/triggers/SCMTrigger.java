/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot, id:cactusman
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
package hudson.triggers;

import hudson.console.AnnotatedLargeText;
import hudson.model.Descriptor.FormException;
import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractBuildExt;
import hudson.model.Action;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.ProjectExt;
import hudson.model.SCMedItem;
import hudson.model.AdministrativeMonitorExt;
import hudson.util.FlushProofOutputStream;
import hudson.util.FormValidation;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;


/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTrigger extends SCMTriggerExt {

    private static final Logger LOGGER = Logger.getLogger(SCMTrigger.class.getName());

    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec) throws ANTLRException {
        super(scmpoll_spec);
    }

    /**
     * Run the SCM trigger with additional build actions. Used by SubversionRepositoryStatus
     * to trigger a build at a specific revisionn number.
     * 
     * @param additionalActions
     * @since 1.375
     */
    @Override
    public void run(Action[] additionalActions) {
        if (HudsonExt.getInstance().isQuietingDown()) {
            return; // noop
        }
        DescriptorImpl d = getDescriptor();
        
        this.setSynchronousPolling(d.synchronousPolling); 
        this.setPollingThreadCount(d.getPollingThreadCount());

        super.run(additionalActions); 
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        
        /**
         * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
         * that each project polls for changes independently.
         */
        public boolean synchronousPolling = false;
        /**
         * Max number of threads for SCM polling.
         * 0 for unbounded.
         */
        private int maximumThreads;

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(ItemExt item) {
            return item instanceof SCMedItem;
        }

         
        @Override
        public String getDisplayName() {
            return Messages.SCMTrigger_DisplayName();
        }

        /**
         * Gets the number of concurrent threads used for polling.
         *
         * @return
         *      0 if unlimited.
         */
        public int getPollingThreadCount() {
            return maximumThreads;
        }

        /**
         * Sets the number of concurrent threads used for SCM polling and resizes the thread pool accordingly
         * @param n number of concurrent threads, zero or less means unlimited, maximum is 100
         */
        public void setPollingThreadCount(int n) {
            maximumThreads = n;
        }

         
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            String t = json.optString("pollingThreadCount", null);
            if (t == null || t.length() == 0) {
                setPollingThreadCount(0);
            } else {
                setPollingThreadCount(Integer.parseInt(t));
            }

            // Save configuration
            save();

            return true;
        }

        public FormValidation doCheckPollingThreadCount(@QueryParameter String value) {
            if (value != null && "".equals(value.trim())) {
                return FormValidation.ok();
            }
            return FormValidation.validateNonNegativeInteger(value);
        }
    }

    /**
     * Associated with {@link AbstractBuildExt} to show the polling log
     * that triggered that build.
     *
     * @since 1.376
     */
    public static class BuildAction extends BuildActionExt {

        public BuildAction(AbstractBuildExt build) {
            super(build);
        }

        /**
         * Sends out the raw polling log output.
         */
        public void doPollingLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            // Prevent jelly from flushing stream so Content-Length header can be added afterwards
            FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
            getPollingLogText().writeLogTo(0, out);
            out.close();
        }

        public AnnotatedLargeText getPollingLogText() {
            return new AnnotatedLargeText<BuildAction>(getPollingLogFile(), Charset.defaultCharset(), true, this);
        }

        /**
         * Used from <tt>polling.jelly</tt> to write annotated polling log to the given output.
         */
        public void writePollingLogTo(long offset, XMLOutput out) throws IOException {
            // TODO: resurrect compressed log file support
            getPollingLogText().writeHtmlTo(offset, out.asWriter());
        }
    }

    /**
     * Action object for {@link ProjectExt}. Used to display the last polling log.
     */
    public final class SCMAction extends SCMActionExt {

        /**
         * Writes the annotated log to the given output.
         * @since 1.350
         */
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<SCMAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
    }
}

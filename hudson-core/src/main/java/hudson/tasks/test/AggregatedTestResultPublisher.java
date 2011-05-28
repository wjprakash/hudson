/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Michael B. Donohue, Yahoo!, Inc.
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
package hudson.tasks.test;

import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import static hudson.Util.fixNull;
import hudson.model.BuildListener;
import hudson.model.FingerprintExt.RangeSet;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.ResultExt;
import hudson.model.RunExt;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates downstream test reports into a single consolidated report,
 * so that people can see the overall test results in one page
 * when tests are scattered across many different jobs.
 *
 * @author Kohsuke Kawaguchi
 */
public class AggregatedTestResultPublisher extends Recorder {
    /**
     * Jobs to aggregate. Comma separated.
     * Null if triggering downstreams.
     */
    public final String jobs;

    public AggregatedTestResultPublisher(String jobs) {
        this.jobs = Util.fixEmptyAndTrim(jobs);
    }

    public boolean perform(AbstractBuildExt<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // add a TestResult just so that it can show up later.
        build.addAction(new TestResultAction(jobs,build));
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Action that serves the aggregated record.
     *
     * TODO: persist some information so that even when some of the individuals
     * are gone, we can still retain some useful information.
     */
    public static final class TestResultAction extends AbstractTestResultAction {
        /**
         * Jobs to aggregate. Comma separated.
         * Never null.
         */
        private final String jobs;

        /**
         * The last time the fields of this object is computed from the rest.
         */
        private transient long lastUpdated = 0;
        /**
         * When was the last time any build completed?
         */
        private static long lastChanged = 0;

        private transient int failCount;
        private transient int totalCount;
        private transient List<AbstractTestResultAction> individuals;
        /**
         * Projects that haven't run yet.
         */
        private transient List<AbstractProjectExt> didntRun;
        private transient List<AbstractProjectExt> noFingerprints;

        public TestResultAction(String jobs, AbstractBuildExt<?,?> owner) {
            super(owner);
            if(jobs==null) {
                // resolve null as the transitive downstream jobs
                StringBuilder buf = new StringBuilder();
                for (AbstractProjectExt p : getProject().getTransitiveDownstreamProjects()) {
                    if(buf.length()>0)  buf.append(',');
                    buf.append(p.getFullName());
                }
                jobs = buf.toString();
            }
            this.jobs = jobs;
        }

        /**
         * Gets the jobs to be monitored.
         */
        public Collection<AbstractProjectExt> getJobs() {
            List<AbstractProjectExt> r = new ArrayList<AbstractProjectExt>();
            for (String job : Util.tokenize(jobs,",")) {
                AbstractProjectExt j = HudsonExt.getInstance().getItemByFullName(job.trim(), AbstractProjectExt.class);
                if(j!=null)
                    r.add(j);
            }
            return r;
        }

        private AbstractProjectExt<?,?> getProject() {
            return owner.getProject();
        }

        public int getFailCount() {
            upToDateCheck();
            return failCount;
        }

        public int getTotalCount() {
            upToDateCheck();
            return totalCount;
        }

        public Object getResult() {
            upToDateCheck();
            return this;
        }

        /**
         * Since there's no TestObject that points this action as the owner
         * (aggregated {@link TestObject}s point to their respective real owners, not 'this'),
         * so this method should be never invoked.
         *
         * @deprecated
         *      so that IDE warns you if you accidentally try to call it.
         */
        @Override
        protected String getDescription(TestObject object) {
            throw new AssertionError();
        }

        /**
         * See {@link #getDescription(TestObject)}
         *
         * @deprecated
         *      so that IDE warns you if you accidentally try to call it.
         */
        @Override
        protected void setDescription(TestObject object, String description) {
            throw new AssertionError();
        }

        /**
         * Returns the individual test results that are aggregated.
         */
        public List<AbstractTestResultAction> getIndividuals() {
            upToDateCheck();
            return Collections.unmodifiableList(individuals);
        }

        /**
         * Gets the downstream projects that haven't run yet, but
         * expected to produce test results.
         */
        public List<AbstractProjectExt> getDidntRun() {
            return Collections.unmodifiableList(didntRun);
        }

        /** 
         * Gets the downstream projects that have available test results, but 
         * do not appear to have fingerprinting enabled.
         */
        public List<AbstractProjectExt> getNoFingerprints() {
            return Collections.unmodifiableList(noFingerprints);
        }

        /**
         * Makes sure that the data fields are up to date.
         */
        private synchronized void upToDateCheck() {
            // up to date check
            if(lastUpdated>lastChanged)     return;
            lastUpdated = lastChanged+1;

            int failCount = 0;
            int totalCount = 0;
            List<AbstractTestResultAction> individuals = new ArrayList<AbstractTestResultAction>();
            List<AbstractProjectExt> didntRun = new ArrayList<AbstractProjectExt>();
            List<AbstractProjectExt> noFingerprints = new ArrayList<AbstractProjectExt>();
            for (AbstractProjectExt job : getJobs()) {
                RangeSet rs = owner.getDownstreamRelationship(job);
                if(rs.isEmpty()) {
                    // is this job expected to produce a test result?
                    RunExt b = job.getLastSuccessfulBuild();
                    if(b!=null && b.getAction(AbstractTestResultAction.class)!=null) {
                        if(b.getAction(FingerprintAction.class)!=null) {
                            didntRun.add(job);
                        } else {
                            noFingerprints.add(job);
                        }
                    }
                } else {
                    for (int n : rs.listNumbersReverse()) {
                        RunExt b = job.getBuildByNumber(n);
                        if(b==null) continue;
                        if(b.isBuilding() || b.getResult().isWorseThan(ResultExt.UNSTABLE))
                            continue;   // don't count them

                        for( AbstractTestResultAction ta : b.getActions(AbstractTestResultAction.class)) {
                            failCount += ta.getFailCount();
                            totalCount += ta.getTotalCount();
                            individuals.add(ta);
                        }
                        break;
                    }
                }
            }

            this.failCount = failCount;
            this.totalCount = totalCount;
            this.individuals = individuals;
            this.didntRun = didntRun;
            this.noFingerprints = noFingerprints;
        }

        public boolean getHasFingerprintAction() {
            return this.owner.getAction(FingerprintAction.class)!=null;
        }

        @Override
        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_Title();
        }

        @Override
        public String getUrlName() {
            return "aggregatedTestReport";
        }

        @Extension
        public static class RunListenerImpl extends RunListener<RunExt> {
            @Override
            public void onCompleted(RunExt run, TaskListener listener) {
                lastChanged = System.currentTimeMillis();
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;    // for all types
        }

        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/tasks/aggregate-test/help.html";
        }

        public FormValidation doCheck(@AncestorInPath AbstractProjectExt project, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(ItemExt.CONFIGURE))  return FormValidation.ok();

            for (String name : Util.tokenize(fixNull(value), ",")) {
                name = name.trim();
                if(HudsonExt.getInstance().getItemByFullName(name)==null)
                    return FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoSuchProject(name,AbstractProjectExt.findNearest(name).getName()));
            }
            
            return FormValidation.ok();
        }

        @Override
        public AggregatedTestResultPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            JSONObject s = formData.getJSONObject("specify");
            if(s.isNullObject())
                return new AggregatedTestResultPublisher(null);
            else
                return new AggregatedTestResultPublisher(s.getString("jobs"));
        }
    }

}

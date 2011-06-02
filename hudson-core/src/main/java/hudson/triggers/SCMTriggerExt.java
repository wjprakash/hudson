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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import hudson.util.SequentialExecutionQueue;
import java.util.concurrent.Executors;
import antlr.ANTLRException;
import hudson.UtilExt;
import hudson.Extension;
import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.Action;
import hudson.model.CauseExt;
import hudson.model.HudsonExt;
import hudson.model.SCMedItem;
import hudson.model.AdministrativeMonitorExt;
import hudson.util.StreamTaskListener;
import hudson.util.TimeUnit2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.DateFormat;


import static java.util.logging.Level.*;

/**
 * {@link Trigger} that checks for SCM updates periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class SCMTriggerExt extends Trigger<SCMedItem> {

    private static final Logger LOGGER = Logger.getLogger(SCMTriggerExt.class.getName());
    /**
     * Used to control the execution of the polling tasks.
     * <p>
     * This executor implementation has a semantics suitable for polling. Namely, no two threads will try to poll the same project
     * at once, and multiple polling requests to the same job will be combined into one. Note that because executor isn't aware
     * of a potential workspace lock between a build and a polling, we may end up using executor threads unwisely --- they
     * may block.
     */
    protected transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());
    /**
     * Whether the projects should be polled all in one go in the order of dependencies. The default behavior is
     * that each project polls for changes independently.
     */
    private boolean synchronousPolling = false;
    /**
     * Max number of threads for SCM polling.
     * 0 for unbounded.
     */
    private int maximumThreads;

    public SCMTriggerExt(String scmpoll_spec) throws ANTLRException {
        super(scmpoll_spec);
    }

    public boolean isSynchronousPolling() {
        return synchronousPolling;
    }

    public void setSynchronousPolling(boolean synchronousPolling) {
        this.synchronousPolling = synchronousPolling;
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
        // fool proof
        if (n < 0) {
            n = 0;
        }
        if (n > 100) {
            n = 100;
        }

        maximumThreads = n;

        resizeThreadPool();
    }

    /**
     * Update the {@link ExecutorService} instance.
     */
    /*package*/ synchronized void resizeThreadPool() {
        queue.setExecutors(
                (maximumThreads == 0 ? Executors.newCachedThreadPool() : Executors.newFixedThreadPool(maximumThreads)));
    }

    public ExecutorService getExecutor() {
        return queue.getExecutors();
    }

    /**
     * Returns true if the SCM polling thread queue has too many jobs
     * than it can handle.
     */
    public boolean isClogged() {
        return queue.isStarving(STARVATION_THRESHOLD);
    }

    /**
     * Checks if the queue is clogged, and if so,
     * activate {@link AdministrativeMonitorImpl}.
     */
    public void clogCheck() {
        AdministrativeMonitorExt.all().get(AdministrativeMonitorImpl.class).on = isClogged();
    }

    /**
     * Gets the snapshot of {@link Runner}s that are performing polling.
     */
    public List<Runner> getRunners() {
        return UtilExt.filter(queue.getInProgress(), Runner.class);
    }

    /**
     * Gets the snapshot of {@link SCMedItem}s that are being polled at this very moment.
     */
    public List<SCMedItem> getItemsBeingPolled() {
        List<SCMedItem> r = new ArrayList<SCMedItem>();
        for (Runner i : getRunners()) {
            r.add(i.getTarget());
        }
        return r;
    }

    @Override
    public void run() {
        run(null);
    }

    /**
     * Run the SCM trigger with additional build actions. Used by SubversionRepositoryStatus
     * to trigger a build at a specific revisionn number.
     * 
     * @param additionalActions
     * @since 1.375
     */
    public void run(Action[] additionalActions) {
        if (HudsonExt.getInstance().isQuietingDown()) {
            return; // noop
        }
        LOGGER.log(FINE, "Scheduling a polling for {0}", job);
        if (synchronousPolling) {
            LOGGER.fine("Running the trigger directly without threading, "
                    + "as it's already taken care of by Trigger.Cron");
            new Runner(additionalActions).run();
        } else {
            // schedule the polling.
            // even if we end up submitting this too many times, that's OK.
            // the real exclusion control happens inside Runner.
            LOGGER.fine("scheduling the trigger to (asynchronously) run");
            queue.execute(new Runner(additionalActions));
            clogCheck();
        }
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new SCMActionExt());
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(), "scm-polling.log");
    }

    @Extension
    public static final class AdministrativeMonitorImpl extends AdministrativeMonitorExt {

        private boolean on;

        public boolean isActivated() {
            return on;
        }
    }

    /**
     * Associated with {@link AbstractBuildExt} to show the polling log
     * that triggered that build.
     *
     * @since 1.376
     */
    public static class BuildActionExt implements Action {

        public final AbstractBuildExt build;

        public BuildActionExt(AbstractBuildExt build) {
            this.build = build;
        }

        /**
         * Polling log that triggered the build.
         */
        public File getPollingLogFile() {
            return new File(build.getRootDir(), "polling.log");
        }

        public String getIconFileName() {
            return "clipboard.gif";
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_BuildAction_DisplayName();
        }

        public String getUrlName() {
            return "pollingLog";
        }
    }

    /**
     * Action object for {@link ProjectExt}. Used to display the last polling log.
     */
    public class SCMActionExt implements Action {

        public AbstractProjectExt<?, ?> getOwner() {
            return job.asProject();
        }

        public String getIconFileName() {
            return "clipboard.gif";
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_getDisplayName(job.getScm().getDescriptor().getDisplayName());
        }

        public String getUrlName() {
            return "scmPollLog";
        }

        public String getLog() throws IOException {
            return UtilExt.loadFile(getLogFile());
        }
    }

    /**
     * {@link Runnable} that actually performs polling.
     */
    public class Runner implements Runnable {

        /**
         * When did the polling start?
         */
        private volatile long startTime;
        private Action[] additionalActions;

        public Runner() {
            additionalActions = new Action[0];
        }

        public Runner(Action[] actions) {
            if (actions == null) {
                additionalActions = new Action[0];
            } else {
                additionalActions = actions;
            }
        }

        /**
         * Where the log file is written.
         */
        public File getLogFile() {
            return SCMTriggerExt.this.getLogFile();
        }

        /**
         * For which {@link ItemExt} are we polling?
         */
        public SCMedItem getTarget() {
            return job;
        }

        /**
         * When was this polling started?
         */
        public long getStartTime() {
            return startTime;
        }

        /**
         * Human readable string of when this polling is started.
         */
        public String getDuration() {
            return UtilExt.getTimeSpanString(System.currentTimeMillis() - startTime);
        }

        private boolean runPolling() {
            try {
                // to make sure that the log file contains up-to-date text,
                // don't do buffering.
                StreamTaskListener listener = new StreamTaskListener(getLogFile());

                try {
                    PrintStream logger = listener.getLogger();
                    long start = System.currentTimeMillis();
                    logger.println("Started on " + DateFormat.getDateTimeInstance().format(new Date()));
                    boolean result = job.poll(listener).hasChanges();
                    logger.println("Done. Took " + UtilExt.getTimeSpanString(System.currentTimeMillis() - start));
                    if (result) {
                        logger.println("Changes found");
                    } else {
                        logger.println("No changes");
                    }
                    return result;
                } catch (Error e) {
                    e.printStackTrace(listener.error("Failed to record SCM polling"));
                    LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                    throw e;
                } catch (RuntimeException e) {
                    e.printStackTrace(listener.error("Failed to record SCM polling"));
                    LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                    throw e;
                } finally {
                    listener.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                return false;
            }
        }

        public void run() {
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("SCM polling for " + job);
            try {
                startTime = System.currentTimeMillis();
                if (runPolling()) {
                    AbstractProjectExt p = job.asProject();
                    String name = " #" + p.getNextBuildNumber();
                    SCMTriggerCause cause;
                    try {
                        cause = new SCMTriggerCause(getLogFile());
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Failed to parse the polling log", e);
                        cause = new SCMTriggerCause();
                    }
                    if (p.scheduleBuild(p.getQuietPeriod(), cause, additionalActions)) {
                        LOGGER.info("SCM changes detected in " + job.getName() + ". Triggering " + name);
                    } else {
                        LOGGER.info("SCM changes detected in " + job.getName() + ". Job is already in the queue");
                    }
                }
            } finally {
                Thread.currentThread().setName(threadName);
            }
        }

        private SCMedItem job() {
            return job;
        }

        // as per the requirement of SequentialExecutionQueue, value equality is necessary
        @Override
        public boolean equals(Object that) {
            return that instanceof Runner && job() == ((Runner) that).job();
        }

        @Override
        public int hashCode() {
            return job.hashCode();
        }
    }

    public static class SCMTriggerCause extends CauseExt {

        /**
         * Only used while ths cause is in the queue.
         * Once attached to the build, we'll move this into a file to reduce the memory footprint.
         */
        private String pollingLog;

        public SCMTriggerCause(File logFile) throws IOException {
            // TODO: charset of this log file?
            this(FileUtils.readFileToString(logFile));
        }

        public SCMTriggerCause(String pollingLog) {
            this.pollingLog = pollingLog;
        }

        /**
         * @deprecated
         *      Use {@link #SCMTriggerCause(String)}.
         */
        public SCMTriggerCause() {
            this("");
        }

        @Override
        public void onAddedTo(AbstractBuildExt build) {
            try {
                BuildActionExt a = new BuildActionExt(build);
                FileUtils.writeStringToFile(a.getPollingLogFile(), pollingLog);
                build.addAction(a);
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to persist the polling log", e);
            }
            pollingLog = null;
        }

        @Override
        public String getShortDescription() {
            return Messages.SCMTrigger_SCMTriggerCause_ShortDescription();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SCMTriggerCause;
        }

        @Override
        public int hashCode() {
            return 3;
        }
    }
    /**
     * How long is too long for a polling activity to be in the queue?
     */
    public static long STARVATION_THRESHOLD = Long.getLong(SCMTriggerExt.class.getName() + ".starvationThreshold", TimeUnit2.HOURS.toMillis(1));
}

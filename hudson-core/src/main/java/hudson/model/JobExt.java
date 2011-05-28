/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Martin Eigenbrodt, 
 * Matthew R. Harrah, Red Hat, Inc., Stephen Connolly, Tom Huybrechts, Winston Prakash
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
package hudson.model;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.ExtensionPoint;
import hudson.PermalinkList;
import hudson.Extension;
import hudson.cli.declarative.CLIResolver;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.FingerprintExt.RangeSet;
import hudson.model.FingerprintExt.Range;
import hudson.search.QuickSilver;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.search.SearchItems;
import hudson.security.ACL;
import hudson.tasks.LogRotator;
import hudson.util.ColorPalette;
import hudson.util.CopyOnWriteList;
import hudson.util.graph.DataSet;
import hudson.util.IOException2;
import hudson.util.RunList;
import hudson.util.TextFile;
import hudson.util.graph.ChartLabel;
import hudson.util.graph.Graph;
import hudson.widgets.HistoryWidget;
import hudson.widgets.Widget;
import hudson.widgets.HistoryWidget.Adapter;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.LinkedList;

import org.jvnet.localizer.Localizable;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;

/**
 * A job is an runnable entity under the monitoring of HudsonExt.
 * 
 * <p>
 * Every time it "runs", it will be recorded as a {@link RunExt} object.
 *
 * <p>
 * To create a custom job type, extend {@link TopLevelItemDescriptor} and put {@link Extension} on it.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class JobExt<JobT extends JobExt<JobT, RunT>, RunT extends RunExt<JobT, RunT>>
        extends AbstractItemExt implements ExtensionPoint{

    /**
     * Next build number. Kept in a separate file because this is the only
     * information that gets updated often. This allows the rest of the
     * configuration to be in the VCS.
     * <p>
     * In 1.28 and earlier, this field was stored in the project configuration
     * file, so even though this is marked as transient, don't move it around.
     */
    protected transient volatile int nextBuildNumber = 1;
    /**
     * Newly copied jobs get this flag set, so that HudsonExt doesn't try to run the job until its configuration
     * is saved once.
     */
    private transient volatile boolean holdOffBuildUntilSave;
    protected volatile LogRotator logRotator;
    /**
     * Not all plugins are good at calculating their health report quickly.
     * These fields are used to cache the health reports to speed up rendering
     * the main page.
     */
    private transient Integer cachedBuildHealthReportsBuildNumber = null;
    private transient List<HealthReportExt> cachedBuildHealthReports = null;
    protected boolean keepDependencies;
    /**
     * List of {@link UserProperty}s configured for this project.
     */
    protected CopyOnWriteList<JobPropertyExt<? super JobT>> properties = new CopyOnWriteList<JobPropertyExt<? super JobT>>();

    protected JobExt(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public synchronized void save() throws IOException {
        super.save();
        holdOffBuildUntilSave = false;
    }

    @Override
    public void onLoad(ItemGroup<? extends ItemExt> parent, String name)
            throws IOException {
        super.onLoad(parent, name);

        TextFile f = getNextBuildNumberFile();
        if (f.exists()) {
            // starting 1.28, we store nextBuildNumber in a separate file.
            // but old HudsonExt didn't do it, so if the file doesn't exist,
            // assume that nextBuildNumber was read from config.xml
            try {
                synchronized (this) {
                    this.nextBuildNumber = Integer.parseInt(f.readTrim());
                }
            } catch (NumberFormatException e) {
                throw new IOException2(f + " doesn't contain a number", e);
            }
        } else {
            // From the old HudsonExt, or doCreateItem. Create this file now.
            saveNextBuildNumber();
            save(); // and delete it from the config.xml
        }

        if (properties == null) // didn't exist < 1.72
        {
            properties = new CopyOnWriteList<JobPropertyExt<? super JobT>>();
        }

        for (JobPropertyExt p : properties) {
            p.setOwner(this);
        }
    }

    @Override
    public void onCopiedFrom(ItemExt src) {
        super.onCopiedFrom(src);
        synchronized (this) {
            this.nextBuildNumber = 1; // reset the next build number
            this.holdOffBuildUntilSave = true;
        }
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        // if a build is in progress. Cancel it.
        RunT lb = getLastBuild();
        if (lb != null) {
            ExecutorExt e = lb.getExecutor();
            if (e != null) {
                e.interrupt();
                // should we block until the build is cancelled?
            }
        }
        super.performDelete();
    }

    /*package*/ TextFile getNextBuildNumberFile() {
        return new TextFile(new File(this.getRootDir(), "nextBuildNumber"));
    }

    protected boolean isHoldOffBuildUntilSave() {
        return holdOffBuildUntilSave;
    }

    protected synchronized void saveNextBuildNumber() throws IOException {
        if (nextBuildNumber == 0) { // #3361
            nextBuildNumber = 1;
        }
        getNextBuildNumberFile().write(String.valueOf(nextBuildNumber) + '\n');
    }

    public boolean isInQueue() {
        return false;
    }

    /**
     * If this job is in the build queue, return its item.
     */
    public QueueExt.Item getQueueItem() {
        return null;
    }

    /**
     * Returns true if a build of this project is in progress.
     */
    public boolean isBuilding() {
        RunT b = getLastBuild();
        return b != null && b.isBuilding();
    }

    @Override
    public String getPronoun() {
        return Messages.Job_Pronoun();
    }

    /**
     * Returns whether the name of this job can be changed by user.
     */
    public boolean isNameEditable() {
        return true;
    }

    /**
     * If true, it will keep all the build logs of dependency components.
     */
    public boolean isKeepDependencies() {
        return keepDependencies;
    }

    /**
     * Allocates a new buildCommand number.
     */
    public synchronized int assignBuildNumber() throws IOException {
        int r = nextBuildNumber++;
        saveNextBuildNumber();
        return r;
    }

    /**
     * Peeks the next build number.
     */
    public int getNextBuildNumber() {
        return nextBuildNumber;
    }

    /**
     * Programatically updates the next build number.
     * 
     * <p>
     * Much of HudsonExt assumes that the build number is unique and monotonic, so
     * this method can only accept a new value that's bigger than
     * {@link #getLastBuild()} returns. Otherwise it'll be no-op.
     * 
     * @since 1.199 (before that, this method was package private.)
     */
    public synchronized void updateNextBuildNumber(int next) throws IOException {
        RunT lb = getLastBuild();
        if (lb != null ? next > lb.getNumber() : next > 0) {
            this.nextBuildNumber = next;
            saveNextBuildNumber();
        }
    }

    /**
     * Returns the log rotator for this job, or null if none.
     */
    public LogRotator getLogRotator() {
        return logRotator;
    }

    public void setLogRotator(LogRotator logRotator) {
        this.logRotator = logRotator;
    }

    /**
     * Perform log rotation.
     */
    public void logRotate() throws IOException, InterruptedException {
        LogRotator lr = getLogRotator();
        if (lr != null) {
            lr.perform(this);
        }
    }

    /**
     * True if this instance supports log rotation configuration.
     */
    public boolean supportsLogRotator() {
        return true;
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new SearchIndex() {

            public void find(String token, List<SearchItem> result) {
                try {
                    if (token.startsWith("#")) {
                        token = token.substring(1); // ignore leading '#'
                    }
                    int n = Integer.parseInt(token);
                    RunExt b = getBuildByNumber(n);
                    if (b == null) {
                        return; // no such build
                    }
                    result.add(SearchItems.create("#" + n, "" + n, b));
                } catch (NumberFormatException e) {
                    // not a number.
                }
            }

            public void suggest(String token, List<SearchItem> result) {
                find(token, result);
            }
        }).add("configure", "config", "configure");
    }

    public Collection<? extends JobExt> getAllJobs() {
        return Collections.<JobExt>singleton(this);
    }

    /**
     * Adds {@link JobPropertyExt}.
     * 
     * @since 1.188
     */
    public void addProperty(JobPropertyExt<? super JobT> jobProp) throws IOException {
        ((JobPropertyExt) jobProp).setOwner(this);
        properties.add(jobProp);
        save();
    }

    /**
     * Removes {@link JobPropertyExt}
     *
     * @since 1.279
     */
    public void removeProperty(JobPropertyExt<? super JobT> jobProp) throws IOException {
        properties.remove(jobProp);
        save();
    }

    /**
     * Removes the property of the given type.
     *
     * @return
     *      The property that was just removed.
     * @since 1.279
     */
    public <T extends JobPropertyExt> T removeProperty(Class<T> clazz) throws IOException {
        for (JobPropertyExt<? super JobT> p : properties) {
            if (clazz.isInstance(p)) {
                removeProperty(p);
                return clazz.cast(p);
            }
        }
        return null;
    }

    /**
     * Gets all the job properties configured for this job.
     */
    @SuppressWarnings("unchecked")
    public Map<JobPropertyDescriptorExt, JobPropertyExt<? super JobT>> getProperties() {
        return DescriptorExt.toMap((Iterable) properties);
    }

    /**
     * List of all {@link JobPropertyExt} exposed primarily for the remoting API.
     * @since 1.282
     */
    public List<JobPropertyExt<? super JobT>> getAllProperties() {
        return properties.getView();
    }

    /**
     * Gets the specific property, or null if the propert is not configured for
     * this job.
     */
    public <T extends JobPropertyExt> T getProperty(Class<T> clazz) {
        for (JobPropertyExt p : properties) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    /**
     * Overrides from job properties.
     */
    public Collection<?> getOverrides() {
        List<Object> r = new ArrayList<Object>();
        for (JobPropertyExt<? super JobT> p : properties) {
            r.addAll(p.getJobOverrides());
        }
        return r;
    }

    public List<Widget> getWidgets() {
        ArrayList<Widget> r = new ArrayList<Widget>();
        r.add(createHistoryWidget());
        return r;
    }

    protected HistoryWidget createHistoryWidget() {
        return new HistoryWidget<JobExt, RunT>(this, getBuilds(), HISTORY_ADAPTER);
    }
    protected static final HistoryWidget.Adapter<RunExt> HISTORY_ADAPTER = new Adapter<RunExt>() {

        public int compare(RunExt record, String key) {
            try {
                int k = Integer.parseInt(key);
                return record.getNumber() - k;
            } catch (NumberFormatException nfe) {
                return String.valueOf(record.getNumber()).compareTo(key);
            }
        }

        public String getKey(RunExt record) {
            return String.valueOf(record.getNumber());
        }

        public boolean isBuilding(RunExt record) {
            return record.isBuilding();
        }

        public String getNextKey(String key) {
            try {
                int k = Integer.parseInt(key);
                return String.valueOf(k + 1);
            } catch (NumberFormatException nfe) {
                return "-unable to determine next key-";
            }
        }
    };

    /**
     * Renames a job.
     */
    @Override
    public void renameTo(String newName) throws IOException {
        super.renameTo(newName);
    }

    /**
     * Returns true if we should display "build now" icon
     */
    public abstract boolean isBuildable();

    /**
     * Gets the read-only view of all the builds.
     * 
     * @return never null. The first entry is the latest build.
     */
    @WithBridgeMethods(List.class)
    public RunList<RunT> getBuilds() {
        return RunList.fromRuns(_getRuns().values());
    }

    /**
     * Obtains all the {@link RunExt}s whose build numbers matches the given {@link RangeSet}.
     */
    public synchronized List<RunT> getBuilds(RangeSet rs) {
        List<RunT> builds = new LinkedList<RunT>();

        for (Range r : rs.getRanges()) {
            for (RunT b = getNearestBuild(r.start); b != null && b.getNumber() < r.end; b = b.getNextBuild()) {
                builds.add(b);
            }
        }

        return builds;
    }

    /**
     * Gets all the builds in a map.
     */
    public SortedMap<Integer, RunT> getBuildsAsMap() {
        return Collections.unmodifiableSortedMap(_getRuns());
    }

    /**
     * @deprecated since 2008-06-15.
     *     This is only used to support backward compatibility with old URLs.
     */
    @Deprecated
    public RunT getBuild(String id) {
        for (RunT r : _getRuns().values()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * @param n
     *            The build number.
     * @return null if no such build exists.
     * @see RunExt#getNumber()
     */
    public RunT getBuildByNumber(int n) {
        return _getRuns().get(n);
    }

    /**
     * Obtains a list of builds, in the descending order, that are within the specified time range [start,end).
     *
     * @return can be empty but never null.
     * @deprecated
     *      as of 1.372. Should just do {@code getBuilds().byTimestamp(s,e)} to avoid code bloat in {@link JobExt}.
     */
    @WithBridgeMethods(List.class)
    public RunList<RunT> getBuildsByTimestamp(long start, long end) {
        return getBuilds().byTimestamp(start, end);
    }

    @CLIResolver
    public RunT getBuildForCLI(@Argument(required = true, metaVar = "BUILD#", usage = "Build number") String id) throws CmdLineException {
        try {
            int n = Integer.parseInt(id);
            RunT r = getBuildByNumber(n);
            if (r == null) {
                throw new CmdLineException(null, "No such build '#" + n + "' exists");
            }
            return r;
        } catch (NumberFormatException e) {
            throw new CmdLineException(null, id + "is not a number");
        }
    }

    /**
     * Gets the youngest build #m that satisfies <tt>n&lt;=m</tt>.
     * 
     * This is useful when you'd like to fetch a build but the exact build might
     * be already gone (deleted, rotated, etc.)
     */
    public final RunT getNearestBuild(int n) {
        SortedMap<Integer, ? extends RunT> m = _getRuns().headMap(n - 1); // the map should
        // include n, so n-1
        if (m.isEmpty()) {
            return null;
        }
        return m.get(m.lastKey());
    }

    /**
     * Gets the latest build #m that satisfies <tt>m&lt;=n</tt>.
     * 
     * This is useful when you'd like to fetch a build but the exact build might
     * be already gone (deleted, rotated, etc.)
     */
    public final RunT getNearestOldBuild(int n) {
        SortedMap<Integer, ? extends RunT> m = _getRuns().tailMap(n);
        if (m.isEmpty()) {
            return null;
        }
        return m.get(m.firstKey());
    }

    /**
     * Directory for storing {@link RunExt} records.
     * <p>
     * Some {@link JobExt}s may not have backing data store for {@link RunExt}s, but
     * those {@link JobExt}s that use file system for storing data should use this
     * directory for consistency.
     * 
     * @see RunMap
     */
    protected File getBuildDir() {
        return new File(getRootDir(), "builds");
    }

    /**
     * Gets all the runs.
     * 
     * The resulting map must be immutable (by employing copy-on-write
     * semantics.) The map is descending order, with newest builds at the top.
     */
    protected abstract SortedMap<Integer, ? extends RunT> _getRuns();

    /**
     * Called from {@link RunExt} to remove it from this job.
     * 
     * The files are deleted already. So all the callee needs to do is to remove
     * a reference from this {@link JobExt}.
     */
    protected abstract void removeRun(RunT run);

    /**
     * Returns the last build.
     */
    @QuickSilver
    public RunT getLastBuild() {
        SortedMap<Integer, ? extends RunT> runs = _getRuns();

        if (runs.isEmpty()) {
            return null;
        }
        return runs.get(runs.firstKey());
    }

    /**
     * Returns the oldest build in the record.
     */
    @QuickSilver
    public RunT getFirstBuild() {
        SortedMap<Integer, ? extends RunT> runs = _getRuns();

        if (runs.isEmpty()) {
            return null;
        }
        return runs.get(runs.lastKey());
    }

    /**
     * Returns the last successful build, if any. Otherwise null. A successful build
     * would include either {@link Result#SUCCESS} or {@link Result#UNSTABLE}.
     * 
     * @see #getLastStableBuild()
     */
    @QuickSilver
    public RunT getLastSuccessfulBuild() {
        RunT r = getLastBuild();
        // temporary hack till we figure out what's causing this bug
        while (r != null
                && (r.isBuilding() || r.getResult() == null || r.getResult().isWorseThan(ResultExt.UNSTABLE))) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last build that was anything but stable, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @QuickSilver
    public RunT getLastUnsuccessfulBuild() {
        RunT r = getLastBuild();
        while (r != null
                && (r.isBuilding() || r.getResult() == ResultExt.SUCCESS)) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last unstable build, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @QuickSilver
    public RunT getLastUnstableBuild() {
        RunT r = getLastBuild();
        while (r != null
                && (r.isBuilding() || r.getResult() != ResultExt.UNSTABLE)) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last stable build, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @QuickSilver
    public RunT getLastStableBuild() {
        RunT r = getLastBuild();
        while (r != null
                && (r.isBuilding() || r.getResult().isWorseThan(ResultExt.SUCCESS))) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last failed build, if any. Otherwise null.
     */
    @QuickSilver
    public RunT getLastFailedBuild() {
        RunT r = getLastBuild();
        while (r != null && (r.isBuilding() || r.getResult() != ResultExt.FAILURE)) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last completed build, if any. Otherwise null.
     */
    @QuickSilver
    public RunT getLastCompletedBuild() {
        RunT r = getLastBuild();
        while (r != null && r.isBuilding()) {
            r = r.getPreviousBuild();
        }
        return r;
    }

    /**
     * Returns the last 'numberOfBuilds' builds with a build result >= 'threshold'
     * 
     * @return a list with the builds. May be smaller than 'numberOfBuilds' or even empty
     *   if not enough builds satisfying the threshold have been found. Never null.
     */
    public List<RunT> getLastBuildsOverThreshold(int numberOfBuilds, ResultExt threshold) {

        List<RunT> result = new ArrayList<RunT>(numberOfBuilds);

        RunT r = getLastBuild();
        while (r != null && result.size() < numberOfBuilds) {
            if (!r.isBuilding()
                    && (r.getResult() != null && r.getResult().isBetterOrEqualTo(threshold))) {
                result.add(r);
            }
            r = r.getPreviousBuild();
        }

        return result;
    }

    public final long getEstimatedDuration() {
        List<RunT> builds = getLastBuildsOverThreshold(3, ResultExt.UNSTABLE);

        if (builds.isEmpty()) {
            return -1;
        }

        long totalDuration = 0;
        for (RunT b : builds) {
            totalDuration += b.getDuration();
        }
        if (totalDuration == 0) {
            return -1;
        }

        return Math.round((double) totalDuration / builds.size());
    }

    /**
     * Gets all the {@link Permalink}s defined for this job.
     *
     * @return never null
     */
    public PermalinkList getPermalinks() {
        // TODO: shall we cache this?
        PermalinkList permalinks = new PermalinkList(Permalink.BUILTIN);
        for (Action a : getActions()) {
            if (a instanceof PermalinkProjectAction) {
                PermalinkProjectAction ppa = (PermalinkProjectAction) a;
                permalinks.addAll(ppa.getPermalinks());
            }
        }
        return permalinks;
    }

    /**
     * Used as the color of the status ball for the project.
     */
    public BallColorExt getIconColor() {
        RunT lastBuild = getLastBuild();
        while (lastBuild != null && lastBuild.hasntStartedYet()) {
            lastBuild = lastBuild.getPreviousBuild();
        }

        if (lastBuild != null) {
            return lastBuild.getIconColor();
        } else {
            return BallColorExt.GREY;
        }
    }

    /**
     * Get the current health report for a job.
     * 
     * @return the health report. Never returns null
     */
    public HealthReportExt getBuildHealth() {
        List<HealthReportExt> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReportExt() : reports.get(0);
    }

    public List<HealthReportExt> getBuildHealthReports() {
        List<HealthReportExt> reports = new ArrayList<HealthReportExt>();
        RunT lastBuild = getLastBuild();

        if (lastBuild != null && lastBuild.isBuilding()) {
            // show the previous build's report until the current one is
            // finished building.
            lastBuild = lastBuild.getPreviousBuild();
        }

        // check the cache
        if (cachedBuildHealthReportsBuildNumber != null
                && cachedBuildHealthReports != null
                && lastBuild != null
                && cachedBuildHealthReportsBuildNumber.intValue() == lastBuild.getNumber()) {
            reports.addAll(cachedBuildHealthReports);
        } else if (lastBuild != null) {
            for (HealthReportingAction healthReportingAction : lastBuild.getActions(HealthReportingAction.class)) {
                final HealthReportExt report = healthReportingAction.getBuildHealth();
                if (report != null) {
                    if (report.isAggregateReport()) {
                        reports.addAll(report.getAggregatedReports());
                    } else {
                        reports.add(report);
                    }
                }
            }
            final HealthReportExt report = getBuildStabilityHealthReport();
            if (report != null) {
                if (report.isAggregateReport()) {
                    reports.addAll(report.getAggregatedReports());
                } else {
                    reports.add(report);
                }
            }

            Collections.sort(reports);

            // store the cache
            cachedBuildHealthReportsBuildNumber = lastBuild.getNumber();
            cachedBuildHealthReports = new ArrayList<HealthReportExt>(reports);
        }

        return reports;
    }

    private HealthReportExt getBuildStabilityHealthReport() {
        // we can give a simple view of build health from the last five builds
        int failCount = 0;
        int totalCount = 0;
        RunT i = getLastBuild();
        while (totalCount < 5 && i != null) {
            switch (i.getIconColor()) {
                case BLUE:
                case YELLOW:
                    // failCount stays the same
                    totalCount++;
                    break;
                case RED:
                    failCount++;
                    totalCount++;
                    break;

                default:
                    // do nothing as these are inconclusive statuses
                    break;
            }
            i = i.getPreviousBuild();
        }
        if (totalCount > 0) {
            int score = (int) ((100.0 * (totalCount - failCount)) / totalCount);

            Localizable description;
            if (failCount == 0) {
                description = Messages._Job_NoRecentBuildFailed();
            } else if (totalCount == failCount) {
                // this should catch the case where totalCount == 1
                // as failCount must be between 0 and totalCount
                // and we can't get here if failCount == 0
                description = Messages._Job_AllRecentBuildFailed();
            } else {
                description = Messages._Job_NOfMFailed(failCount, totalCount);
            }
            return new HealthReportExt(score, Messages._Job_BuildStability(description));
        }
        return null;
    }

    public String getBuildStatusUrl() {
        return getIconColor().getImage();
    }

    public Graph getBuildTimeGraph() {
        Graph graph = new Graph(getLastBuild().getTimestamp(), 500, 400);

        DataSet<String, ChartLabel> data = new DataSet<String, ChartLabel>();
        for (RunExt r : getBuilds()) {
            if (r.isBuilding()) {
                continue;
            }
            data.add(((double) r.getDuration()) / (1000 * 60), "min",
                    new TimeTrendChartLabel(r));
        }
        graph.setXAxisLabel(Messages.Job_minutes());
        graph.setData(data);

        return graph;
    }

    private class TimeTrendChartLabel extends ChartLabel {

        final RunExt run;

        public TimeTrendChartLabel(RunExt r) {
            this.run = r;
        }

        public int compareTo(ChartLabel that) {
            return this.run.number - ((TimeTrendChartLabel) that).run.number;
        }

        @Override
        public boolean equals(Object o) {
            // HUDSON-2682 workaround for Eclipse compilation bug
            // on (c instanceof ChartLabel)
            if (o == null || !ChartLabel.class.isAssignableFrom(o.getClass())) {
                return false;
            }
            TimeTrendChartLabel that = (TimeTrendChartLabel) o;
            return run == that.run;
        }

        public Color getColor(int row, int column) {
            // TODO: consider gradation. See
            // http://www.javadrive.jp/java2d/shape/index9.html
            ResultExt r = run.getResult();
            if (r == ResultExt.FAILURE) {
                return ColorPalette.RED;
            } else if (r == ResultExt.UNSTABLE) {
                return ColorPalette.YELLOW;
            } else if (r == ResultExt.ABORTED || r == ResultExt.NOT_BUILT) {
                return ColorPalette.GREY;
            } else {
                return ColorPalette.BLUE;
            }
        }

        @Override
        public int hashCode() {
            return run.hashCode();
        }

        @Override
        public String toString() {
            String l = run.getDisplayName();
            if (run instanceof Build) {
                String s = ((Build) run).getBuiltOnStr();
                if (s != null) {
                    l += ' ' + s;
                }
            }
            return l;
        }

        @Override
        public String getLink(int row, int column) {
            return String.valueOf(run.number);
        }

        @Override
        public String getToolTip(int row, int column) {
            return run.getDisplayName() + " : " + run.getDurationString();
        }
    }

    /**
     * Returns the {@link ACL} for this object.
     * We need to override the identical method in AbstractItemExt because we won't
     * call getACL(JobExt) otherwise (single dispatch)
     */
    @Override
    public ACL getACL() {
        return HudsonExt.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public BuildTimelineWidgetExt getTimeline() {
        return new BuildTimelineWidgetExt(getBuilds());
    }
}

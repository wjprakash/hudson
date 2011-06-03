/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Romain Seguy, Yahoo! Inc.,
 * Darek Ostolski
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

import hudson.model.RunExt.Runner;
import hudson.model.RunExt.Runner;
import hudson.console.ConsoleLogFilter;
import hudson.FunctionsExt;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FilePathExt;
import hudson.UtilExt;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.console.ConsoleNote;
import hudson.matrix.MatrixBuildExt;
import hudson.matrix.MatrixRunExt;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.tasks.LogRotatorExt;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildStep;
import hudson.tasks.test.AbstractTestResultActionExt;
import hudson.util.IOException2;
import hudson.util.LogTaskListener;
import hudson.util.XStream2;
import hudson.util.ProcessTree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;


import net.sf.json.JSONObject;
import org.apache.commons.io.input.NullInputStream;

import com.thoughtworks.xstream.XStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import static java.util.logging.Level.FINE;

/**
 * A particular execution of {@link JobExt}.
 *
 * <p>
 * Custom {@link RunExt} type is always used in conjunction with
 * a custom {@link JobExt} type, so there's no separate registration
 * mechanism for custom {@link RunExt} types.
 *
 * @author Kohsuke Kawaguchi
 * @see RunListener
 */
public abstract class RunExt <JobT extends JobExt<JobT,RunT>,RunT extends RunExt<JobT,RunT>>
        extends ActionableExt implements ExtensionPoint, Comparable<RunT>, AccessControlled, PersistenceRoot, DescriptorByNameOwner {

    protected transient final JobT project;
    
    protected String url;

    /**
     * Build number.
     *
     * <p>
     * In earlier versions &lt; 1.24, this number is not unique nor continuous,
     * but going forward, it will, and this really replaces the build id.
     */
    public /*final*/ int number;

    /**
     * Previous build. Can be null.
     * These two fields are maintained and updated by {@link RunMap}.
     */
    protected volatile transient RunT previousBuild;

    /**
     * Next build. Can be null.
     */
    protected volatile transient RunT nextBuild;

    /**
     * Pointer to the next younger build in progress. This data structure is lazily updated,
     * so it may point to the build that's already completed. This pointer is set to 'this'
     * if the computation determines that everything earlier than this build is already completed.
     */
    /* does not compile on JDK 7: private*/ volatile transient RunT previousBuildInProgress;

    /**
     * When the build is scheduled.
     */
    protected transient final long timestamp;

    /**
     * The build result.
     * This value may change while the state is in {@link State#BUILDING}.
     */
    protected volatile ResultExt result;

    /**
     * Human-readable description. Can be null.
     */
    protected volatile String description;

    /**
     * Human-readable name of this build. Can be null.
     * If non-null, this text is displayed instead of "#NNN", which is the default.
     * @since 1.390
     */
    private volatile String displayName;

    /**
     * The current build state.
     */
    protected volatile transient State state;

    private static enum State {
        /**
         * Build is created/queued but we haven't started building it.
         */
        NOT_STARTED,
        /**
         * Build is in progress.
         */
        BUILDING,
        /**
         * Build is completed now, and the status is determined,
         * but log files are still being updated.
         */
        POST_PRODUCTION,
        /**
         * Build is completed now, and log file is closed.
         */
        COMPLETED
    }

    /**
     * Number of milli-seconds it took to run this build.
     */
    protected long duration;

    /**
     * Charset in which the log file is written.
     * For compatibility reason, this field may be null.
     * For persistence, this field is string and not {@link Charset}.
     *
     * @see #getCharset()
     * @since 1.257
     */
    protected String charset;

    /**
     * Keeps this log entries.
     */
    private boolean keepLog;

    /**
     * If the build is in progress, remember {@link Runner} that's running it.
     * This field is not persisted.
     */
    private volatile transient Runner runner;

    protected static final ThreadLocal<SimpleDateFormat> ID_FORMATTER =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                }
            };

    /**
     * Creates a new {@link RunExt}.
     */
    protected RunExt(JobT job) throws IOException {
        this(job, new GregorianCalendar());
        this.number = project.assignBuildNumber();
    }

    /**
     * Constructor for creating a {@link RunExt} object in
     * an arbitrary state.
     */
    protected RunExt(JobT job, Calendar timestamp) {
        this(job,timestamp.getTimeInMillis());
    }

    protected RunExt(JobT job, long timestamp) {
        this.project = job;
        this.timestamp = timestamp;
        this.state = State.NOT_STARTED;
    }

    /**
     * Loads a run from a log file.
     */
    protected RunExt(JobT project, File buildDir) throws IOException {
        this(project, parseTimestampFromBuildDir(buildDir));
        this.previousBuildInProgress = _this(); // loaded builds are always completed
        this.state = State.COMPLETED;
        this.result = ResultExt.FAILURE;  // defensive measure. value should be overwritten by unmarshal, but just in case the saved data is inconsistent
        getDataFile().unmarshal(this); // load the rest of the data
    }
    
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    /**
     * Called after the build is loaded and the object is added to the build list.
     */
    protected void onLoad() {
        for (Action a : getActions())
            if (a instanceof RunAction)
                ((RunAction) a).onLoad();
    }

    @Override
    public void addAction(Action a) {
        super.addAction(a);
        if (a instanceof RunAction)
            ((RunAction) a).onAttached(this);
    }

    /*package*/ static long parseTimestampFromBuildDir(File buildDir) throws IOException {
        try {
            return ID_FORMATTER.get().parse(buildDir.getName()).getTime();
        } catch (ParseException e) {
            throw new IOException2("Invalid directory name "+buildDir,e);
        } catch (NumberFormatException e) {
            throw new IOException2("Invalid directory name "+buildDir,e);
        }
    }

    /**
     * Obtains 'this' in a more type safe signature.
     */
    @SuppressWarnings({"unchecked"})
    private RunT _this() {
        return (RunT)this;
    }

    /**
     * Ordering based on build numbers.
     */
    public int compareTo(RunT that) {
        return this.number - that.number;
    }

    /**
     * Returns the build result.
     *
     * <p>
     * When a build is {@link #isBuilding() in progress}, this method
     * returns an intermediate result.
     */
    public ResultExt getResult() {
        return result;
    }

    public void setResult(ResultExt r) {
        // state can change only when we are building
        assert state==State.BUILDING;

        // result can only get worse
        if(result==null) {
            result = r;
            LOGGER.log(FINE, toString()+" : result is set to "+r,new Exception());
        } else {
            if(r.isWorseThan(result)) {
                LOGGER.log(FINE, toString()+" : result is set to "+r,new Exception());
                result = r;
            }
        }
    }

    /**
     * Gets the subset of {@link #getActions()} that consists of {@link BuildBadgeAction}s.
     */
    public List<BuildBadgeAction> getBadgeActions() {
        List<BuildBadgeAction> r = null;
        for (Action a : getActions()) {
            if(a instanceof BuildBadgeAction) {
                if(r==null)
                    r = new ArrayList<BuildBadgeAction>();
                r.add((BuildBadgeAction)a);
            }
        }
        if(isKeepLog()) {
            if(r==null)
                r = new ArrayList<BuildBadgeAction>();
            r.add(new KeepLogBuildBadge());
        }
        if(r==null)     return Collections.emptyList();
        else            return r;
    }

    /**
     * Returns true if the build is not completed yet.
     * This includes "not started yet" state.
     */
    public boolean isBuilding() {
        return state.compareTo(State.POST_PRODUCTION) < 0;
    }

    /**
     * Returns true if the log file is still being updated.
     */
    public boolean isLogUpdated() {
        return state.compareTo(State.COMPLETED) < 0;
    }

    /**
     * Gets the {@link ExecutorExt} building this job, if it's being built.
     * Otherwise null.
     */
    public ExecutorExt getExecutor() {
        for( ComputerExt c : HudsonExt.getInstance().getComputers() ) {
            for (ExecutorExt e : c.getExecutors()) {
                if(e.getCurrentExecutable()==this)
                    return e;
            }
        }
        return null;
    }

    /**
     * Gets the charset in which the log file is written.
     * @return never null.
     * @since 1.257
     */
    public final Charset getCharset() {
        if(charset==null)   return Charset.defaultCharset();
        return Charset.forName(charset);
    }

    /**
     * Returns the {@link CauseExt}s that tirggered a build.
     *
     * <p>
     * If a build sits in the queue for a long time, multiple build requests made during this period
     * are all rolled up into one build, hence this method may return a list.
     *
     * @return
     *      can be empty but never null. read-only.
     * @since 1.321
     */
    public List<CauseExt> getCauses() {
        CauseActionExt a = getAction(CauseActionExt.class);
        if (a==null)    return Collections.emptyList();
        return Collections.unmodifiableList(a.getCauses());
    }

    /**
     * Returns a {@link CauseExt} of a particular type.
     *
     * @since 1.362
     */
    public <T extends CauseExt> T getCause(Class<T> type) {
        for (CauseExt c : getCauses())
            if (type.isInstance(c))
                return type.cast(c);
        return null;
    }

    /**
     * Returns true if this log file should be kept and not deleted.
     *
     * This is used as a signal to the {@link LogRotator}.
     */
    public boolean isKeepLog() {
        return getWhyKeepLog()!=null;
    }

    /**
     * If {@link #isKeepLog()} returns true, returns a human readable
     * one-line string that explains why it's being kept.
     */
    public String getWhyKeepLog() {
        if(keepLog)
            return Messages.Run_MarkedExplicitly();
        return null;    // not marked at all
    }

    /**
     * The project this build is for.
     */
    public JobT getParent() {
        return project;
    }

    /**
     * When the build is scheduled.
     */
    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(timestamp);
        return c;
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     */
    public final Date getTime() {
        return new Date(timestamp);
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type, that is since the time of the epoc.
     */
    public final long getTimeInMillis() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }


    /**
     * Returns the length-limited description.
     * @return The length-limited description.
     */
    public String getTruncatedDescription() {
        final int maxDescrLength = 100;
        if (description == null || description.length() < maxDescrLength) {
            return description;
        }

        final String ending = "...";
        final int sz = description.length(), maxTruncLength = maxDescrLength - ending.length();

        boolean inTag = false;
        int displayChars = 0;
        int lastTruncatablePoint = -1;

        for (int i=0; i<sz; i++) {
            char ch = description.charAt(i);
            if(ch == '<') {
                inTag = true;
            } else if (ch == '>') {
                inTag = false;
                if (displayChars <= maxTruncLength) {
                    lastTruncatablePoint = i + 1;
                }
            }
            if (!inTag) {
                displayChars++;
                if (displayChars <= maxTruncLength && ch == ' ') {
                    lastTruncatablePoint = i;
                }
            }
        }

        String truncDesc = description;

        // Could not find a preferred truncable index, force a trunc at maxTruncLength
        if (lastTruncatablePoint == -1)
            lastTruncatablePoint = maxTruncLength;

        if (displayChars >= maxDescrLength) {
            truncDesc = truncDesc.substring(0, lastTruncatablePoint) + ending;
        }
        
        return truncDesc;
        
    }

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis()-timestamp;
        return UtilExt.getPastTimeString(duration);
    }

    /**
     * Returns the timestamp formatted in xs:dateTime.
     */
    public String getTimestampString2() {
        return UtilExt.XS_DATETIME_FORMATTER.format(new Date(timestamp));
    }

    /**
     * Gets the string that says how long the build took to run.
     */
    public String getDurationString() {
        if(isBuilding())
            return Messages.Run_InProgressDuration(
                    UtilExt.getTimeSpanString(System.currentTimeMillis()-timestamp));
        return UtilExt.getTimeSpanString(duration);
    }

    /**
     * Gets the millisecond it took to build.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Gets the icon color for display.
     */
    public BallColorExt getIconColor() {
        if(!isBuilding()) {
            // already built
            return getResult().color;
        }

        // a new build is in progress
        BallColorExt baseColor;
        if(previousBuild==null)
            baseColor = BallColorExt.GREY;
        else
            baseColor = previousBuild.getIconColor();

        return baseColor.anime();
    }

    /**
     * Returns true if the build is still queued and hasn't started yet.
     */
    public boolean hasntStartedYet() {
        return state ==State.NOT_STARTED;
    }

    @Override
    public String toString() {
        return getFullDisplayName();
    }

    public String getFullDisplayName() {
        return project.getFullDisplayName()+' '+getDisplayName();
    }

    public String getDisplayName() {
        return displayName!=null ? displayName : "#"+number;
    }

    public boolean hasCustomDisplayName() {
        return displayName!=null;
    }

    /**
     * @param value
     *      Set to null to revert back to the default "#NNN".
     */
    public void setDisplayName(String value) throws IOException {
        checkPermission(UPDATE);
        this.displayName = value;
        save();
    }

    public int getNumber() {
        return number;
    }

    public RunT getPreviousBuild() {
        return previousBuild;
    }

    /**
     * Gets the most recent {@linkplain #isBuilding() completed} build excluding 'this' RunExt itself.
     */
    public final RunT getPreviousCompletedBuild() {
        RunT r=getPreviousBuild();
        while (r!=null && r.isBuilding())
            r=r.getPreviousBuild();
        return r;
    }

    /**
     * Obtains the next younger build in progress. It uses a skip-pointer so that we can compute this without
     * O(n) computation time. This method also fixes up the skip list as we go, in a way that's concurrency safe.
     *
     * <p>
     * We basically follow the existing skip list, and wherever we find a non-optimal pointer, we remember them
     * in 'fixUp' and update them later.
     */
    public final RunT getPreviousBuildInProgress() {
        if(previousBuildInProgress==this)   return null;    // the most common case

        List<RunT> fixUp = new ArrayList<RunT>();
        RunT r = _this(); // 'r' is the source of the pointer (so that we can add it to fix up if we find that the target of the pointer is inefficient.)
        RunT answer;
        while (true) {
            RunT n = r.previousBuildInProgress;
            if (n==null) {// no field computed yet.
                n=r.getPreviousBuild();
                fixUp.add(r);
            }
            if (r==n || n==null) {
                // this indicates that we know there's no build in progress beyond this point
                answer = null;
                break;
            }
            if (n.isBuilding()) {
                // we now know 'n' is the target we wanted
                answer = n;
                break;
            }

            fixUp.add(r);   // r contains the stale 'previousBuildInProgress' back pointer
            r = n;
        }

        // fix up so that the next look up will run faster
        for (RunT f : fixUp)
            f.previousBuildInProgress = answer==null ? f : answer;
        return answer;
    }

    /**
     * Returns the last build that was actually built - i.e., skipping any with Result.NOT_BUILT
     */
    public RunT getPreviousBuiltBuild() {
        RunT r=previousBuild;
        // in certain situations (aborted m2 builds) r.getResult() can still be null, although it should theoretically never happen
        while( r!=null && (r.getResult() == null || r.getResult()==ResultExt.NOT_BUILT) )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last build that didn't fail before this build.
     */
    public RunT getPreviousNotFailedBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()==ResultExt.FAILURE )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last failed build before this build.
     */
    public RunT getPreviousFailedBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()!=ResultExt.FAILURE )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last successful build before this build.
     * @since 1.383
     */
    public RunT getPreviousSuccessfulBuild() {
        RunT r=previousBuild;
        while( r!=null && r.getResult()!=ResultExt.SUCCESS )
            r=r.previousBuild;
        return r;
    }

    /**
     * Returns the last 'numberOfBuilds' builds with a build result >= 'threshold'.
     * 
     * @param numberOfBuilds the desired number of builds
     * @param threshold the build result threshold
     * @return a list with the builds (youngest build first).
     *   May be smaller than 'numberOfBuilds' or even empty
     *   if not enough builds satisfying the threshold have been found. Never null.
     * @since 1.383
     */
    public List<RunT> getPreviousBuildsOverThreshold(int numberOfBuilds, ResultExt threshold) {
        List<RunT> builds = new ArrayList<RunT>(numberOfBuilds);
        
        RunT r = getPreviousBuild();
        while (r != null && builds.size() < numberOfBuilds) {
            if (!r.isBuilding() && 
                 (r.getResult() != null && r.getResult().isBetterOrEqualTo(threshold))) {
                builds.add(r);
            }
            r = r.getPreviousBuild();
        }
        
        return builds;
    }

    public RunT getNextBuild() {
        return nextBuild;
    }


    public final String getSearchUrl() {
        return getNumber() + "/";
    }

    /**
     * Unique ID of this build.
     */
    public String getId() {
        return ID_FORMATTER.get().format(new Date(timestamp));
    }
    
    /**
     * Get the date formatter used to convert the directory name in to a timestamp
     * This is nasty exposure of private data, but needed all the time the directory
     * containing the build is used as it's timestamp.
     */
    public static DateFormat getIDFormatter() {
    	return ID_FORMATTER.get();
    }

    public DescriptorExt getDescriptorByName(String className) {
        return HudsonExt.getInstance().getDescriptorByName(className);
    }

    /**
     * Root directory of this {@link RunExt} on the master.
     *
     * Files related to this {@link RunExt} should be stored below this directory.
     */
    public File getRootDir() {
        File f = new File(project.getBuildDir(),getId());
        f.mkdirs();
        return f;
    }

    /**
     * Gets the directory where the artifacts are archived.
     */
    public File getArtifactsDir() {
        return new File(getRootDir(),"archive");
    }

    /**
     * Gets the artifacts (relative to {@link #getArtifactsDir()}.
     */
    public List<Artifact> getArtifacts() {
        return getArtifactsUpTo(Integer.MAX_VALUE);
    }

    /**
     * Gets the first N artifacts.
     */
    public List<Artifact> getArtifactsUpTo(int n) {
        ArtifactList r = new ArtifactList();
        addArtifacts(getArtifactsDir(),"","",r,null,n);
        r.computeDisplayName();
        return r;
    }

    /**
     * Returns true if this run has any artifacts.
     *
     * <p>
     * The strange method name is so that we can access it from EL.
     */
    public boolean getHasArtifacts() {
        return !getArtifactsUpTo(1).isEmpty();
    }

    private int addArtifacts( File dir, String path, String pathHref, ArtifactList r, Artifact parent, int upTo ) {
        String[] children = dir.list();
        if(children==null)  return 0;
        Arrays.sort(children, String.CASE_INSENSITIVE_ORDER);

        int n = 0;
        for (String child : children) {
            String childPath = path + child;
            String childHref = pathHref + UtilExt.rawEncode(child);
            File sub = new File(dir, child);
            boolean collapsed = (children.length==1 && parent!=null);
            Artifact a;
            if (collapsed) {
                // Collapse single items into parent node where possible:
                a = new Artifact(parent.getFileName() + '/' + child, childPath,
                                 sub.isDirectory() ? null : childHref, parent.getTreeNodeId());
                r.tree.put(a, r.tree.remove(parent));
            } else {
                // Use null href for a directory:
                a = new Artifact(child, childPath,
                                 sub.isDirectory() ? null : childHref, "n" + ++r.idSeq);
                r.tree.put(a, parent!=null ? parent.getTreeNodeId() : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(sub, childPath + '/', childHref + '/', r, a, upTo-n);
                if (n>=upTo) break;
            } else {
                // Don't store collapsed path in ArrayList (for correct data in external API)
                r.add(collapsed ? new Artifact(child, a.relativePath, a.href, a.treeNodeId) : a);
                if (++n>=upTo) break;
            }
        }
        return n;
    }

    /**
     * Maximum number of artifacts to list before using switching to the tree view.
     */
    public static final int LIST_CUTOFF = Integer.parseInt(System.getProperty("hudson.model.Run.ArtifactList.listCutoff", "16"));

    /**
     * Maximum number of artifacts to show in tree view before just showing a link.
     */
    public static final int TREE_CUTOFF = Integer.parseInt(System.getProperty("hudson.model.Run.ArtifactList.treeCutoff", "40"));

    // ..and then "too many"

    public final class ArtifactList extends ArrayList<Artifact> {
        /**
         * Map of Artifact to treeNodeId of parent node in tree view.
         * Contains Artifact objects for directories and files (the ArrayList contains only files).
         */
        private LinkedHashMap<Artifact,String> tree = new LinkedHashMap<Artifact,String>();
        private int idSeq = 0;

        public Map<Artifact,String> getTree() {
            return tree;
        }

        public void computeDisplayName() {
            if(size()>LIST_CUTOFF)   return; // we are not going to display file names, so no point in computing this

            int maxDepth = 0;
            int[] len = new int[size()];
            String[][] tokens = new String[size()][];
            for( int i=0; i<tokens.length; i++ ) {
                tokens[i] = get(i).relativePath.split("[\\\\/]+");
                maxDepth = Math.max(maxDepth,tokens[i].length);
                len[i] = 1;
            }

            boolean collision;
            int depth=0;
            do {
                collision = false;
                Map<String,Integer/*index*/> names = new HashMap<String,Integer>();
                for (int i = 0; i < tokens.length; i++) {
                    String[] token = tokens[i];
                    String displayName = combineLast(token,len[i]);
                    Integer j = names.put(displayName, i);
                    if(j!=null) {
                        collision = true;
                        if(j>=0)
                            len[j]++;
                        len[i]++;
                        names.put(displayName,-1);  // occupy this name but don't let len[i] incremented with additional collisions
                    }
                }
            } while(collision && depth++<maxDepth);

            for (int i = 0; i < tokens.length; i++)
                get(i).displayPath = combineLast(tokens[i],len[i]);

//            OUTER:
//            for( int n=1; n<maxLen; n++ ) {
//                // if we just display the last n token, would it be suffice for disambiguation?
//                Set<String> names = new HashSet<String>();
//                for (String[] token : tokens) {
//                    if(!names.add(combineLast(token,n)))
//                        continue OUTER; // collision. Increase n and try again
//                }
//
//                // this n successfully diambiguates
//                for (int i = 0; i < tokens.length; i++) {
//                    String[] token = tokens[i];
//                    get(i).displayPath = combineLast(token,n);
//                }
//                return;
//            }

//            // it's impossible to get here, as that means
//            // we have the same artifacts archived twice, but be defensive
//            for (Artifact a : this)
//                a.displayPath = a.relativePath;
        }

        /**
         * Combines last N token into the "a/b/c" form.
         */
        private String combineLast(String[] token, int n) {
            StringBuilder buf = new StringBuilder();
            for( int i=Math.max(0,token.length-n); i<token.length; i++ ) {
                if(buf.length()>0)  buf.append('/');
                buf.append(token[i]);
            }
            return buf.toString();
        }
    }

    /**
     * A build artifact.
     */
    public class Artifact {
        /**
         * Relative path name from {@link RunExt#getArtifactsDir()}
         */
        public final String relativePath;

        /**
         * Truncated form of {@link #relativePath} just enough
         * to disambiguate {@link Artifact}s.
         */
        /*package*/ String displayPath;

        /**
         * The filename of the artifact.
         * (though when directories with single items are collapsed for tree view, name may
         *  include multiple path components, like "dist/pkg/mypkg")
         */
        private String name;

        /**
         * Properly encoded relativePath for use in URLs.  This field is null for directories.
         */
        private String href;

        /**
         * Id of this node for use in tree view.
         */
        private String treeNodeId;

        /*package for test*/ Artifact(String name, String relativePath, String href, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.treeNodeId = treeNodeId;
        }

        /**
         * Gets the artifact file.
         */
        public File getFile() {
            return new File(getArtifactsDir(),relativePath);
        }

        /**
         * Returns just the file name portion, without the path.
         */
        public String getFileName() {
            return name;
        }

        public String getDisplayPath() {
            return displayPath;
        }

        public String getHref() {
            return href;
        }

        public String getTreeNodeId() {
            return treeNodeId;
        }

        @Override
        public String toString() {
            return relativePath;
        }
    }

    /**
     * Returns the log file.
     */
    public File getLogFile() {
        return new File(getRootDir(),"log");
    }

    /**
     * Returns an input stream that reads from the log file.
     * It will use a gzip-compressed log file (log.gz) if that exists.
     *
     * @throws IOException 
     * @return an input stream from the log file, or null if none exists
     * @since 1.349
     */
    public InputStream getLogInputStream() throws IOException {
    	File logFile = getLogFile();
    	if (logFile.exists() ) {
            return new FileInputStream(logFile);
    	}

    	File compressedLogFile = new File(logFile.getParentFile(), logFile.getName()+ ".gz");
    	if (compressedLogFile.exists()) {
            return new GZIPInputStream(new FileInputStream(compressedLogFile));
    	}
    	
    	return new NullInputStream(0);
    }

    public Reader getLogReader() throws IOException {
        if (charset==null)  return new InputStreamReader(getLogInputStream());
        else                return new InputStreamReader(getLogInputStream(),charset);
    }

    
    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex()
                .add("console")
                .add("changes");
        for (Action a : getActions()) {
            if(a.getIconFileName()!=null)
                builder.add(a.getUrlName());
        }
        return builder;
    }

    public void checkPermission(Permission p) {
        getACL().checkPermission(p);
    }

    public boolean hasPermission(Permission p) {
        return getACL().hasPermission(p);
    }

    public ACL getACL() {
        // for now, don't maintain ACL per run, and do it at project level
        return getParent().getACL();
    }

    /**
     * Deletes this build's artifacts. 
     *
     * @throws IOException
     *      if we fail to delete.
     *
     * @since 1.350
     */
    public synchronized void deleteArtifacts() throws IOException {
        File artifactsDir = getArtifactsDir();

        UtilExt.deleteContentsRecursive(artifactsDir);
    }

    /**
     * Deletes this build and its entire log
     *
     * @throws IOException
     *      if we fail to delete.
     */
    public synchronized void delete() throws IOException {
        RunListener.fireDeleted(this);

        // if we have a symlink, delete it, too
        File link = new File(project.getBuildDir(), String.valueOf(getNumber()));
        link.delete();

        File rootDir = getRootDir();
        File tmp = new File(rootDir.getParentFile(),'.'+rootDir.getName());
        
        boolean renamingSucceeded = rootDir.renameTo(tmp);
        UtilExt.deleteRecursive(tmp);
        // some user reported that they see some left-over .xyz files in the workspace,
        // so just to make sure we've really deleted it, schedule the deletion on VM exit, too.
        if(tmp.exists())
            tmp.deleteOnExit();

        if(!renamingSucceeded)
            throw new IOException(rootDir+" is in use");

        removeRunFromParent();
    }

    @SuppressWarnings("unchecked") // seems this is too clever for Java's type system?
    private void removeRunFromParent() {
        getParent().removeRun((RunT)this);
    }


    /**
     * @see CheckPoint#report()
     */
    /*package*/ static void reportCheckpoint(CheckPoint id) {
        RunnerStack.INSTANCE.peek().checkpoints.report(id);
    }

    /**
     * @see CheckPoint#block()
     */
    /*package*/ static void waitForCheckpoint(CheckPoint id) throws InterruptedException {
        while(true) {
            RunExt b = RunnerStack.INSTANCE.peek().getBuild().getPreviousBuildInProgress();
            if(b==null)     return; // no pending earlier build
            RunExt.Runner runner = b.runner;
            if(runner==null) {
                // polled at the wrong moment. try again.
                Thread.sleep(0);
                continue;
            }
            if(runner.checkpoints.waitForCheckPoint(id))
                return; // confirmed that the previous build reached the check point

            // the previous build finished without ever reaching the check point. try again.
        }
    }

    protected abstract class Runner {
        /**
         * Keeps track of the check points attained by a build, and abstracts away the synchronization needed to 
         * maintain this data structure.
         */
        private final class CheckpointSet {
            /**
             * Stages of the builds that this runner has completed. This is used for concurrent {@link Runner}s to
             * coordinate and serialize their executions where necessary.
             */
            private final Set<CheckPoint> checkpoints = new HashSet<CheckPoint>();

            private boolean allDone;

            protected synchronized void report(CheckPoint identifier) {
                checkpoints.add(identifier);
                notifyAll();
            }

            protected synchronized boolean waitForCheckPoint(CheckPoint identifier) throws InterruptedException {
                final Thread t = Thread.currentThread();
                final String oldName = t.getName();
                t.setName(oldName+" : waiting for "+identifier+" on "+getFullDisplayName());
                try {
                    while(!allDone && !checkpoints.contains(identifier))
                        wait();
                    return checkpoints.contains(identifier);
                } finally {
                    t.setName(oldName);
                }
            }

            /**
             * Notifies that the build is fully completed and all the checkpoint locks be released.
             */
            private synchronized void allDone() {
                allDone = true;
                notifyAll();
            }
        }

        private final CheckpointSet checkpoints = new CheckpointSet();

        /**
         * Performs the main build and returns the status code.
         *
         * @throws Exception
         *      exception will be recorded and the build will be considered a failure.
         */
        public abstract ResultExt run( BuildListener listener ) throws Exception, RunnerAbortedException;

        /**
         * Performs the post-build action.
         * <p>
         * This method is called after {@linkplain #run(BuildListener) the main portion of the build is completed.}
         * This is a good opportunity to do notifications based on the result
         * of the build. When this method is called, the build is not really
         * finalized yet, and the build is still considered in progress --- for example,
         * even if the build is successful, this build still won't be picked up
         * by {@link JobExt#getLastSuccessfulBuild()}.
         */
        public abstract void post( BuildListener listener ) throws Exception;

        /**
         * Performs final clean up action.
         * <p>
         * This method is called after {@link #post(BuildListener)},
         * after the build result is fully finalized. This is the point
         * where the build is already considered completed.
         * <p>
         * Among other things, this is often a necessary pre-condition
         * before invoking other builds that depend on this build.
         */
        public abstract void cleanUp(BuildListener listener) throws Exception;

        protected final RunT getBuild() {
            return _this();
        }
    }

    /**
     * Used in {@link Runner#run} to indicates that a fatal error in a build
     * is reported to {@link BuildListener} and the build should be simply aborted
     * without further recording a stack trace.
     */
    public static final class RunnerAbortedException extends RuntimeException {}

    protected final void run(Runner job) {
        if(result!=null)
            return;     // already built.

        StreamBuildListener listener=null;

        runner = job;
        onStartBuilding();
        try {
            // to set the state to COMPLETE in the end, even if the thread dies abnormally.
            // otherwise the queue state becomes inconsistent

            long start = System.currentTimeMillis();

            try {
                try {
                    Charset charset = ComputerExt.currentComputer().getDefaultCharset();
                    this.charset = charset.name();

                    // don't do buffering so that what's written to the listener
                    // gets reflected to the file immediately, which can then be
                    // served to the browser immediately
                    OutputStream logger = new FileOutputStream(getLogFile());
                    RunT build = job.getBuild();

                    // Global log filters
                    for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
                        logger = filter.decorateLogger((AbstractBuildExt) build, logger);
                    }

                    // Project specific log filterss
                    if (project instanceof BuildableItemWithBuildWrappers && build instanceof AbstractBuildExt) {
                        BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
                        for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                            logger = bw.decorateLogger((AbstractBuildExt) build, logger);
                        }
                    }

                    listener = new StreamBuildListener(logger,charset);

                    listener.started(getCauses());

                    RunListener.fireStarted(this,listener);

                    // create a symlink from build number to ID.
                    UtilExt.createSymlink(getParent().getBuildDir(),getId(),String.valueOf(getNumber()),listener);

                    setResult(job.run(listener));

                    LOGGER.info(toString()+" main build action completed: "+result);
                    CheckPoint.MAIN_COMPLETED.report();
                } catch (ThreadDeath t) {
                    throw t;
                } catch( AbortException e ) {// orderly abortion.
                    result = ResultExt.FAILURE;
                    listener.error(e.getMessage());
                    LOGGER.log(FINE, "Build "+this+" aborted",e);
                } catch( RunnerAbortedException e ) {// orderly abortion.
                    result = ResultExt.FAILURE;
                    LOGGER.log(FINE, "Build "+this+" aborted",e);
                } catch( InterruptedException e) {
                    // aborted
                    result = ResultExt.ABORTED;
                    listener.getLogger().println(Messages.Run_BuildAborted());
                    LOGGER.log(Level.INFO,toString()+" aborted",e);
                } catch( Throwable e ) {
                    handleFatalBuildProblem(listener,e);
                    result = ResultExt.FAILURE;
                }

                // even if the main build fails fatally, try to run post build processing
                job.post(listener);

            } catch (ThreadDeath t) {
                throw t;
            } catch( Throwable e ) {
                handleFatalBuildProblem(listener,e);
                result = ResultExt.FAILURE;
            } finally {
                long end = System.currentTimeMillis();
                duration = Math.max(end - start, 0);  // @see HUDSON-5844

                // advance the state.
                // the significance of doing this is that HudsonExt
                // will now see this build as completed.
                // things like triggering other builds requires this as pre-condition.
                // see issue #980.
                state = State.POST_PRODUCTION;

                try {
                    job.cleanUp(listener);
                } catch (Exception e) {
                    handleFatalBuildProblem(listener,e);
                    // too late to update the result now
                }

                RunListener.fireCompleted(this,listener);

                if(listener!=null)
                    listener.finished(result);
                if(listener!=null)
                    listener.closeQuietly();

                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record",e);
                }
            }

            try {
                getParent().logRotate();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to rotate log",e);
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Failed to rotate log",e);
            }
        } finally {
            onEndBuilding();
        }
    }

    /**
     * Handles a fatal build problem (exception) that occurred during the build.
     */
    private void handleFatalBuildProblem(BuildListener listener, Throwable e) {
        if(listener!=null) {
            if(e instanceof IOException)
                UtilExt.displayIOException((IOException)e,listener);

            Writer w = listener.fatalError(e.getMessage());
            if(w!=null) {
                try {
                    e.printStackTrace(new PrintWriter(w));
                    w.close();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    /**
     * Called when a job started building.
     */
    protected void onStartBuilding() {
        state = State.BUILDING;
        if (runner!=null)
            RunnerStack.INSTANCE.push(runner);
    }

    /**
     * Called when a job finished building normally or abnormally.
     */
    protected void onEndBuilding() {
        // signal that we've finished building.
        if (runner!=null) {
            // MavenBuilds may be created without their corresponding runners.
            state = State.COMPLETED;
            runner.checkpoints.allDone();
            runner = null;
            RunnerStack.INSTANCE.pop();
        } else {
            state = State.COMPLETED;
        }
        if (result == null) {
            result = ResultExt.FAILURE;
            LOGGER.warning(toString() + ": No build result is set, so marking as failure. This shouldn't happen.");
        }

        RunListener.fireFinalized(this);
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getDataFile().write(this);
        SaveableListener.fireOnChange(this, getDataFile());
    }

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM,new File(getRootDir(),"build.xml"));
    }

    /**
     * Gets the log of the build as a string.
     *
     * @deprecated since 2007-11-11.
     *     Use {@link #getLog(int)} instead as it avoids loading
     *     the whole log into memory unnecessarily.
     */
    @Deprecated
    public String getLog() throws IOException {
        return UtilExt.loadFile(getLogFile(),getCharset());
    }

    /**
     * Gets the log of the build as a list of strings (one per log line).
     * The number of lines returned is constrained by the maxLines parameter.
     *
     * @param maxLines The maximum number of log lines to return.  If the log
     * is bigger than this, only the most recent lines are returned.
     * @return A list of log lines.  Will have no more than maxLines elements.
     * @throws IOException If there is a problem reading the log file.
     */
    public List<String> getLog(int maxLines) throws IOException {
        int lineCount = 0;
        List<String> logLines = new LinkedList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getLogFile()),getCharset()));
        try {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                logLines.add(line);
                ++lineCount;
                // If we have too many lines, remove the oldest line.  This way we
                // never have to hold the full contents of a huge log file in memory.
                // Adding to and removing from the ends of a linked list are cheap
                // operations.
                if (lineCount > maxLines)
                    logLines.remove(0);
            }
        } finally {
            reader.close();
        }

        // If the log has been truncated, include that information.
        // Use set (replaces the first element) rather than add so that
        // the list doesn't grow beyond the specified maximum number of lines.
        if (lineCount > maxLines)
            logLines.set(0, "[...truncated " + (lineCount - (maxLines - 1)) + " lines...]");

        return ConsoleNote.removeNotes(logLines);
    }


    public String getBuildStatusUrl() {
        return getIconColor().getImage();
    }

    public static class Summary {
        /**
         * Is this build worse or better, compared to the previous build?
         */
        public boolean isWorse;
        public String message;

        public Summary(boolean worse, String message) {
            this.isWorse = worse;
            this.message = message;
        }
    }

    /**
     * Gets an object that computes the single line summary of this build.
     */
    public Summary getBuildStatusSummary() {
        RunExt prev = getPreviousBuild();

        if(getResult()==ResultExt.SUCCESS) {
            if(prev==null || prev.getResult()== ResultExt.SUCCESS)
                return new Summary(false, Messages.Run_Summary_Stable());
            else
                return new Summary(false, Messages.Run_Summary_BackToNormal());
        }

        if(getResult()==ResultExt.FAILURE) {
            RunT since = getPreviousNotFailedBuild();
            if(since==null)
                return new Summary(false, Messages.Run_Summary_BrokenForALongTime());
            if(since==prev)
                return new Summary(true, Messages.Run_Summary_BrokenSinceThisBuild());
            RunT failedBuild = since.getNextBuild();
            return new Summary(false, Messages.Run_Summary_BrokenSince(failedBuild.getDisplayName()));
        }

        if(getResult()==ResultExt.ABORTED)
            return new Summary(false, Messages.Run_Summary_Aborted());

        if(getResult()==ResultExt.UNSTABLE) {
            if(((RunExt)this) instanceof AbstractBuildExt) {
                AbstractTestResultActionExt trN = ((AbstractBuildExt)(RunExt)this).getTestResultAction();
                AbstractTestResultActionExt trP = prev==null ? null : ((AbstractBuildExt) prev).getTestResultAction();
                if(trP==null) {
                    if(trN!=null && trN.getFailCount()>0)
                        return new Summary(false, Messages.Run_Summary_TestFailures(trN.getFailCount()));
                    else // ???
                        return new Summary(false, Messages.Run_Summary_Unstable());
                }
                if(trP.getFailCount()==0)
                    return new Summary(true, Messages.Run_Summary_TestsStartedToFail(trN.getFailCount()));
                if(trP.getFailCount() < trN.getFailCount())
                    return new Summary(true, Messages.Run_Summary_MoreTestsFailing(trN.getFailCount()-trP.getFailCount(), trN.getFailCount()));
                if(trP.getFailCount() > trN.getFailCount())
                    return new Summary(false, Messages.Run_Summary_LessTestsFailing(trP.getFailCount()-trN.getFailCount(), trN.getFailCount()));

                return new Summary(false, Messages.Run_Summary_TestsStillFailing(trN.getFailCount()));
            }
        }

        return new Summary(false, Messages.Run_Summary_Unknown());
    }

    /**
     * Serves the artifacts.
     */
    public DirectoryBrowserSupportExt doArtifact() {
        if(FunctionsExt.isArtifactsPermissionEnabled()) {
          checkPermission(ARTIFACTS);
        }
        return new DirectoryBrowserSupportExt(this,new FilePathExt(getArtifactsDir()), project.getDisplayName()+' '+getDisplayName(), "package.gif", true);
    }

    /**
     * Marks this build to keep the log.
     */
    @CLIMethod(name="keep-build")
    public final void keepLog() throws IOException {
        keepLog(true);
    }

    public void keepLog(boolean newValue) throws IOException {
        checkPermission(UPDATE);
        keepLog = newValue;
        save();
    }


    public void setDescription(String description) throws IOException {
        checkPermission(UPDATE);
        this.description = description;
        save();
    }
    

    /**
     * @deprecated as of 1.292
     *      Use {@link #getEnvironment()} instead.
     */
    public Map<String,String> getEnvVars() {
        try {
            return getEnvironment();
        } catch (IOException e) {
            return new EnvVars();
        } catch (InterruptedException e) {
            return new EnvVars();
        }
    }

    /**
     * @deprecated as of 1.305 use {@link #getEnvironment(TaskListener)}
     */
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
    }

    /**
     * Returns the map that contains environmental variables to be used for launching
     * processes for this build.
     *
     * <p>
     * {@link BuildStep}s that invoke external processes should use this.
     * This allows {@link BuildWrapper}s and other project configurations (such as JDK selection)
     * to take effect.
     *
     * <p>
     * Unlike earlier {@link #getEnvVars()}, this map contains the whole environment,
     * not just the overrides, so one can introspect values to change its behavior.
     * @since 1.305
     */
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars env = getCharacteristicEnvVars();
        ComputerExt c = ComputerExt.currentComputer();
        if (c!=null)
            env = c.getEnvironment().overrideAll(env);
        
        if(!env.containsKey("HUDSON_HOME"))
            env.put("HUDSON_HOME", HudsonExt.getInstance().getRootDir().getPath() );

        Thread t = Thread.currentThread();
        if (t instanceof ExecutorExt) {
            ExecutorExt e = (ExecutorExt) t;
            env.put("EXECUTOR_NUMBER",String.valueOf(e.getNumber()));
            env.put("NODE_NAME",e.getOwner().getName());
            NodeExt n = e.getOwner().getNode();
            if (n!=null)
                env.put("NODE_LABELS",UtilExt.join(n.getAssignedLabels()," "));
        }

        for (EnvironmentContributor ec : EnvironmentContributor.all())
            ec.buildEnvironmentFor(this,env,log);

        return env;
    }

    /**
     * Builds up the environment variable map that's sufficient to identify a process
     * as ours. This is used to kill run-away processes via {@link ProcessTree#killAll(Map)}.
     */
    public final EnvVars getCharacteristicEnvVars() {
        EnvVars env = new EnvVars();
        env.put("HUDSON_SERVER_COOKIE",UtilExt.getDigestOf("ServerID:"+HudsonExt.getInstance().getSecretKey()));
        env.put("BUILD_NUMBER",String.valueOf(number));
        env.put("BUILD_ID",getId());
        env.put("BUILD_TAG","hudson-"+getParent().getName()+"-"+number);
        env.put("JOB_NAME",getParent().getFullName());
        return env;
    }

    public String getExternalizableId() {
        return project.getName() + "#" + getNumber();
    }

    public static RunExt<?,?> fromExternalizableId(String id) {
        int hash = id.lastIndexOf('#');
        if (hash <= 0) {
            throw new IllegalArgumentException("Invalid id");
        }
        String jobName = id.substring(0, hash);
        int number = Integer.parseInt(id.substring(hash + 1));

        JobExt<?,?> job = (JobExt<?,?>) HudsonExt.getInstance().getItem(jobName);
        return job.getBuildByNumber(number);
    }

    /**
     * Returns the estimated duration for this run if it is currently running.
     * Default to {@link JobExt#getEstimatedDuration()}, may be overridden in subclasses
     * if duration may depend on run specific parameters (like incremental Maven builds).
     * 
     * @return the estimated duration in milliseconds
     * @since 1.383
     */
    public long getEstimatedDuration() {
        return project.getEstimatedDuration();
    }


    protected void submit(JSONObject json) throws IOException {
        setDisplayName(UtilExt.fixEmptyAndTrim(json.getString("displayName")));
        setDescription(json.getString("description"));
    }

    public static final XStream XSTREAM = new XStream2();
    static {
        XSTREAM.alias("build",FreeStyleBuild.class);
        XSTREAM.alias("matrix-build",MatrixBuildExt.class);
        XSTREAM.alias("matrix-run",MatrixRunExt.class);
        XSTREAM.registerConverter(ResultExt.conv);
    }

    private static final Logger LOGGER = Logger.getLogger(RunExt.class.getName());

    /**
     * Sort by date. Newer ones first. 
     */
    public static final Comparator<RunExt> ORDER_BY_DATE = new Comparator<RunExt>() {
        public int compare(RunExt lhs, RunExt rhs) {
            long lt = lhs.getTimeInMillis();
            long rt = rhs.getTimeInMillis();
            if(lt>rt)   return -1;
            if(lt<rt)   return 1;
            return 0;
        }
    };

    /**
     * {@link BuildBadgeAction} that shows the logs are being kept.
     */
    public final class KeepLogBuildBadge implements BuildBadgeAction {
        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
        public String getWhyKeepLog() { return RunExt.this.getWhyKeepLog(); }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(RunExt.class,Messages._Run_Permissions_Title());
    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete",Messages._Run_DeletePermission_Description(),Permission.DELETE);
    public static final Permission UPDATE = new Permission(PERMISSIONS,"Update",Messages._Run_UpdatePermission_Description(),Permission.UPDATE);
    /** See {@link hudson.FunctionsExt#isArtifactsPermissionEnabled} */
    public static final Permission ARTIFACTS = new Permission(PERMISSIONS,"Artifacts",Messages._Run_ArtifactsPermission_Description(), null,
                                                              FunctionsExt.isArtifactsPermissionEnabled());

}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Brian Westrich, Erik Ramfelt, Ertan Deniz, Jean-Baptiste Quenot,
 * Luca Domenico Milanesio, R. Tyler Ballance, Stephen Connolly, Tom Huybrechts,
 * id:cactusman, Yahoo! Inc.
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

import java.util.regex.Pattern;
import antlr.ANTLRException;
import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.FilePathExt;
import hudson.Launcher;
import hudson.Util;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.CauseExt.LegacyCodeCause;
import hudson.model.FingerprintExt.RangeSet;
import hudson.model.QueueExt.Executable;
import hudson.model.QueueExt.Task;
import hudson.model.queue.SubTask;
import hudson.model.QueueExt.WaitingItem;
import hudson.model.RunMap.Constructor;
import hudson.model.labels.LabelAtomExt;
import hudson.model.labels.LabelExpression;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTaskContributor;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCMExt;
import hudson.scm.SCMRevisionState;
import hudson.search.SearchIndexBuilder;
import hudson.security.Permission;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import hudson.util.EditDistance;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.scm.PollingResult.*;

/**
 * Base implementation of {@link JobExt}s that build software.
 *
 * For now this is primarily the common part of {@link Project} and MavenModule.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractBuildExt
 */
public abstract class AbstractProjectExt<P extends AbstractProjectExt<P,R>,R extends AbstractBuildExt<P,R>> extends JobExt<P,R> implements BuildableItem {

    /**
     * {@link SCM} associated with the project.
     * To allow derived classes to link {@link SCM} config to elsewhere,
     * access to this variable should always go through {@link #getScm()}.
     */
    private volatile SCMExt scm = new NullSCM();

    /**
     * State returned from {@link SCM#poll(AbstractProjectExt, Launcher, FilePathExt, TaskListener, SCMRevisionState)}.
     */
    private volatile transient SCMRevisionState pollingBaseline = null;

    /**
     * All the builds keyed by their build number.
     */
    protected transient /*almost final*/ RunMap<R> builds = new RunMap<R>();

    /**
     * The quiet period. Null to delegate to the system default.
     */
    protected volatile Integer quietPeriod = null;
    
    /**
     * The retry count. Null to delegate to the system default.
     */
    protected volatile Integer scmCheckoutRetryCount = null;

    /**
     * If this project is configured to be only built on a certain label,
     * this value will be set to that label.
     *
     * For historical reasons, this is called 'assignedNode'. Also for
     * a historical reason, null to indicate the affinity
     * with the master node.
     *
     * @see #canRoam
     */
    protected String assignedNode;

    /**
     * True if this project can be built on any node.
     *
     * <p>
     * This somewhat ugly flag combination is so that we can migrate
     * existing HudsonExt installations nicely.
     */
    protected volatile boolean canRoam;

    /**
     * True to suspend new builds.
     */
    protected volatile boolean disabled;

    /**
     * True to keep builds of this project in queue when downstream projects are
     * building. False by default to keep from breaking existing behavior.
     */
    protected volatile boolean blockBuildWhenDownstreamBuilding = false;

    /**
     * True to keep builds of this project in queue when upstream projects are
     * building. False by default to keep from breaking existing behavior.
     */
    protected volatile boolean blockBuildWhenUpstreamBuilding = false;

    /**
     * Identifies {@link JDKExt} to be used.
     * Null if no explicit configuration is required.
     *
     * <p>
     * Can't store {@link JDKExt} directly because {@link HudsonExt} and {@link Project}
     * are saved independently.
     *
     * @see HudsonExt#getJDK(String)
     */
    protected volatile String jdk;

    /**
     * @deprecated since 2007-01-29.
     */
    private transient boolean enableRemoteTrigger;

    protected volatile BuildAuthorizationTokenExt authToken = null;

    /**
     * List of all {@link Trigger}s for this project.
     */
    protected List<Trigger<?>> triggers = new Vector<Trigger<?>>();

    /**
     * {@link Action}s contributed from subsidiary objects associated with
     * {@link AbstractProjectExt}, such as from triggers, builders, publishers, etc.
     *
     * We don't want to persist them separately, and these actions
     * come and go as configuration change, so it's kept separate.
     */
    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<Action>();

    protected boolean concurrentBuild;
    
    /**
    * True to clean the workspace prior to each build.
    */
    protected volatile boolean cleanWorkspaceRequired;

    protected AbstractProjectExt(ItemGroup parent, String name) {
        super(parent,name);

        if(!HudsonExt.getInstance().getNodes().isEmpty()) {
            // if a new job is configured with HudsonExt that already has slave nodes
            // make it roamable by default
            canRoam = true;
        }
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        // solicit initial contributions, especially from TransientProjectActionFactory
        updateTransientActions();
    }

    @Override
    public void onLoad(ItemGroup<? extends ItemExt> parent, String name) throws IOException {
        super.onLoad(parent, name);

        this.builds = new RunMap<R>();
        this.builds.load(this,new Constructor<R>() {
            public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });

        // boolean! Can't tell if xml file contained false..
        if (enableRemoteTrigger) OldDataMonitorExt.report(this, "1.77");
        if(triggers==null) {
            // it didn't exist in < 1.28
            triggers = new Vector<Trigger<?>>();
            OldDataMonitorExt.report(this, "1.28");
        }
        for (Trigger t : triggers)
            t.start(this,false);
        if(scm==null)
            scm = new NullSCM(); // perhaps it was pointing to a plugin that no longer exists.

        if(transientActions==null)
            transientActions = new Vector<Action>();    // happens when loaded from disk
        updateTransientActions();
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        // prevent a new build while a delete operation is in progress
        makeDisabled(true);
        FilePathExt ws = getWorkspace();
        if(ws!=null) {
            NodeExt on = getLastBuiltOn();
            getScm().processWorkspaceBeforeDeletion(this, ws, on);
            if(on!=null)
                on.getFileSystemProvisioner().discardWorkspace(this,ws);
        }
        super.performDelete();
    }

    /**
     * Does this project perform concurrent builds?
     * @since 1.319
     */
    public boolean isConcurrentBuild() {
        return HudsonExt.CONCURRENT_BUILD && concurrentBuild;
    }

    public void setConcurrentBuild(boolean b) throws IOException {
        concurrentBuild = b;
        save();
    }
    
    public boolean isCleanWorkspaceRequired() {
        return cleanWorkspaceRequired;
    }

    /**
     * If this project is configured to be always built on this node,
     * return that {@link NodeExt}. Otherwise null.
     */
    public LabelExt getAssignedLabel() {
        if(canRoam)
            return null;

        if(assignedNode==null)
            return HudsonExt.getInstance().getSelfLabel();
        return HudsonExt.getInstance().getLabel(assignedNode);
    }

    /**
     * Gets the textual representation of the assigned label as it was entered by the user.
     */
    public String getAssignedLabelString() {
        if (canRoam || assignedNode==null)    return null;
        try {
            LabelExpression.parseExpression(assignedNode);
            return assignedNode;
        } catch (ANTLRException e) {
            // must be old label or host name that includes whitespace or other unsafe chars
            return LabelAtomExt.escape(assignedNode);
        }
    }

    /**
     * Sets the assigned label.
     */
    public void setAssignedLabel(LabelExt l) throws IOException {
        if(l==null) {
            canRoam = true;
            assignedNode = null;
        } else {
            canRoam = false;
            if(l==HudsonExt.getInstance().getSelfLabel())  assignedNode = null;
            else                                        assignedNode = l.getExpression();
        }
        save();
    }

    /**
     * Assigns this job to the given node. A convenience method over {@link #setAssignedLabel(LabelExt)}.
     */
    public void setAssignedNode(NodeExt l) throws IOException {
        setAssignedLabel(l.getSelfLabel());
    }

    /**
     * Get the term used in the UI to represent this kind of {@link AbstractProjectExt}.
     * Must start with a capital letter.
     */
    @Override
    public String getPronoun() {
        return Messages.AbstractProject_Pronoun();
    }

    /**
     * Returns the root project value.
     *
     * @return the root project value.
     */
    public AbstractProjectExt getRootProject() {
        if (this.getParent() instanceof HudsonExt) {
            return this;
        } else {
            return ((AbstractProjectExt) this.getParent()).getRootProject();
        }
    }

    /**
     * Gets the directory where the module is checked out.
     *
     * @return
     *      null if the workspace is on a slave that's not connected.
     * @deprecated as of 1.319
     *      To support concurrent builds of the same project, this method is moved to {@link AbstractBuildExt}.
     *      For backward compatibility, this method returns the right {@link AbstractBuildExt#getWorkspace()} if called
     *      from {@link ExecutorExt}, and otherwise the workspace of the last build.
     *
     *      <p>
     *      If you are calling this method during a build from an executor, switch it to {@link AbstractBuildExt#getWorkspace()}.
     *      If you are calling this method to serve a file from the workspace, doing a form validation, etc., then
     *      use {@link #getSomeWorkspace()}
     */
    public final FilePathExt getWorkspace() {
        AbstractBuildExt b = getBuildForDeprecatedMethods();
        return b != null ? b.getWorkspace() : null;

    }
    
    /**
     * Various deprecated methods in this class all need the 'current' build.  This method returns
     * the build suitable for that purpose.
     * 
     * @return An AbstractBuildExt for deprecated methods to use.
     */
    private AbstractBuildExt getBuildForDeprecatedMethods() {
        ExecutorExt e = ExecutorExt.currentExecutor();
        if(e!=null) {
            Executable exe = e.getCurrentExecutable();
            if (exe instanceof AbstractBuildExt) {
                AbstractBuildExt b = (AbstractBuildExt) exe;
                if(b.getProject()==this)
                    return b;
            }
        }
        R lb = getLastBuild();
        if(lb!=null)    return lb;
        return null;
    }

    /**
     * Gets a workspace for some build of this project.
     *
     * <p>
     * This is useful for obtaining a workspace for the purpose of form field validation, where exactly
     * which build the workspace belonged is less important. The implementation makes a cursory effort
     * to find some workspace.
     *
     * @return
     *      null if there's no available workspace.
     * @since 1.319
     */
    public final FilePathExt getSomeWorkspace() {
        R b = getSomeBuildWithWorkspace();
        return b!=null ? b.getWorkspace() : null;
    }

    /**
     * Gets some build that has a live workspace.
     *
     * @return null if no such build exists.
     */
    public final R getSomeBuildWithWorkspace() {
        int cnt=0;
        for (R b = getLastBuild(); cnt<5 && b!=null; b=b.getPreviousBuild()) {
            FilePathExt ws = b.getWorkspace();
            if (ws!=null)   return b;
        }
        return null;
    }

    /**
     * Returns the root directory of the checked-out module.
     * <p>
     * This is usually where <tt>pom.xml</tt>, <tt>build.xml</tt>
     * and so on exists.
     *
     * @deprecated as of 1.319
     *      See {@link #getWorkspace()} for a migration strategy.
     */
    public FilePathExt getModuleRoot() {
        AbstractBuildExt b = getBuildForDeprecatedMethods();
        return b != null ? b.getModuleRoot() : null;
    }

    /**
     * Returns the root directories of all checked-out modules.
     * <p>
     * Some SCMs support checking out multiple modules into the same workspace.
     * In these cases, the returned array will have a length greater than one.
     * @return The roots of all modules checked out from the SCM.
     *
     * @deprecated as of 1.319
     *      See {@link #getWorkspace()} for a migration strategy.
     */
    public FilePathExt[] getModuleRoots() {
        AbstractBuildExt b = getBuildForDeprecatedMethods();
        return b != null ? b.getModuleRoots() : null;
    }

    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : HudsonExt.getInstance().getQuietPeriod();
    }
    
    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount !=null ? scmCheckoutRetryCount : HudsonExt.getInstance().getScmCheckoutRetryCount();
    }

    // ugly name because of EL
    public boolean getHasCustomQuietPeriod() {
        return quietPeriod!=null;
    }

    /**
     * Sets the custom quiet period of this project, or revert to the global default if null is given. 
     */
    public void setQuietPeriod(Integer seconds) throws IOException {
        this.quietPeriod = seconds;
        save();
    }
    
    public boolean hasCustomScmCheckoutRetryCount(){
        return scmCheckoutRetryCount != null;
    }

    @Override
    public boolean isBuildable() {
        return !isDisabled() && !isHoldOffBuildUntilSave();
    }

    /**
     * Used in <tt>sidepanel.jelly</tt> to decide whether to display
     * the config/delete/build links.
     */
    public boolean isConfigurable() {
        return true;
    }

    public boolean blockBuildWhenDownstreamBuilding() {
        return blockBuildWhenDownstreamBuilding;
    }

    public void setBlockBuildWhenDownstreamBuilding(boolean b) throws IOException {
        blockBuildWhenDownstreamBuilding = b;
        save();
    }

    public boolean blockBuildWhenUpstreamBuilding() {
        return blockBuildWhenUpstreamBuilding;
    }

    public void setBlockBuildWhenUpstreamBuilding(boolean b) throws IOException {
        blockBuildWhenUpstreamBuilding = b;
        save();
    }

    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Marks the build as disabled.
     */
    public void makeDisabled(boolean b) throws IOException {
        if(disabled==b)     return; // noop
        this.disabled = b;
        if(b)
            HudsonExt.getInstance().getQueue().cancel(this);
        save();
    }

    public void disable() throws IOException {
        makeDisabled(true);
    }

    public void enable() throws IOException {
        makeDisabled(false);
    }

    @Override
    public BallColorExt getIconColor() {
        if(isDisabled())
            return BallColorExt.DISABLED;
        else
            return super.getIconColor();
    }

    /**
     * effectively deprecated. Since using updateTransientActions correctly
     * under concurrent environment requires a lock that can too easily cause deadlocks.
     *
     * <p>
     * Override {@link #createTransientActions()} instead.
     */
    protected void updateTransientActions() {
        transientActions = createTransientActions();
    }

    protected List<Action> createTransientActions() {
        Vector<Action> ta = new Vector<Action>();

        for (JobPropertyExt<? super P> p : properties)
            ta.addAll(p.getJobActions((P)this));

        for (TransientProjectActionFactory tpaf : TransientProjectActionFactory.all())
            ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
        return ta;
    }

    /**
     * Returns the live list of all {@link Publisher}s configured for this project.
     *
     * <p>
     * This method couldn't be called <tt>getPublishers()</tt> because existing methods
     * in sub-classes return different inconsistent types.
     */
    public abstract DescribableList<Publisher,DescriptorExt<Publisher>> getPublishersList();

    @Override
    public void addProperty(JobPropertyExt<? super P> jobProp) throws IOException {
        super.addProperty(jobProp);
        updateTransientActions();
    }

    public List<ProminentProjectAction> getProminentActions() {
        List<Action> a = getActions();
        List<ProminentProjectAction> pa = new Vector<ProminentProjectAction>();
        for (Action action : a) {
            if(action instanceof ProminentProjectAction)
                pa.add((ProminentProjectAction) action);
        }
        return pa;
    }

	/**
	 * @deprecated
	 *    Use {@link #scheduleBuild(CauseExt)}.  Since 1.283
	 */
    public boolean scheduleBuild() {
    	return scheduleBuild(new LegacyCodeCause());
    }
    
	/**
	 * @deprecated
	 *    Use {@link #scheduleBuild(int, CauseExt)}.  Since 1.283
	 */
    public boolean scheduleBuild(int quietPeriod) {
    	return scheduleBuild(quietPeriod, new LegacyCodeCause());
    }
    
    /**
     * Schedules a build of this project.
     *
     * @return
     *      true if the project is actually added to the queue.
     *      false if the queue contained it and therefore the add()
     *      was noop
     */
    public boolean scheduleBuild(CauseExt c) {
        return scheduleBuild(getQuietPeriod(), c);
    }

    public boolean scheduleBuild(int quietPeriod, CauseExt c) {
        return scheduleBuild(quietPeriod, c, new Action[0]);
    }

    /**
     * Schedules a build.
     *
     * Important: the actions should be persistable without outside references (e.g. don't store
     * references to this project). To provide parameters for a parameterized project, add a ParametersAction. If
     * no ParametersAction is provided for such a project, one will be created with the default parameter values.
     *
     * @param quietPeriod the quiet period to observer
     * @param c the cause for this build which should be recorded
     * @param actions a list of Actions that will be added to the build
     * @return whether the build was actually scheduled
     */
    public boolean scheduleBuild(int quietPeriod, CauseExt c, Action... actions) {
        return scheduleBuild2(quietPeriod,c,actions)!=null;
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions
     *      For the convenience of the caller, this array can contain null, and those will be silently ignored.
     */
    public Future<R> scheduleBuild2(int quietPeriod, CauseExt c, Action... actions) {
        return scheduleBuild2(quietPeriod,c,Arrays.asList(actions));
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions
     *      For the convenience of the caller, this collection can contain null, and those will be silently ignored.
     * @since 1.383
     */
    public Future<R> scheduleBuild2(int quietPeriod, CauseExt c, Collection<? extends Action> actions) {
        if (!isBuildable())
            return null;

        List<Action> queueActions = new ArrayList<Action>(actions);
        if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
            queueActions.add(new ParametersAction(getDefaultParametersValues()));
        }

        if (c != null) {
            queueActions.add(new CauseActionExt(c));
        }

        WaitingItem i = HudsonExt.getInstance().getQueue().schedule(this, quietPeriod, queueActions);
        if(i!=null)
            return (Future)i.getFuture();
        return null;
    }

    private List<ParameterValueExt> getDefaultParametersValues() {
        ParametersDefinitionProperty paramDefProp = getProperty(ParametersDefinitionProperty.class);
        ArrayList<ParameterValueExt> defValues = new ArrayList<ParameterValueExt>();
        
        /*
         * This check is made ONLY if someone will call this method even if isParametrized() is false.
         */
        if(paramDefProp == null)
            return defValues;
        
        /* Scan for all parameter with an associated default values */
        for(ParameterDefinitionExt paramDefinition : paramDefProp.getParameterDefinitions())
        {
           ParameterValueExt defaultValue  = paramDefinition.getDefaultParameterValue();
            
            if(defaultValue != null)
                defValues.add(defaultValue);           
        }
        
        return defValues;
    }

    /**
     * Schedules a build, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * <p>
     * Production code shouldn't be using this, but for tests this is very convenient, so this isn't marked
     * as deprecated.
     */
    public Future<R> scheduleBuild2(int quietPeriod) {
        return scheduleBuild2(quietPeriod, new LegacyCodeCause());
    }
    
    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     */
    public Future<R> scheduleBuild2(int quietPeriod, CauseExt c) {
        return scheduleBuild2(quietPeriod, c, new Action[0]);
    }

    /**
     * Schedules a polling of this project.
     */
    public boolean schedulePolling() {
        if(isDisabled())    return false;
        SCMTrigger scmt = getTrigger(SCMTrigger.class);
        if(scmt==null)      return false;
        scmt.run();
        return true;
    }

    /**
     * Returns true if the build is in the queue.
     */
    @Override
    public boolean isInQueue() {
        return HudsonExt.getInstance().getQueue().contains(this);
    }

    @Override
    public QueueExt.Item getQueueItem() {
        return HudsonExt.getInstance().getQueue().getItem(this);
    }

    /**
     * Gets the JDKExt that this project is configured with, or null.
     */
    public JDKExt getJDK() {
        return HudsonExt.getInstance().getJDK(jdk);
    }

    /**
     * Overwrites the JDKExt setting.
     */
    public void setJDK(JDKExt jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    public BuildAuthorizationTokenExt getAuthToken() {
        return authToken;
    }

    @Override
    public SortedMap<Integer, ? extends R> _getRuns() {
        return builds.getView();
    }

    @Override
    public void removeRun(R run) {
        this.builds.remove(run);
    }

    /**
     * Determines Class&lt;R>.
     */
    protected abstract Class<R> getBuildClass();

    // keep track of the previous time we started a build
    private transient long lastBuildStartTime;
    
    /**
     * Creates a new build of this project for immediate execution.
     */
    protected synchronized R newBuild() throws IOException {
    	// make sure we don't start two builds in the same second
    	// so the build directories will be different too
    	long timeSinceLast = System.currentTimeMillis() - lastBuildStartTime;
    	if (timeSinceLast < 1000) {
    		try {
				Thread.sleep(1000 - timeSinceLast);
			} catch (InterruptedException e) {
			}
    	}
    	lastBuildStartTime = System.currentTimeMillis();
        try {
            R lastBuild = getBuildClass().getConstructor(getClass()).newInstance(this);
            builds.put(lastBuild);
            return lastBuild;
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    private IOException handleInvocationTargetException(InvocationTargetException e) {
        Throwable t = e.getTargetException();
        if(t instanceof Error)  throw (Error)t;
        if(t instanceof RuntimeException)   throw (RuntimeException)t;
        if(t instanceof IOException)    return (IOException)t;
        throw new Error(t);
    }

    /**
     * Loads an existing build record from disk.
     */
    protected R loadBuild(File dir) throws IOException {
        try {
            return getBuildClass().getConstructor(getClass(),File.class).newInstance(this,dir);
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Note that this method returns a read-only view of {@link Action}s.
     * {@link BuildStep}s and others who want to add a project action
     * should do so by implementing {@link BuildStep#getProjectActions(AbstractProjectExt)}.
     *
     * @see TransientProjectActionFactory
     */
    @Override
    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    /**
     * Gets the {@link NodeExt} where this project was last built on.
     *
     * @return
     *      null if no information is available (for example,
     *      if no build was done yet.)
     */
    public NodeExt getLastBuiltOn() {
        // where was it built on?
        AbstractBuildExt b = getLastBuild();
        if(b==null)
            return null;
        else
            return b.getBuiltOn();
    }

    public Object getSameNodeConstraint() {
        return this; // in this way, any member that wants to run with the main guy can nominate the project itself 
    }

    public final Task getOwnerTask() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A project must be blocked if its own previous build is in progress,
     * or if the blockBuildWhenUpstreamBuilding option is true and an upstream
     * project is building, but derived classes can also check other conditions.
     */
    public boolean isBuildBlocked() {
        return getCauseOfBlockage()!=null;
    }

    public String getWhyBlocked() {
        CauseOfBlockage cb = getCauseOfBlockage();
        return cb!=null ? cb.getShortDescription() : null;
    }

    /**
     * Blocked because the previous build is already in progress.
     */
    public static class BecauseOfBuildInProgress extends CauseOfBlockage {
        private final AbstractBuildExt<?,?> build;

        public BecauseOfBuildInProgress(AbstractBuildExt<?, ?> build) {
            this.build = build;
        }

        @Override
        public String getShortDescription() {
            ExecutorExt e = build.getExecutor();
            String eta = "";
            if (e != null)
                eta = Messages.AbstractProject_ETA(e.getEstimatedRemainingTime());
            int lbn = build.getNumber();
            return Messages.AbstractProject_BuildInProgress(lbn, eta);
        }
    }
    
    /**
     * Because the downstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfDownstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProjectExt<?,?> up;

        public BecauseOfDownstreamBuildInProgress(AbstractProjectExt<?,?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.AbstractProject_DownstreamBuildInProgress(up.getName());
        }
    }

    /**
     * Because the upstream build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfUpstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProjectExt<?,?> up;

        public BecauseOfUpstreamBuildInProgress(AbstractProjectExt<?,?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.AbstractProject_UpstreamBuildInProgress(up.getName());
        }
    }

    public CauseOfBlockage getCauseOfBlockage() {
        if (isBuilding() && !isConcurrentBuild())
            return new BecauseOfBuildInProgress(getLastBuild());
        if (blockBuildWhenDownstreamBuilding()) {
            AbstractProjectExt<?,?> bup = getBuildingDownstream();
            if (bup!=null)
                return new BecauseOfDownstreamBuildInProgress(bup);
        } else if (blockBuildWhenUpstreamBuilding()) {
            AbstractProjectExt<?,?> bup = getBuildingUpstream();
            if (bup!=null)
                return new BecauseOfUpstreamBuildInProgress(bup);
        }
        return null;
    }

    /**
     * Returns the project if any of the downstream project (or itself) is either
     * building or is in the queue.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    protected AbstractProjectExt getBuildingDownstream() {
    	DependencyGraph graph = HudsonExt.getInstance().getDependencyGraph();
        Set<AbstractProjectExt> tups = graph.getTransitiveDownstream(this);
        tups.add(this);
        for (AbstractProjectExt tup : tups) {
            if(tup!=this && (tup.isBuilding() || tup.isInQueue()))
                return tup;
        }
        return null;
    }

    /**
     * Returns the project if any of the upstream project (or itself) is either
     * building or is in the queue.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    protected AbstractProjectExt getBuildingUpstream() {
    	DependencyGraph graph = HudsonExt.getInstance().getDependencyGraph();
        Set<AbstractProjectExt> tups = graph.getTransitiveUpstream(this);
        tups.add(this);
        for (AbstractProjectExt tup : tups) {
            if(tup!=this && (tup.isBuilding() || tup.isInQueue()))
                return tup;
        }
        return null;
    }

    public List<SubTask> getSubTasks() {
        List<SubTask> r = new ArrayList<SubTask>();
        r.add(this);
        for (SubTaskContributor euc : SubTaskContributor.all())
            r.addAll(euc.forProject(this));
        for (JobPropertyExt<? super P> p : properties)
            r.addAll(p.getSubTasks());
        return r;
    }

    public R createExecutable() throws IOException {
        if(isDisabled())    return null;
        return newBuild();
    }

    public void checkAbortPermission() {
        checkPermission(AbstractProjectExt.ABORT);
    }

    public boolean hasAbortPermission() {
        return hasPermission(AbstractProjectExt.ABORT);
    }

    /**
     * Gets the {@link Resource} that represents the workspace of this project.
     * Useful for locking and mutual exclusion control.
     *
     * @deprecated as of 1.319
     *      Projects no longer have a fixed workspace, ands builds will find an available workspace via
     *      {@link WorkspaceList} for each build (furthermore, that happens after a build is started.)
     *      So a {@link Resource} representation for a workspace at the project level no longer makes sense.
     *
     *      <p>
     *      If you need to lock a workspace while you do some computation, see the source code of
     *      {@link #pollSCMChanges(TaskListener)} for how to obtain a lock of a workspace through {@link WorkspaceList}.
     */
    public Resource getWorkspaceResource() {
        return new Resource(getFullDisplayName()+" workspace");
    }

    /**
     * List of necessary resources to perform the build of this project.
     */
    public ResourceList getResourceList() {
        final Set<ResourceActivity> resourceActivities = getResourceActivities();
        final List<ResourceList> resourceLists = new ArrayList<ResourceList>(1 + resourceActivities.size());
        for (ResourceActivity activity : resourceActivities) {
            if (activity != this && activity != null) {
                // defensive infinite recursion and null check
                resourceLists.add(activity.getResourceList());
            }
        }
        return ResourceList.union(resourceLists);
    }

    /**
     * Set of child resource activities of the build of this project (override in child projects).
     * @return The set of child resource activities of the build of this project.
     */
    protected Set<ResourceActivity> getResourceActivities() {
        return Collections.emptySet();
    }

    public boolean checkout(AbstractBuildExt build, Launcher launcher, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        SCMExt scm = getScm();
        if(scm==null)
            return true;    // no SCM

        FilePathExt workspace = build.getWorkspace();
        workspace.mkdirs();
        
        boolean r = scm.checkout(build, launcher, workspace, listener, changelogFile);
        calcPollingBaseline(build, launcher, listener);
        return r;
    }

    /**
     * Pushes the baseline up to the newly checked out revision.
     */
    private void calcPollingBaseline(AbstractBuildExt build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        SCMRevisionState baseline = build.getAction(SCMRevisionState.class);
        if (baseline==null) {
            try {
                baseline = getScm()._calcRevisionsFromBuild(build, launcher, listener);
            } catch (AbstractMethodError e) {
                baseline = SCMRevisionState.NONE; // pre-1.345 SCM implementations, which doesn't use the baseline in polling
            }
            if (baseline!=null)
                build.addAction(baseline);
        }
        pollingBaseline = baseline;
    }

    /**
     * For reasons I don't understand, if I inline this method, AbstractMethodError escapes try/catch block.
     */
    private SCMRevisionState safeCalcRevisionsFromBuild(AbstractBuildExt build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return getScm()._calcRevisionsFromBuild(build, launcher, listener);
    }

    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * @deprecated as of 1.346
     *      Use {@link #poll(TaskListener)} instead.
     */
    public boolean pollSCMChanges( TaskListener listener ) {
        return poll(listener).hasChanges();
    }
    
    /**
     * Checks if there's any update in SCM, and returns true if any is found.
     *
     * <p>
     * The implementation is responsible for ensuring mutual exclusion between polling and builds
     * if necessary.
     *
     * @since 1.345
     */
    public PollingResult poll( TaskListener listener ) {
        SCMExt scm = getScm();
        if (scm==null) {
            listener.getLogger().println(Messages.AbstractProject_NoSCM());
            return NO_CHANGES;
        }
        if (isDisabled()) {
            listener.getLogger().println(Messages.AbstractProject_Disabled());
            return NO_CHANGES;
        }

        R lb = getLastBuild();
        if (lb==null) {
            listener.getLogger().println(Messages.AbstractProject_NoBuilds());
            return isInQueue() ? NO_CHANGES : BUILD_NOW;
        }

        if (pollingBaseline==null) {
            R success = getLastSuccessfulBuild(); // if we have a persisted baseline, we'll find it by this
            for (R r=lb; r!=null; r=r.getPreviousBuild()) {
                SCMRevisionState s = r.getAction(SCMRevisionState.class);
                if (s!=null) {
                    pollingBaseline = s;
                    break;
                }
                if (r==success) break;  // searched far enough
            }
            // NOTE-NO-BASELINE:
            // if we don't have baseline yet, it means the data is built by old HudsonExt that doesn't set the baseline
            // as action, so we need to compute it. This happens later.
        }

        try {
            if (scm.requiresWorkspaceForPolling()) {
                // lock the workspace of the last build
                FilePathExt ws=lb.getWorkspace();

                if (ws==null || !ws.exists()) {
                    // workspace offline. build now, or nothing will ever be built
                    LabelExt label = getAssignedLabel();
                    if (label != null && label.isSelfLabel()) {
                        // if the build is fixed on a node, then attempting a build will do us
                        // no good. We should just wait for the slave to come back.
                        listener.getLogger().println(Messages.AbstractProject_NoWorkspace());
                        return NO_CHANGES;
                    }
                    listener.getLogger().println( ws==null
                        ? Messages.AbstractProject_WorkspaceOffline()
                        : Messages.AbstractProject_NoWorkspace());
                    if (isInQueue()) {
                        listener.getLogger().println(Messages.AbstractProject_AwaitingBuildForWorkspace());
                        return NO_CHANGES;
                    } else {
                        listener.getLogger().println(Messages.AbstractProject_NewBuildForWorkspace());
                        return BUILD_NOW;
                    }
                } else {
                    WorkspaceList l = lb.getBuiltOn().toComputer().getWorkspaceList();
                    // if doing non-concurrent build, acquire a workspace in a way that causes builds to block for this workspace.
                    // this prevents multiple workspaces of the same job --- the behavior of HudsonExt < 1.319.
                    //
                    // OTOH, if a concurrent build is chosen, the user is willing to create a multiple workspace,
                    // so better throughput is achieved over time (modulo the initial cost of creating that many workspaces)
                    // by having multiple workspaces
                    WorkspaceList.Lease lease = l.acquire(ws, !concurrentBuild);
                    Launcher launcher = ws.createLauncher(listener);
                    try {
                        LOGGER.fine("Polling SCM changes of " + getName());
                        if (pollingBaseline==null) // see NOTE-NO-BASELINE above
                            calcPollingBaseline(lb,launcher,listener);
                        PollingResult r = scm.poll(this, launcher, ws, listener, pollingBaseline);
                        pollingBaseline = r.remote;
                        return r;
                    } finally {
                        lease.release();
                    }
                }
            } else {
                // polling without workspace
                LOGGER.fine("Polling SCM changes of " + getName());

                if (pollingBaseline==null) // see NOTE-NO-BASELINE above
                    calcPollingBaseline(lb,null,listener);
                PollingResult r = scm.poll(this, null, null, listener, pollingBaseline);
                pollingBaseline = r.remote;
                return r;
            }
        } catch (AbortException e) {
            listener.getLogger().println(e.getMessage());
            listener.fatalError(Messages.AbstractProject_Aborted());
            LOGGER.log(Level.FINE, "Polling "+this+" aborted",e);
            return NO_CHANGES;
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
            return NO_CHANGES;
        } catch (InterruptedException e) {
            e.printStackTrace(listener.fatalError(Messages.AbstractProject_PollingABorted()));
            return NO_CHANGES;
        }
    }

    /**
     * Returns true if this user has made a commit to this project.
     *
     * @since 1.191
     */
    public boolean hasParticipant(UserExt user) {
        for( R build = getLastBuild(); build!=null; build=build.getPreviousBuild())
            if(build.hasParticipant(user))
                return true;
        return false;
    }

    public SCMExt getScm() {
        return scm;
    }

    public void setScm(SCMExt scm) throws IOException {
        this.scm = scm;
        save();
    }

    /**
     * Adds a new {@link Trigger} to this {@link Project} if not active yet.
     */
    public void addTrigger(Trigger<?> trigger) throws IOException {
        addToList(trigger,triggers);
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger,triggers);
    }

    protected final synchronized <T extends Describable<T>>
    void addToList( T item, List<T> collection ) throws IOException {
        for( int i=0; i<collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item.getDescriptor()) {
                // replace
                collection.set(i,item);
                save();
                return;
            }
        }
        // add
        collection.add(item);
        save();
        updateTransientActions();
    }

    protected final synchronized <T extends Describable<T>>
    void removeFromList(DescriptorExt<T> item, List<T> collection) throws IOException {
        for( int i=0; i< collection.size(); i++ ) {
            if(collection.get(i).getDescriptor()==item) {
                // found it
                collection.remove(i);
                save();
                updateTransientActions();
                return;
            }
        }
    }

    public synchronized Map<TriggerDescriptor,Trigger> getTriggers() {
        return (Map)DescriptorExt.toMap(triggers);
    }

    /**
     * Gets the specific trigger, or null if the propert is not configured for this job.
     */
    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        for (Trigger p : triggers) {
            if(clazz.isInstance(p))
                return clazz.cast(p);
        }
        return null;
    }

//
//
// fingerprint related
//
//
    /**
     * True if the builds of this project produces {@link FingerprintExt} records.
     */
    public abstract boolean isFingerprintConfigured();

    /**
     * Gets the other {@link AbstractProjectExt}s that should be built
     * when a build of this project is completed.
     */
    public  List<AbstractProjectExt> getDownstreamProjects() {
        return HudsonExt.getInstance().getDependencyGraph().getDownstream(this);
    }

    public  List<AbstractProjectExt> getUpstreamProjects() {
        return HudsonExt.getInstance().getDependencyGraph().getUpstream(this);
    }

    /**
     * Returns only those upstream projects that defines {@link BuildTrigger} to this project.
     * This is a subset of {@link #getUpstreamProjects()}
     *
     * @return A List of upstream projects that has a {@link BuildTrigger} to this project.
     */
    public final List<AbstractProjectExt> getBuildTriggerUpstreamProjects() {
        ArrayList<AbstractProjectExt> result = new ArrayList<AbstractProjectExt>();
        for (AbstractProjectExt<?,?> ap : getUpstreamProjects()) {
            BuildTrigger buildTrigger = ap.getPublishersList().get(BuildTrigger.class);
            if (buildTrigger != null)
                if (buildTrigger.getChildProjects().contains(this))
                    result.add(ap);
        }        
        return result;
    }    
    
    /**
     * Gets all the upstream projects including transitive upstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProjectExt> getTransitiveUpstreamProjects() {
        return HudsonExt.getInstance().getDependencyGraph().getTransitiveUpstream(this);
    }

    /**
     * Gets all the downstream projects including transitive downstream projects.
     *
     * @since 1.138
     */
    public final Set<AbstractProjectExt> getTransitiveDownstreamProjects() {
        return HudsonExt.getInstance().getDependencyGraph().getTransitiveDownstream(this);
    }

    /**
     * Gets the dependency relationship map between this project (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      can be empty but not null. build number of this project to the build
     *      numbers of that project.
     */
    public SortedMap<Integer, RangeSet> getRelationship(AbstractProjectExt that) {
        TreeMap<Integer,RangeSet> r = new TreeMap<Integer,RangeSet>(REVERSE_INTEGER_COMPARATOR);

        checkAndRecord(that, r, this.getBuilds());
        // checkAndRecord(that, r, that.getBuilds());

        return r;
    }

    /**
     * Helper method for getDownstreamRelationship.
     *
     * For each given build, find the build number range of the given project and put that into the map.
     */
    private void checkAndRecord(AbstractProjectExt that, TreeMap<Integer, RangeSet> r, Collection<R> builds) {
        for (R build : builds) {
            RangeSet rs = build.getDownstreamRelationship(that);
            if(rs==null || rs.isEmpty())
                continue;

            int n = build.getNumber();

            RangeSet value = r.get(n);
            if(value==null)
                r.put(n,rs);
            else
                value.add(rs);
        }
    }

    /**
     * Builds the dependency graph.
     * @see DependencyGraph
     */
    protected abstract void buildDependencyGraph(DependencyGraph graph);

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = super.makeSearchIndex();
        if(isBuildable() && hasPermission(HudsonExt.ADMINISTER))
            sib.add("build","build");
        return sib;
    }

    public boolean isParameterized() {
        return getProperty(ParametersDefinitionProperty.class) != null;
    }

    public boolean cleanWorkspace() throws IOException, InterruptedException{
        checkPermission(BUILD);
        R b = getSomeBuildWithWorkspace();
        FilePathExt ws = b != null ? b.getWorkspace() : null;
        if (ws != null && getScm().processWorkspaceBeforeDeletion(this, ws, b.getBuiltOn())) {
            ws.deleteRecursive();
            return true;
        } else{
            // If we get here, that means the SCM blocked the workspace deletion.
            return false;
        }
    }

     

    /**
     * {@link AbstractProjectExt} subtypes should implement this base class as a descriptor.
     *
     * @since 1.294
     */
    public static abstract class AbstractProjectDescriptorExt extends TopLevelItemDescriptor {
        /**
         * {@link AbstractProjectExt} subtypes can override this method to veto some {@link DescriptorExt}s
         * from showing up on their configuration screen. This is often useful when you are building
         * a workflow/company specific project type, where you want to limit the number of choices
         * given to the users.
         *
         * <p>
         * Some {@link DescriptorExt}s define their own schemes for controlling applicability
         * (such as {@link BuildStepDescriptor#isApplicable(Class)}),
         * This method works like AND in conjunction with them;
         * Both this method and that method need to return true in order for a given {@link DescriptorExt}
         * to show up for the given {@link Project}.
         *
         * <p>
         * The default implementation returns true for everything.
         *
         * @see BuildStepDescriptor#isApplicable(Class) 
         * @see BuildWrapperDescriptor#isApplicable(AbstractProjectExt) 
         * @see TriggerDescriptor#isApplicable(ItemExt)
         */
        @Override
        public boolean isApplicable(DescriptorExt descriptor) {
            return true;
        }

         
        /**
        * Utility class for taking the current input value and computing a list
        * of potential terms to match against the list of defined labels.
         */
        static class AutoCompleteSeeder {
            private String source;
            private Pattern quoteMatcher = Pattern.compile("(\\\"?)(.+?)(\\\"?+)(\\s*)");

            AutoCompleteSeeder(String source) {
                this.source = source;
            }

            List<String> getSeeds() {
                ArrayList<String> terms = new ArrayList();
                boolean trailingQuote = source.endsWith("\"");
                boolean leadingQuote = source.startsWith("\"");
                boolean trailingSpace = source.endsWith(" ");

                if (trailingQuote || (trailingSpace && !leadingQuote)) {
                    terms.add("");
                } else {
                    if (leadingQuote) {
                        int quote = source.lastIndexOf('"');
                        if (quote == 0) {
                            terms.add(source.substring(1));
                        } else {
                            terms.add("");
                        }
                    } else {
                        int space = source.lastIndexOf(' ');
                        if (space > -1) {
                            terms.add(source.substring(space+1));
                        } else {
                            terms.add(source);
                        }
                    }
                }

                return terms;
            }
        }
    }

    /**
     * Finds a {@link AbstractProjectExt} that has the name closest to the given name.
     */
    public static AbstractProjectExt findNearest(String name) {
        List<AbstractProjectExt> projects = HudsonExt.getInstance().getItems(AbstractProjectExt.class);
        String[] names = new String[projects.size()];
        for( int i=0; i<projects.size(); i++ )
            names[i] = projects.get(i).getName();

        String nearest = EditDistance.findNearest(name, names);
        return (AbstractProjectExt)HudsonExt.getInstance().getItem(nearest);
    }

    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
            return o2-o1;
        }
    };

    private static final Logger LOGGER = Logger.getLogger(AbstractProjectExt.class.getName());

    /**
     * Permission to abort a build. For now, let's make it the same as {@link #BUILD}
     */
    public static final Permission ABORT = BUILD;
}

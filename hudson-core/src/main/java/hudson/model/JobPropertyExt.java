/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.PluginExt;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Extensible property of {@link JobExt}.
 *
 * <p>
 * {@link PluginExt}s can extend this to define custom properties
 * for {@link JobExt}s. {@link JobPropertyExt}s show up in the user
 * configuration screen, and they are persisted with the job object.
 *
 * <p>
 * Configuration screen should be defined in <tt>config.jelly</tt>.
 * Within this page, the {@link JobPropertyExt} instance is available
 * as <tt>instance</tt> variable (while <tt>it</tt> refers to {@link JobExt}.
 *
 * <p>
 * Starting 1.150, {@link JobPropertyExt} implements {@link BuildStep},
 * meaning it gets the same hook as {@link Publisher} and {@link Builder}.
 * The primary intention of this mechanism is so that {@link JobPropertyExt}s
 * can add actions to the new build. The {@link #perform(AbstractBuildExt, Launcher, BuildListener)}
 * and {@link #prebuild(AbstractBuildExt, BuildListener)} are invoked after those
 * of {@link Publisher}s.
 *
 * @param <J>
 *      When you restrict your job property to be only applicable to a certain
 *      subtype of {@link JobExt}, you can use this type parameter to improve
 *      the type signature of this class. See {@link JobPropertyDescriptorExt#isApplicable(Class)}. 
 *
 * @author Kohsuke Kawaguchi
 * @see JobPropertyDescriptorExt
 * @since 1.72
 */
public abstract class JobPropertyExt<J extends JobExt<?,?>> implements Describable<JobPropertyExt<?>>, BuildStep, ExtensionPoint {
    /**
     * The {@link JobExt} object that owns this property.
     * This value will be set by the HudsonExt code.
     * Derived classes can expect this value to be always set.
     */
    protected transient J owner;

    /**
     * Hook for performing post-initialization action.
     *
     * <p>
     * This method is invoked in two cases. One is when the {@link JobExt} that owns
     * this property is loaded from disk, and the other is when a job is re-configured
     * and all the {@link JobPropertyExt} instances got re-created.
     */
    protected void setOwner(J owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    public JobPropertyDescriptorExt getDescriptor() {
        return (JobPropertyDescriptorExt)HudsonExt.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * @deprecated
     *      as of 1.341. Override {@link #getJobActions(JobExt)} instead.
     */
    public Action getJobAction(J job) {
        return null;
    }

    /**
     * {@link Action}s to be displayed in the job page.
     *
     * <p>
     * Returning actions from this method allows a job property to add them
     * to the left navigation bar in the job page.
     *
     * <p>
     * {@link Action} can implement additional marker interface to integrate
     * with the UI in different ways.
     *
     * @param job
     *      Always the same as {@link #owner} but passed in anyway for backward compatibility (I guess.)
     *      You really need not use this value at all.
     * @return
     *      can be empty but never null.
     * @since 1.341
     * @see ProminentProjectAction
     * @see PermalinkProjectAction
     */
    public Collection<? extends Action> getJobActions(J job) {
        // delegate to getJobAction (singular) for backward compatible behavior
        Action a = getJobAction(job);
        if (a==null)    return Collections.emptyList();
        return Collections.singletonList(a);
    }

//
// default no-op implementation
//

    public boolean prebuild(AbstractBuildExt<?,?> build, BuildListener listener) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Invoked after {@link Publisher}s have run.
     */
    public boolean perform(AbstractBuildExt<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Returns {@link BuildStepMonitor#NONE} by default, as {@link JobPropertyExt}s normally don't depend
     * on its previous result.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public final Action getProjectAction(AbstractProjectExt<?,?> project) {
        return getJobAction((J)project);
    }

    public final Collection<? extends Action> getProjectActions(AbstractProjectExt<?,?> project) {
        return getJobActions((J)project);
    }

    public Collection<?> getJobOverrides() {
        return Collections.emptyList();
    }

    /**
     * Contributes {@link SubTask}s to {@link AbstractProjectExt#getSubTasks()}
     *
     * @since 1.377
     */
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.emptyList();
    }
}

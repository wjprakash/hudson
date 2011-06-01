/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Seiji Sogabe, Stephen Connolly
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
import hudson.FilePathExt;
import hudson.FileSystemProvisioner;
import hudson.Launcher;
import hudson.model.QueueExt.Task;
import hudson.model.labels.LabelAtomExt;
import hudson.model.queue.CauseOfBlockage;
import hudson.node_monitors.NodeMonitorExt;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.NodeDescriptorExt;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.EnumConverter;
import hudson.util.TagCloud;
import hudson.util.TagCloud.WeightFunction;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;


/**
 * Base type of HudsonExt slaves (although in practice, you probably extend {@link Slave} to define a new slave type.)
 *
 * <p>
 * As a special case, {@link HudsonExt} extends from here.
 *
 * @author Kohsuke Kawaguchi
 * @see NodeMonitor
 * @see NodeDescriptorExt
 */
public abstract class NodeExt extends AbstractModelObjectExt implements Describable<NodeExt>, ExtensionPoint, AccessControlled {
    /**
     * Newly copied slaves get this flag set, so that HudsonExt doesn't try to start this node until its configuration
     * is saved once.
     */
    protected volatile transient boolean holdOffLaunchUntilSave;

    public String getDisplayName() {
        return getNodeName(); // default implementation
    }

    public String getSearchUrl() {
        return "computer/"+getNodeName();
    }

    public boolean isHoldOffLaunchUntilSave() {
        return holdOffLaunchUntilSave;
    }

    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    public abstract String getNodeName();

    /**
     * When the user clones a {@link NodeExt}, HudsonExt uses this method to change the node name right after
     * the cloned {@link NodeExt} object is instantiated.
     *
     * <p>
     * This method is never used for any other purpose, and as such for all practical intents and purposes,
     * the node name should be treated like immutable.
     *
     * @deprecated to indicate that this method isn't really meant to be called by random code.
     */
    public abstract void setNodeName(String name);

    /**
     * Human-readable description of this node.
     */
    public abstract String getNodeDescription();

    /**
     * Returns a {@link Launcher} for executing programs on this node.
     *
     * <p>
     * The callee must call {@link Launcher#decorateFor(NodeExt)} before returning to complete the decoration. 
     */
    public abstract Launcher createLauncher(TaskListener listener);

    /**
     * Returns the number of {@link Executor}s.
     *
     * This may be different from <code>getExecutors().size()</code>
     * because it takes time to adjust the number of executors.
     */
    public abstract int getNumExecutors();

    /**
     * Returns {@link Mode#EXCLUSIVE} if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    public abstract ModeExt getMode();

    /**
     * Gets the corresponding {@link ComputerExt} object.
     *
     * @return
     *      this method can return null if there's no {@link ComputerExt} object for this node,
     *      such as when this node has no executors at all.
     */
    public final ComputerExt toComputer() {
        return HudsonExt.getInstance().getComputer(this);
    }

    /**
     * Gets the current channel, if the node is connected and online, or null.
     *
     * This is just a convenience method for {@link ComputerExt#getChannel()} with null check. 
     */
    public final VirtualChannel getChannel() {
        ComputerExt c = toComputer();
        return c==null ? null : c.getChannel();
    }

    /**
     * Creates a new {@link ComputerExt} object that acts as the UI peer of this {@link NodeExt}.
     * Nobody but {@link HudsonExt#updateComputerList()} should call this method.
     */
    protected abstract ComputerExt createComputer();

    /**
     * Return the possibly empty tag cloud for the labels of this node.
     */
    public TagCloud<LabelAtomExt> getLabelCloud() {
        return new TagCloud<LabelAtomExt>(getAssignedLabels(),new WeightFunction<LabelAtomExt>() {
            public float weight(LabelAtomExt item) {
                return item.getTiedJobs().size();
            }
        });
    }
    /**
     * Returns the possibly empty set of labels that are assigned to this node,
     * including the automatic {@link #getSelfLabel() self label}, manually
     * assigned labels and dynamically assigned labels via the
     * {@link LabelFinder} extension point.
     *
     * This method has a side effect of updating the hudson-wide set of labels
     * and should be called after events that will change that - e.g. a slave
     * connecting.
     */
    public Set<LabelAtomExt> getAssignedLabels() {
        Set<LabelAtomExt> r = LabelExt.parse(getLabelString());
        r.add(getSelfLabel());
        r.addAll(getDynamicLabels());
        return Collections.unmodifiableSet(r);
    }

    /**
     * Return all the labels assigned dynamically to this node.
     * This calls all the LabelFinder implementations with the node converts
     * the results into Labels.
     * @return HashSet<Label>.
     */
    private HashSet<LabelAtomExt> getDynamicLabels() {
        HashSet<LabelAtomExt> result = new HashSet<LabelAtomExt>();
        for (LabelFinder labeler : LabelFinder.all()) {
            // Filter out any bad(null) results from plugins
            // for compatibility reasons, findLabels may return LabelExpression and not atom.
            for (LabelExt label : labeler.findLabels(this))
                if (label instanceof LabelAtomExt) result.add((LabelAtomExt)label);
        }
        return result;
    }


    /**
     * Returns the manually configured label for a node. The list of assigned
     * and dynamically determined labels is available via 
     * {@link #getAssignedLabels()} and includes all labels that have been
     * manually configured.
     * 
     * Mainly for form binding.
     */
    public abstract String getLabelString();

    /**
     * Gets the special label that represents this node itself.
     */
    @WithBridgeMethods(LabelExt.class)
    public LabelAtomExt getSelfLabel() {
        return LabelAtomExt.get(getNodeName());
    }

    /**
     * Called by the {@link Queue} to determine whether or not this node can
     * take the given task. The default checks include whether or not this node
     * is part of the task's assigned label, whether this node is in
     * {@link Mode#EXCLUSIVE} mode if it is not in the task's assigned label,
     * and whether or not any of this node's {@link NodeProperty}s say that the
     * task cannot be run.
     *
     * @since 1.360
     */
    public CauseOfBlockage canTake(Task task) {
        LabelExt l = task.getAssignedLabel();
        if(l!=null && !l.contains(this))
            return CauseOfBlockage.fromMessage(Messages._Node_LabelMissing(getNodeName(),l));   // the task needs to be executed on label that this node doesn't have.

        if(l==null && getMode()== ModeExt.EXCLUSIVE)
            return CauseOfBlockage.fromMessage(Messages._Node_BecauseNodeIsReserved(getNodeName()));   // this node is reserved for tasks that are tied to it

        // Check each NodeProperty to see whether they object to this node
        // taking the task
        for (NodeProperty prop: getNodeProperties()) {
            CauseOfBlockage c = prop.canTake(task);
            if (c!=null)    return c;
        }

        // Looks like we can take the task
        return null;
    }

    /**
     * Returns a "workspace" directory for the given {@link TopLevelItem}.
     *
     * <p>
     * Workspace directory is usually used for keeping out the checked out
     * source code, but it can be used for anything.
     *
     * @return
     *      null if this node is not connected hence the path is not available
     */
    // TODO: should this be modified now that getWorkspace is moved from AbstractProject to AbstractBuild?
    public abstract FilePathExt getWorkspaceFor(TopLevelItem item);

    /**
     * Gets the root directory of this node.
     *
     * <p>
     * HudsonExt always owns a directory on every node. This method
     * returns that.
     *
     * @return
     *      null if the node is offline and hence the {@link FilePathExt}
     *      object is not available.
     */
    public abstract FilePathExt getRootPath();

    /**
     * Gets the {@link FilePathExt} on this node.
     */
    public FilePathExt createPath(String absolutePath) {
        VirtualChannel ch = getChannel();
        if(ch==null)    return null;    // offline
        return new FilePathExt(ch,absolutePath);
    }

    public FileSystemProvisioner getFileSystemProvisioner() {
        // TODO: make this configurable or auto-detectable or something else
        return FileSystemProvisioner.DEFAULT;
    }

    /**
     * Gets the {@link NodeProperty} instances configured for this {@link NodeExt}.
     */
    public abstract DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties();

    // used in the Jelly script to expose descriptors
    public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
        return NodeProperty.for_(this);
    }
    
    public ACL getACL() {
        return HudsonExt.getInstance().getAuthorizationStrategy().getACL(this);
    }
    
    public final void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public final boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    public abstract NodeDescriptorExt getDescriptor();

    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      always non-null.
     * @throws InterruptedException
     *      if the operation is aborted.
     */
    public abstract ClockDifference getClockDifference() throws IOException, InterruptedException;

    /**
     * Constants that control how HudsonExt allocates jobs to slaves.
     */
    public enum ModeExt {
        NORMAL(Messages.Node_Mode_NORMAL()),
        EXCLUSIVE(Messages.Node_Mode_EXCLUSIVE());

        private final String description;

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name();
        }

        ModeExt(String description) {
            this.description = description;
        }

        static {
            //Stapler.CONVERT_UTILS.register(new EnumConverter(), Mode.class);
        }
    }

}

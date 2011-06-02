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

import hudson.model.labels.LabelAtomExt;
import hudson.node_monitors.NodeMonitorExt;
import hudson.slaves.NodeDescriptorExt;
import hudson.util.ClockDifferenceExt;
import hudson.util.EnumConverter;

import java.io.IOException;
import java.util.Set;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

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
@ExportedBean
public abstract class Node extends NodeExt{
     
    /**
     * Name of this node.
     *
     * @return
     *      "" if this is master
     */
    @Exported(visibility=999)
    @Override
    public abstract String getNodeName();


    /**
     * Human-readable description of this node.
     */
    @Exported
    @Override
    public abstract String getNodeDescription();

    /**
     * Returns the number of {@link Executor}s.
     *
     * This may be different from <code>getExecutors().size()</code>
     * because it takes time to adjust the number of executors.
     */
    @Exported
    @Override
    public abstract int getNumExecutors();

    /**
     * Returns {@link Mode#EXCLUSIVE} if this node is only available
     * for those jobs that exclusively specifies this node
     * as the assigned node.
     */
    @Exported
    @Override
    public abstract ModeExt getMode();

    
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
    @Exported
    @Override
    public Set<LabelAtomExt> getAssignedLabels() {
         
        return super.getAssignedLabels();
    }
    
    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      always non-null.
     * @throws InterruptedException
     *      if the operation is aborted.
     */
    public abstract ClockDifferenceExt getClockDifference() throws IOException, InterruptedException;

    /**
     * Constants that control how HudsonExt allocates jobs to slaves.
     */
    public enum Mode {
        NORMAL(Messages.Node_Mode_NORMAL()),
        EXCLUSIVE(Messages.Node_Mode_EXCLUSIVE());

        private final String description;

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name();
        }

        Mode(String description) {
            this.description = description;
        }

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), Mode.class);
        }
    }

}

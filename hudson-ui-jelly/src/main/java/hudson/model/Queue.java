/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Stephen Connolly, Tom Huybrechts, InfraDNA, Inc.
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

import hudson.UtilExt;

import hudson.model.queue.FutureImpl;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsBusy;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsBusy;
import hudson.util.TimeUnit2;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;


/**
 * Build queue.
 *
 * <p>
 * This class implements the core scheduling logic. {@link Task} represents the executable
 * task that are placed in the queue. While in the queue, it's wrapped into {@link ItemExt}
 * so that we can keep track of additional data used for deciding what to exeucte when.
 *
 * <p>
 * Items in queue goes through several stages, as depicted below:
 * <pre>
 * (enter) --> waitingList --+--> blockedProjects
 *                           |        ^
 *                           |        |
 *                           |        v
 *                           +--> buildables ---> pending ---> (executed)
 * </pre>
 *
 * <p>
 * In addition, at any stage, an item can be removed from the queue (for example, when the user
 * cancels a job in the queue.) See the corresponding field for their exact meanings.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Queue extends QueueExt {

    public Queue(LoadBalancer loadBalancer) {
        super(loadBalancer);
    }

    /**
     * Gets a snapshot of items in the queue.
     *
     * Generally speaking the array is sorted such that the items that are most likely built sooner are
     * at the end.
     */
    @Exported(inline = true)
    public synchronized QueueExt.Item[] getItems() {
        return super.getItems();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * ItemExt in a queue.
     */
    @ExportedBean(defaultVisibility = 999)
    public static abstract class Item extends QueueExt.Item {

        @Exported
        public final Task task = null;

        ;
        
        protected Item(Task task, List<Action> actions, int id, FutureImpl future) {
            super(task, actions, id, future);
        }

        protected Item(Item item) {
            super(item);
        }

        /**
         * Build is blocked because another build is in progress,
         * required {@link Resource}s are not available, or otherwise blocked
         * by {@link Task#isBuildBlocked()}.
         */
        @Exported
        public boolean isBlocked() {
            return super.isBlocked();
        }

        /**
         * Build is waiting the executor to become available.
         * This flag is only used in {@link Queue#getItems()} for
         * 'pseudo' items that are actually not really in the queue.
         */
        @Exported
        public boolean isBuildable() {
            return super.isBuildable();
        }

        /**
         * True if the item is starving for an executor for too long.
         */
        @Exported
        public boolean isStuck() {
            return super.isStuck();
        }

        /**
         * Gets a human-readable status message describing why it's in the queue.
         */
        @Exported
        public final String getWhy() {
            return super.getWhy();
        }

        @Exported
        public String getParams() {
            return super.getParams();
        }

        /**
         * Called from queue.jelly.
         */
        public HttpResponse doCancelQueue() throws IOException, ServletException {
            HudsonExt.getInstance().getQueue().cancel(this);
            return HttpResponses.forwardToPreviousPage();
        }
    }

    /**
     * {@link ItemExt} in the {@link Queue#waitingList} stage.
     */
    public static final class WaitingItem extends Item implements Comparable<WaitingItem> {

        private static final AtomicInteger COUNTER = new AtomicInteger(0);
        /**
         * This item can be run after this time.
         */
        @Exported
        public Calendar timestamp;

        public WaitingItem(Calendar timestamp, Task project, List<Action> actions) {
            super(project, actions, COUNTER.incrementAndGet(), new FutureImpl(project));
            this.timestamp = timestamp;
        }

        public int compareTo(WaitingItem that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            if (r != 0) {
                return r;
            }

            return this.id - that.id;
        }

        public CauseOfBlockage getCauseOfBlockage() {
            long diff = timestamp.getTimeInMillis() - System.currentTimeMillis();
            if (diff > 0) {
                return CauseOfBlockage.fromMessage(Messages._Queue_InQuietPeriod(UtilExt.getTimeSpanString(diff)));
            } else {
                return CauseOfBlockage.fromMessage(Messages._Queue_Unknown());
            }
        }
    }

    /**
     * Common part between {@link BlockedItem} and {@link BuildableItem}.
     */
    public static abstract class NotWaitingItem extends Item {

        /**
         * When did this job exit the {@link Queue#waitingList} phase?
         */
        @Exported
        public final long buildableStartMilliseconds;

        protected NotWaitingItem(WaitingItem wi) {
            super(wi);
            buildableStartMilliseconds = System.currentTimeMillis();
        }

        protected NotWaitingItem(NotWaitingItem ni) {
            super(ni);
            buildableStartMilliseconds = ni.buildableStartMilliseconds;
        }
    }

    /**
     * {@link ItemExt} in the {@link Queue#blockedProjects} stage.
     */
    public final class BlockedItem extends NotWaitingItem {

        public BlockedItem(WaitingItem wi) {
            super(wi);
        }

        public BlockedItem(NotWaitingItem ni) {
            super(ni);
        }

        public CauseOfBlockage getCauseOfBlockage() {
            ResourceActivity r = getBlockingActivity(task);
            if (r != null) {
                if (r == task) // blocked by itself, meaning another build is in progress
                {
                    return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
                }
                return CauseOfBlockage.fromMessage(Messages._Queue_BlockedBy(r.getDisplayName()));
            }
            return task.getCauseOfBlockage();
        }
    }

    /**
     * {@link ItemExt} in the {@link Queue#buildables} stage.
     */
    public final static class BuildableItem extends NotWaitingItem {

        public BuildableItem(WaitingItem wi) {
            super(wi);
        }

        public BuildableItem(NotWaitingItem ni) {
            super(ni);
        }

        public CauseOfBlockage getCauseOfBlockage() {
            HudsonExt hudson = HudsonExt.getInstance();
            if (ifBlockedByHudsonShutdown(task)) {
                return CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown());
            }

            LabelExt label = task.getAssignedLabel();
            if (hudson.getNodes().isEmpty()) {
                label = null;    // no master/slave. pointless to talk about nodes
            }
            if (label != null) {
                if (label.isOffline()) {
                    Set<NodeExt> nodes = label.getNodes();
                    if (nodes.size() != 1) {
                        return new BecauseLabelIsOffline(label);
                    } else {
                        return new BecauseNodeIsOffline(nodes.iterator().next());
                    }
                }
            }

            if (label == null) {
                return CauseOfBlockage.fromMessage(Messages._Queue_WaitingForNextAvailableExecutor());
            }

            Set<NodeExt> nodes = label.getNodes();
            if (nodes.size() != 1) {
                return new BecauseLabelIsBusy(label);
            } else {
                return new BecauseNodeIsBusy(nodes.iterator().next());
            }
        }

        @Override
        public boolean isStuck() {
            LabelExt label = task.getAssignedLabel();
            if (label != null && label.isOffline()) // no executor online to process this job. definitely stuck.
            {
                return true;
            }

            long d = task.getEstimatedDuration();
            long elapsed = System.currentTimeMillis() - buildableStartMilliseconds;
            if (d >= 0) {
                // if we were running elsewhere, we would have done this build ten times.
                return elapsed > Math.max(d, 60000L) * 10;
            } else {
                // more than a day in the queue
                return TimeUnit2.MILLISECONDS.toHours(elapsed) > 24;
            }
        }
    }
}

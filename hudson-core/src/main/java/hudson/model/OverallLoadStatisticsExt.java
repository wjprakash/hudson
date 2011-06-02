/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Seiji Sogabe, Winston Prakash
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

import hudson.util.ColorPalette;
import hudson.util.graph.MultiStageTimeSeriesExt;
import hudson.util.graph.MultiStageTimeSeriesExt.TimeScale;
import hudson.util.graph.MultiStageTimeSeriesExt.TrendChartExt;

/**
 * {@link LoadStatistics} for the entire system (the master and all the slaves combined.)
 *
 * <p>
 * {@link #computeQueueLength()} and {@link #queueLength} counts those tasks
 * that are unassigned to any node, whereas {@link #totalQueueLength}
 * tracks the queue length including tasks that are assigned to a specific node.
 *
 * @author Kohsuke Kawaguchi
 * @see HudsonExt#overallLoad
 */
public class OverallLoadStatisticsExt extends LoadStatisticsExt {

    /**
     * Number of total {@link Queue.BuildableItem}s that represents blocked builds.
     */
    public final MultiStageTimeSeriesExt totalQueueLength = new MultiStageTimeSeriesExt(
            Messages._LoadStatistics_Legends_QueueLength(), ColorPalette.GREY, 0, DECAY);

    public OverallLoadStatisticsExt() {
        super(0, 0);
    }

    @Override
    public int computeIdleExecutors() {
        return new ComputerSetExt().getIdleExecutors();
    }

    @Override
    public int computeTotalExecutors() {
        return new ComputerSetExt().getTotalExecutors();
    }

    @Override
    public int computeQueueLength() {
        return HudsonExt.getInstance().getQueue().countBuildableItemsFor(null);
    }

    /**
     * When drawing the overall load statistics, use the total queue length,
     * not {@link #queueLength}, which just shows jobs that are to be run on the master. 
     */
    protected TrendChartExt createOverallTrendChart(TimeScale timeScale) {
        return MultiStageTimeSeriesExt.createTrendChart(timeScale, busyExecutors, totalExecutors, totalQueueLength);
    }
}

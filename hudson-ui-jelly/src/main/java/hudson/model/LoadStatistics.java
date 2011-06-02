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

import hudson.Extension;
import hudson.util.ColorPalette;
import hudson.util.graph.MultiStageTimeSeriesExt;
import hudson.util.graph.MultiStageTimeSeriesExt.TimeScale;
import hudson.util.graph.MultiStageTimeSeriesExt.TrendChartExt;
import java.awt.Color;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.List;

/**
 * Utilization statistics for a node or a set of nodes.
 *
 * <h2>Implementation Note</h2>
 * <p>
 * Instances of this class is not capable of updating the statistics itself
 * &mdash; instead, it's done by the {@link LoadStatisticsUpdater} timer.
 * This is more efficient (as it allows us a single pass to update all stats),
 * but it's not clear to me if the loss of autonomy is worth it.
 *
 * @author Kohsuke Kawaguchi
 * @see LabelExt#loadStatistics
 * @see HudsonExt#overallLoad
 */
@ExportedBean
public abstract class LoadStatistics extends LoadStatisticsExt{
    /**
     * Number of busy executors and how it changes over time.
     */
    @Exported
    public final MultiStageTimeSeriesExt busyExecutors = null;

    /**
     * Number of total executors and how it changes over time.
     */
    @Exported
    public final MultiStageTimeSeriesExt totalExecutors = null;

    /**
     * Number of {@link Queue.BuildableItem}s that can run on any node in this node set but blocked.
     */
    @Exported
    public final MultiStageTimeSeriesExt queueLength = null;

    protected LoadStatistics(int initialTotalExecutors, int initialBusyExecutors) {
         super(initialTotalExecutors, initialBusyExecutors);
    }

    public float getLatestIdleExecutors(TimeScale timeScale) {
        return totalExecutors.pick(timeScale).getLatest() - busyExecutors.pick(timeScale).getLatest();
    }

    /**
     * Generates the load statistics graph.
     */
    public TrendChartExt doGraph(@QueryParameter String type) throws IOException {
        return createTrendChart(TimeScale.parse(type));
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * With 0.90 decay ratio for every 10sec, half reduction is about 1 min.
     */
    public static final float DECAY = Float.parseFloat(System.getProperty(LoadStatistics.class.getName()+".decay","0.9"));
    /**
     * Load statistics clock cycle in milliseconds. Specify a small value for quickly debugging this feature and node provisioning through cloud.
     */
    public static int CLOCK = Integer.getInteger(LoadStatistics.class.getName()+".clock",10*1000);

    /**
     * Periodically update the load statistics average.
     */
    @Extension
    public static class LoadStatisticsUpdater extends LoadStatisticsExt.LoadStatisticsUpdater {
        
    }
}

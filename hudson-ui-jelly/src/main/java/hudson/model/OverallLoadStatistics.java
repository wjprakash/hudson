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

import hudson.util.graph.MultiStageTimeSeries;

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
public class OverallLoadStatistics extends OverallLoadStatisticsExt {
     
    public MultiStageTimeSeries getTotalQueueLength(){
        return totalQueueLength;
    }

}

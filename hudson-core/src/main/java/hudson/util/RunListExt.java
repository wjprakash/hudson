/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import hudson.model.AbstractBuildExt;
import hudson.model.JobExt;
import hudson.model.NodeExt;
import hudson.model.ResultExt;
import hudson.model.RunExt;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * {@link List} of {@link RunExt}s, sorted in the descending date order.
 *
 * TODO: this should be immutable
 *
 * @author Kohsuke Kawaguchi
 */
public class RunListExt<R extends RunExt> extends ArrayList<R> {
    public RunListExt() {
    }

    public RunListExt(JobExt j) {
        addAll(j.getBuilds());
    }

    public RunListExt(Collection<? extends JobExt> jobs) {
        for (JobExt j : jobs)
            addAll(j.getBuilds());
        Collections.sort(this,RunExt.ORDER_BY_DATE);
    }

    protected RunListExt(Collection<? extends R> c, boolean hack) {
        super(c);
    }
    
     public R getFirstBuild() {
        return isEmpty() ? null : get(size()-1);
    }

    public R getLastBuild() {
        return isEmpty() ? null : get(0);
    }
    
    public static <R extends RunExt>
    RunListExt<R> fromRuns(Collection<? extends R> runs) {
        return new RunListExt<R>(runs,false);
    }

    /**
     * Filter the list to non-successful builds only.
     */
    public RunListExt<R> failureOnly() {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            RunExt r = itr.next();
            if(r.getResult()==ResultExt.SUCCESS)
                itr.remove();
        }
        return this;
    }

    /**
     * Filter the list to builds on a single node only
     */
    public RunListExt<R> node(NodeExt node) {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            RunExt r = itr.next();
            if (!(r instanceof AbstractBuildExt) || ((AbstractBuildExt)r).getBuiltOn()!=node) {
                itr.remove();
            }
        }
        return this;
    }

    /**
     * Filter the list to regression builds only.
     */
    public RunListExt<R> regressionOnly() {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            RunExt r = itr.next();
            if(!r.getBuildStatusSummary().isWorse)
                itr.remove();
        }
        return this;
    }

    /**
     * Filter the list by timestamp.
     *
     * {@code s&lt=;e}.
     */
    public RunListExt<R> byTimestamp(long start, long end) {
        AbstractList<Long> TIMESTAMP_ADAPTER = new AbstractList<Long>() {
            public Long get(int index) {
                return RunListExt.this.get(index).getTimeInMillis();
            }

            public int size() {
                return RunListExt.this.size();
            }
        };
        Comparator<Long> DESCENDING_ORDER = new Comparator<Long>() {
            public int compare(Long o1, Long o2) {
                if (o1 > o2) return -1;
                if (o1 < o2) return +1;
                return 0;
            }
        };

        int s = Collections.binarySearch(TIMESTAMP_ADAPTER, start, DESCENDING_ORDER);
        if (s<0)    s=-(s+1);   // min is inclusive
        int e = Collections.binarySearch(TIMESTAMP_ADAPTER, end,   DESCENDING_ORDER);
        if (e<0)    e=-(e+1);   else e++;   // max is exclusive, so the exact match should be excluded

        return fromRuns(subList(e,s));
    }

    /**
     * Reduce the size of the list by only leaving relatively new ones.
     * This also removes on-going builds, as RSS cannot be used to publish information
     * if it changes.
     */
    public RunListExt<R> newBuilds() {
        GregorianCalendar threshold = new GregorianCalendar();
        threshold.add(Calendar.DAY_OF_YEAR,-7);

        int count=0;

        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            R r = itr.next();
            if(r.isBuilding()) {
                // can't publish on-going builds
                itr.remove();
                continue;
            }
            // at least put 10 items
            if(count<10) {
                count++;
                continue;
            }
            // anything older than 7 days will be ignored
            if(r.getTimestamp().before(threshold))
                itr.remove();
        }
        return this;
    }
}

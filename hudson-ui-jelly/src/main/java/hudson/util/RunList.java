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

import hudson.model.ItemExt;
import hudson.model.JobExt;
import hudson.model.RunExt;
import hudson.model.View;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link List} of {@link RunExt}s, sorted in the descending date order.
 *
 * TODO: this should be immutable
 *
 * @author Kohsuke Kawaguchi
 */
public class RunList<R extends RunExt> extends RunListExt<R> {
    public RunList() {
    }

    public RunList(JobExt j) {
        super(j);
    }

    public RunList(View view) {// this is a type unsafe operation
        for (ItemExt item : view.getItems())
            for (JobExt<?,?> j : item.getAllJobs())
                addAll((Collection<R>)j.getBuilds());
        Collections.sort(this,RunExt.ORDER_BY_DATE);
    }

    public RunList(Collection<? extends JobExt> jobs) {
         super(jobs);
    }

    private RunList(Collection<? extends R> c, boolean hack) {
        super(c, hack);
    }

}

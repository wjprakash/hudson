/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Oracle Corporation, Tom Huybrechts, Yahoo!, Inc., Seiji Sogabe, Winston Prakash
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
package hudson.tasks.junit;

import hudson.tasks.test.TestObjectExt;
import hudson.util.graph.GraphExt;


import org.kohsuke.stapler.Stapler;

/**
 * History of {@link hudson.tasks.test.TestObject} over time.
 *
 * @since 1.320
 */
public class History extends HistoryExt {

    public History(TestObjectExt testObject) {
        super(testObject);
    }

    /**
     * Graph of duration of tests over time.
     */
    @Override
    public GraphExt getDurationGraph() {
        GraphExt graph = new GraphExt(-1, 600, 300);
        graph.setXAxisLabel("seconds");
        int start = Integer.parseInt(Stapler.getCurrentRequest().getParameter("start"));
        int end = Integer.parseInt(Stapler.getCurrentRequest().getParameter("end"));
        graph.setData(getDurationGraphDataSet(start, end));
        return graph;
    }

    /**
     * Graph of # of tests over time.
     */
    @Override
    public GraphExt getCountGraph() {
        GraphExt graph = new GraphExt(-1, 600, 300);
        graph.setXAxisLabel("");
        int start = Integer.parseInt(Stapler.getCurrentRequest().getParameter("start"));
        int end = Integer.parseInt(Stapler.getCurrentRequest().getParameter("end"));
        graph.setData(getCountGraphDataSet(start, end));
        return graph;
    }
}

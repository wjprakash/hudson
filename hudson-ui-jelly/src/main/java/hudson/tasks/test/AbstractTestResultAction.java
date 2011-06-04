/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Inc., Kohsuke Kawaguchi, Daniel Dyer, 
 * Red Hat, Inc., Stephen Connolly, id:cactusman, Yahoo!, Inc, Winston Prakash
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
package hudson.tasks.test;

import hudson.Functions;
import hudson.util.graph.DataSet;
import hudson.model.*;
import hudson.util.*;
import hudson.util.graph.ChartLabel;
import hudson.util.graph.ChartUtil;
import hudson.util.graph.ChartUtil.NumberOnlyBuildLabel;
import hudson.util.graph.GraphExt;
import java.awt.Color;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;

/**
 * Common base class for recording test result.
 *
 * <p>
 * {@link Project} and {@link Build} recognizes {@link Action}s that derive from this,
 * and displays it nicely (regardless of the underlying implementation.)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class AbstractTestResultAction<T extends AbstractTestResultAction> extends AbstractTestResultActionExt<T>{
     
    protected AbstractTestResultAction(AbstractBuildExt owner) {
        super(owner);
    }

    /**
     * Gets the number of failed tests.
     */
    @Exported(visibility=2)
    public abstract int getFailCount();

    /**
     * Gets the number of skipped tests.
     */
    @Exported(visibility=2)
    public int getSkipCount() {
         return super.getSkipCount();
    }

    /**
     * Gets the total number of tests.
     */
    @Exported(visibility=2)
    public abstract int getTotalCount();

     

    @Exported(visibility=2)
    public String getUrlName() {
        return super.getUrlName();
    }

    /**
     * Exposes this object to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    
    /**
     * Generates a PNG image for the test result trend.
     */
    public void doGraph( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(ChartUtil.awtProblemCause!=null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath()+"/images/headless.png");
            return;
        }

        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;
    
        Area defSize = calcDefaultSize();
        GraphExt graph = new GraphExt(-1, defSize.width, defSize.height);
        graph.setXAxisLabel("count");
        graph.setData(getGraphDataSet(req));
        graph.doPng(req,rsp);
        
        //ChartUtil.generateGraph(req,rsp,createChart(req,buildDataSet(req)),calcDefaultSize());
    }

    /**
     * Generates a clickable map HTML for {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doGraphMap( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.checkIfModified(owner.getTimestamp(),rsp))
            return;
        
        Area defSize = calcDefaultSize();
        GraphExt graph = new GraphExt(-1, defSize.width, defSize.height);
        graph.setXAxisLabel("count");
        graph.setData(getGraphDataSet(req));
        graph.doMap(req,rsp);
    }
    
    private DataSet getGraphDataSet(StaplerRequest req) {
        boolean failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));

        DataSet<String, ChartLabel> dsb = new DataSet<String, ChartLabel>();

        for( AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class) ) {
            dsb.add( a.getFailCount(), "failed", new TestResultChartLabel(req, a.owner));
            if(!failureOnly) {
                
                dsb.add( a.getSkipCount(), "skipped", new TestResultChartLabel(req, a.owner));
                dsb.add( a.getTotalCount()-a.getFailCount()-a.getSkipCount(),"total", new TestResultChartLabel(req, a.owner));
            }
        }
        return dsb;
    }
    
    private class TestResultChartLabel extends NumberOnlyBuildLabel{
        final String relPath;
        
        public TestResultChartLabel(StaplerRequest req, AbstractBuildExt build){
            super(build);
            relPath = getRelPath(req);
        }
        @Override
        public Color getColor(int row, int column) {
            return Color.BLUE;
        }

        @Override
        public String getLink(int row, int column) {
            return relPath + build.getNumber()+"/testReport/";
        }

        @Override
        public String getToolTip(int row, int column) {
             
                AbstractTestResultAction a = build.getAction(AbstractTestResultAction.class);
                switch (row) {
                    case 0:
                        return String.valueOf(Messages.AbstractTestResultAction_fail(build.getDisplayName(), a.getFailCount()));
                    case 1:
                        return String.valueOf(Messages.AbstractTestResultAction_skip(build.getDisplayName(), a.getSkipCount()));
                    default:
                        return String.valueOf(Messages.AbstractTestResultAction_test(build.getDisplayName(), a.getTotalCount()));
                }
        }

        public int compareTo(ChartLabel t) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }


    /**
     * Determines the default size of the trend graph.
     *
     * This is default because the query parameter can choose arbitrary size.
     * If the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if(res!=null && res.width<=800)
            return new Area(250,100);
        else
            return new Area(500,200);
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if(relPath==null)   return "";
        return relPath;
    }
    
     /**
     * Returns a full path down to a test result
     */
    public String getTestResultPath(TestResult it) {
        return getUrlName() + "/" + it.getRelativePathFrom(null);
    }
}

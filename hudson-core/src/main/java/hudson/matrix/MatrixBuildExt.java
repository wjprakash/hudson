/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Tom Huybrechts
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
package hudson.matrix;

import hudson.UtilExt;
import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.BuildListener;
import hudson.model.ExecutorExt;
import hudson.model.FingerprintExt;
import hudson.model.HudsonExt;
import hudson.model.JobPropertyExt;
import hudson.model.NodeExt;
import hudson.model.ParametersAction;
import hudson.model.QueueExt;
import hudson.model.ResultExt;
import hudson.model.CauseExt.UpstreamCause;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import hudson.tasks.Publisher;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Build of {@link MatrixProjectExt}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixBuildExt extends AbstractBuildExt<MatrixProjectExt,MatrixBuildExt> {
    private AxisList axes;

    public MatrixBuildExt(MatrixProjectExt job) throws IOException {
        super(job);
    }

    public MatrixBuildExt(MatrixProjectExt job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixBuildExt(MatrixProjectExt project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public Object readResolve() {
        // MatrixBuildExt.axes added in 1.285; default to parent axes for old data
        if (axes==null)
            axes = getParent().getAxes();
        return this;
    }

    /**
     * Used by view to render a ball for {@link MatrixRunExt}.
     */
    public final class RunPtr {
        public final Combination combination;
        private RunPtr(Combination c) { this.combination=c; }

        public MatrixRunExt getRun() {
            return MatrixBuildExt.this.getRun(combination);
        }

        public String getShortUrl() {
            return UtilExt.rawEncode(combination.toString());
        }

        public String getTooltip() {
            MatrixRunExt r = getRun();
            if(r!=null) return r.getIconColor().getDescription();
            QueueExt.Item item = HudsonExt.getInstance().getQueue().getItem(getParent().getItem(combination));
            if(item!=null)
                return item.getWhy();
            return null;    // fall back
        }
    }

    public Layouter<RunPtr> getLayouter() {
        // axes can be null if build page is access right when build starts
        return axes == null ? null : new Layouter<RunPtr>(axes) {
            protected RunPtr getT(Combination c) {
                return new RunPtr(c);
            }
        };
    }

    /**
     * Gets the {@link MatrixRunExt} in this build that corresponds
     * to the given combination.
     */
    public MatrixRunExt getRun(Combination c) {
        MatrixConfiguration config = getParent().getItem(c);
        if(config==null)    return null;
        return config.getBuildByNumber(getNumber());
    }

    /**
     * Returns all {@link MatrixRunExt}s for this {@link MatrixBuildExt}.
     */
    public List<MatrixRunExt> getRuns() {
        List<MatrixRunExt> r = new ArrayList<MatrixRunExt>();
        for(MatrixConfiguration c : getParent().getItems()) {
            MatrixRunExt b = c.getBuildByNumber(getNumber());
            if (b != null) r.add(b);
        }
        return r;
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    @Override
    public FingerprintExt.RangeSet getDownstreamRelationship(AbstractProjectExt that) {
        FingerprintExt.RangeSet rs = super.getDownstreamRelationship(that);
        for(MatrixRunExt run : getRuns())
            rs.add(run.getDownstreamRelationship(that));
        return rs;
    }

    private class RunnerImpl extends AbstractRunner {
        private final List<MatrixAggregator> aggregators = new ArrayList<MatrixAggregator>();

        protected ResultExt doRun(BuildListener listener) throws Exception {
            MatrixProjectExt p = getProject();
            PrintStream logger = listener.getLogger();

            // list up aggregators
            for (Publisher pub : p.getPublishers().values()) {
                if (pub instanceof MatrixAggregatable) {
                    MatrixAggregatable ma = (MatrixAggregatable) pub;
                    MatrixAggregator a = ma.createAggregator(MatrixBuildExt.this, launcher, listener);
                    if(a!=null)
                        aggregators.add(a);
                }
            }

            //let properties do their job
            for (JobPropertyExt prop : p.getProperties().values()) {
                if (prop instanceof MatrixAggregatable) {
                    MatrixAggregatable ma = (MatrixAggregatable) prop;
                    MatrixAggregator a = ma.createAggregator(MatrixBuildExt.this, launcher, listener);
                    if(a!=null)
                        aggregators.add(a);
                }
            }

            axes = p.getAxes();
            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();
            final int n = getNumber();
            
            String touchStoneFilter = p.getTouchStoneCombinationFilter();
            Collection<MatrixConfiguration> touchStoneConfigurations = new HashSet<MatrixConfiguration>();
            Collection<MatrixConfiguration> delayedConfigurations = new HashSet<MatrixConfiguration>();
            for (MatrixConfiguration c: activeConfigurations) {
                if (touchStoneFilter != null && c.getCombination().evalGroovyExpression(p.getAxes(), p.getTouchStoneCombinationFilter())) {
                    touchStoneConfigurations.add(c);
                } else {
                    delayedConfigurations.add(c);
                }
            }

            for (MatrixAggregator a : aggregators)
                if(!a.startBuild())
                    return ResultExt.FAILURE;

            try {
                if(!p.isRunSequentially())
                    for(MatrixConfiguration c : touchStoneConfigurations)
                        scheduleConfigurationBuild(logger, c);

                ResultExt r = ResultExt.SUCCESS;
                for (MatrixConfiguration c : touchStoneConfigurations) {
                    if(p.isRunSequentially())
                        scheduleConfigurationBuild(logger, c);
                    ResultExt buildResult = waitForCompletion(listener, c);
                    r = r.combine(buildResult);
                }
                
                if (p.getTouchStoneResultCondition() != null && r.isWorseThan(p.getTouchStoneResultCondition())) {
                    logger.printf("Touchstone configurations resulted in %s, so aborting...\n", r);
                    return r;
                }
                
                if(!p.isRunSequentially())
                    for(MatrixConfiguration c : delayedConfigurations)
                        scheduleConfigurationBuild(logger, c);

                for (MatrixConfiguration c : delayedConfigurations) {
                    if(p.isRunSequentially())
                        scheduleConfigurationBuild(logger, c);
                    ResultExt buildResult = waitForCompletion(listener, c);
                    r = r.combine(buildResult);
                }

                return r;
            } catch( InterruptedException e ) {
                logger.println("Aborted");
                return ResultExt.ABORTED;
            } catch (AggregatorFailureException e) {
                return ResultExt.FAILURE;
            }
            finally {
                // if the build was aborted in the middle. Cancel all the configuration builds.
                QueueExt q = HudsonExt.getInstance().getQueue();
                synchronized(q) {// avoid micro-locking in q.cancel.
                    for (MatrixConfiguration c : activeConfigurations) {
                        if(q.cancel(c))
                            logger.println(Messages.MatrixBuild_Cancelled(c.getDisplayName()));
                        MatrixRunExt b = c.getBuildByNumber(n);
                        if(b!=null) {
                            ExecutorExt exe = b.getExecutor();
                            if(exe!=null) {
                                logger.println(Messages.MatrixBuild_Interrupting(b.getDisplayName()));
                                exe.interrupt();
                            }
                        }
                    }
                }
            }
        }
        
        private ResultExt waitForCompletion(BuildListener listener, MatrixConfiguration c) throws InterruptedException, IOException, AggregatorFailureException {
            String whyInQueue = "";
            long startTime = System.currentTimeMillis();

            // wait for the completion
            int appearsCancelledCount = 0;
            while(true) {
                MatrixRunExt b = c.getBuildByNumber(getNumber());

                // two ways to get beyond this. one is that the build starts and gets done,
                // or the build gets cancelled before it even started.
                ResultExt buildResult = null;
                if(b!=null && !b.isBuilding())
                    buildResult = b.getResult();
                QueueExt.Item qi = c.getQueueItem();
                if(b==null && qi==null)
                    appearsCancelledCount++;
                else
                    appearsCancelledCount = 0;

                if(appearsCancelledCount>=5) {
                    // there's conceivably a race condition in computating b and qi, as their computation
                    // are not synchronized. There are indeed several reports of HudsonExt incorrectly assuming
                    // builds being cancelled. See
                    // http://www.nabble.com/Master-slave-problem-tt14710987.html and also
                    // http://www.nabble.com/Anyone-using-AccuRev-plugin--tt21634577.html#a21671389
                    // because of this, we really make sure that the build is cancelled by doing this 5
                    // times over 5 seconds
                    listener.getLogger().println(Messages.MatrixBuild_AppearsCancelled(c.getDisplayName()));
                    buildResult = ResultExt.ABORTED;
                }

                if(buildResult!=null) {
                    for (MatrixAggregator a : aggregators)
                        if(!a.endRun(b))
                            throw new AggregatorFailureException();
                    return buildResult;
                } 

                if(qi!=null) {
                    // if the build seems to be stuck in the queue, display why
                    String why = qi.getWhy();
                    if(!why.equals(whyInQueue) && System.currentTimeMillis()-startTime>5000) {
                        listener.getLogger().println(c.getDisplayName()+" is still in the queue: "+why);
                        whyInQueue = why;
                    }
                }
                
                Thread.sleep(1000);
            }
        }

        private void scheduleConfigurationBuild(PrintStream logger, MatrixConfiguration c) {
            logger.println(Messages.MatrixBuild_Triggering(c.getDisplayName()));
            c.scheduleBuild(getAction(ParametersAction.class), new UpstreamCause(MatrixBuildExt.this));
        }

        public void post2(BuildListener listener) throws Exception {
            for (MatrixAggregator a : aggregators)
                a.endBuild();
        }
        
        @Override
        protected Lease decideWorkspace(NodeExt n, WorkspaceList wsl) throws IOException, InterruptedException {
            String customWorkspace = getProject().getCustomWorkspace();
            if (customWorkspace != null) {
                // we allow custom workspaces to be concurrently used between jobs.
                return Lease.createDummyLease(n.getRootPath().child(getEnvironment(listener).expand(customWorkspace)));
            }
            return super.decideWorkspace(n,wsl);
        }
      
    }

    /**
     * A private exception to help maintain the correct control flow after extracting the 'waitForCompletion' method
     */
    private static class AggregatorFailureException extends Exception {}

}

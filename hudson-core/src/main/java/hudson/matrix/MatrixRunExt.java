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
package hudson.matrix;

import hudson.FilePathExt;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import static hudson.matrix.MatrixConfiguration.useShortWorkspaceName;
import hudson.model.Build;
import hudson.model.Node;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;

/**
 * Execution of {@link MatrixConfiguration}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixRunExt extends Build<MatrixConfiguration,MatrixRunExt> {
    public MatrixRunExt(MatrixConfiguration job) throws IOException {
        super(job);
    }

    public MatrixRunExt(MatrixConfiguration job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixRunExt(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Gets the {@link MatrixBuildExt} that has the same build number.
     *
     * @return
     *      null if no such build exists, which happens when the module build
     *      is manually triggered.
     */
    public MatrixBuildExt getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }


    @Override
    public Map<String,String> getBuildVariables() {
        Map<String,String> r = super.getBuildVariables();
        // pick up user axes
        AxisList axes = getParent().getParent().getAxes();
        for (Map.Entry<String,String> e : getParent().getCombination().entrySet()) {
            AxisExt a = axes.find(e.getKey());
            if (a!=null)
                a.addBuildVariable(e.getValue(),r);
            else
                r.put(e.getKey(), e.getValue());
        }
        return r;
    }

    /**
     * If the parent {@link MatrixBuildExt} is kept, keep this record too.
     */
    @Override
    public String getWhyKeepLog() {
        MatrixBuildExt pb = getParentBuild();
        if(pb!=null && pb.getWhyKeepLog()!=null)
            return Messages.MatrixRun_KeptBecauseOfParent(pb);
        return super.getWhyKeepLog();
    }

    @Override
    public MatrixConfiguration getParent() {// don't know why, but javac wants this
        return super.getParent();
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    protected class RunnerImpl extends Build<MatrixConfiguration,MatrixRunExt>.RunnerImpl {
        @Override
        protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            // Map current combination to a directory subtree, e.g. 'axis1=a,axis2=b' to 'axis1/a/axis2/b'.
            String subtree;
            if(useShortWorkspaceName) {
                subtree = getParent().getDigestName(); 
            } else {
                subtree = getParent().getCombination().toString('/','/');
            }
            
            String customWorkspace = getParent().getParent().getCustomWorkspace();
            if (customWorkspace != null) {
                // Use custom workspace as defined in the matrix project settings.
                FilePathExt ws = n.getRootPath().child(getEnvironment(listener).expand(customWorkspace));
                // We allow custom workspaces to be used concurrently between jobs.
                return Lease.createDummyLease(ws.child(subtree));
            } else {
                // Use default workspace as assigned by Hudson.
                Node node = getBuiltOn();
                FilePathExt ws = node.getWorkspaceFor(getParent().getParent());
                // Allocate unique workspace (not to be shared between jobs and runs).
                return wsl.allocate(ws.child(subtree));
            }
        }
    }
}

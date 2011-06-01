/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Red Hat, Inc., Stephen Connolly, Tom Huybrechts
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

import hudson.model.queue.Tasks;
import hudson.model.queue.WorkUnitExt;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;

import static hudson.model.queue.Executables.*;


/**
 * Thread that executes builds.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Executor extends ExecutorExt {
     
    public Executor(ComputerExt owner, int n) {
       super(owner, n);
    }

     

    /**
     * Returns the current {@link Queue.Task} this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @Exported
    @Override
    public QueueExt.Executable getCurrentExecutable() {
        return super.getCurrentExecutable();
    }

    /**
     * Returns the current {@link WorkUnit} (of {@link #getCurrentExecutable() the current executable})
     * that this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @Exported
    @Override
    public WorkUnitExt getCurrentWorkUnit() {
        return super.getCurrentWorkUnit();
    }

    /**
     * Gets the executor number that uniquely identifies it among
     * other {@link ExecutorExt}s for the same computer.
     *
     * @return
     *      a sequential number starting from 0.
     */
    @Exported
    @Override
    public int getNumber() {
        return super.getNumber();
    }

    /**
     * Returns true if this {@link ExecutorExt} is ready for action.
     */
    @Exported
    @Override
    public boolean isIdle() {
        return super.isIdle();
    }

    /**
     * Returns the progress of the current build in the number between 0-100.
     *
     * @return -1
     *      if it's impossible to estimate the progress.
     */
    @Exported
    @Override
    public int getProgress() {
        return super.getProgress();
    }

    /**
     * Returns true if the current build is likely stuck.
     *
     * <p>
     * This is a heuristics based approach, but if the build is suspiciously taking for a long time,
     * this method returns true.
     */
    @Exported
    @Override
    public boolean isLikelyStuck() {
         return super.isLikelyStuck();
    }

    /**
     * Stops the current build.
     */
    public void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        QueueExt.Executable e = executable;
        if(e!=null) {
            Tasks.getOwnerTaskOf(getParentOf(e)).checkAbortPermission();
            interrupt();
        }
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Throws away this executor and get a new one.
     */
    public HttpResponse doYank() {
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);
        if (isAlive())
            throw new FailureExt("Can't yank a live executor");
        owner.removeExecutor(this);
        return HttpResponses.redirectViaContextPath("/");
    }

    /**
     * Exposes the executor to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

}

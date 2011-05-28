/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc.
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

/**
 * {@link Thread} for performing one-off task.
 *
 * <p>
 * Designed to be used inside {@link TaskAction}.
 *
 * 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.191
 * @see TaskAction
 */
public abstract class TaskThreadExt extends Thread {
   

    private final TaskActionExt owner;

    private volatile boolean isRunning;
    
    /**
     * Represents the interface to produce output.
     */
    private TaskListener listener;

    /**
     *
     * @param output
     *      Determines where the output from this task thread goes.
     */
    protected TaskThreadExt(TaskActionExt owner, TaskListener listener) {
        //FIXME this failed to compile super(owner.getBuild().toString()+' '+owner.getDisplayName());
        //Please implement more general way how to get information about action owner, 
        //if you want it in the thread's name.
        super(owner.getDisplayName());
        this.owner = owner;
        this.listener = listener;
    }

   

    /**
     * Registers that this {@link TaskThread} is run for the specified
     * {@link TaskAction}. This can be explicitly called from subtypes
     * to associate a single {@link TaskThread} across multiple tag actions.
     */
    protected void associateWith(TaskActionExt action) {
        action.workerThread = this;
    }

    /**
     * Starts the task execution asynchronously.
     */
    @Override
    public void start() {
        associateWith(owner);
        super.start();
    }

    public boolean isRunning() {
        return isRunning;
    }


    @Override
    public void run() {
        isRunning = true;
        try {
            perform(listener);
            listener.getLogger().println("Completed");
            owner.workerThread = null;            
        } catch (InterruptedException e) {
            listener.getLogger().println("Aborted");
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        } finally {
            listener = null;
            isRunning =false;
        }
    }

    /**
     * Do the actual work.
     *
     * @throws Exception
     *      The exception is recorded and reported as a failure.
     */
    protected abstract void perform(TaskListener listener) throws Exception;

}

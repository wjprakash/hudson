/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

import hudson.model.ExecutorExt;
import hudson.model.QueueExt.Executable;
import hudson.model.QueueExt.Task;
import java.util.Queue;

/**
 * Represents a unit of hand-over to {@link ExecutorExt} from {@link Queue}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.377
 */
public class WorkUnitExt {

    /**
     * Task to be executed.
     */
    public final SubTask work;
    /**
     * Shared context among {@link WorkUnit}s.
     */
    public final WorkUnitContext context;
    private volatile ExecutorExt executor;

    WorkUnitExt(WorkUnitContext context, SubTask work) {
        this.context = context;
        this.work = work;
    }

    /**
     * {@link ExecutorExt} running this work unit.
     * <p>
     * {@link ExecutorExt#getCurrentWorkUnit()} and {@link WorkUnit#getExecutor()}
     * form a bi-directional reachability between them.
     */
    public ExecutorExt getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorExt e) {
        executor = e;
    }

    /**
     * If the execution has already started, return the current executable.
     */
    public Executable getExecutable() {
        return executor != null ? executor.getCurrentExecutable() : null;
    }

    /**
     * Is this work unit the "main work", which is the primary {@link SubTask}
     * represented by {@link Task} itself.
     */
    public boolean isMainWork() {
        return context.task == work;
    }
}

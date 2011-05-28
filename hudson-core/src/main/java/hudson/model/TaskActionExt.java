/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans
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


import hudson.security.Permission;
import hudson.security.ACL;

/**
 * Partial {@link Action} implementation for those who kick some
 * processing asynchronously (such as SCM tagging.)
 *
 * <p>
 * The class offers the basic set of functionality to do it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.191
 * @see TaskThread
 */
public abstract class TaskActionExt extends AbstractModelObjectExt implements Action {
    /**
     * If non-null, that means either the activitiy is in progress
     * asynchronously, or it failed unexpectedly and the thread is dead.
     */
    protected transient volatile TaskThreadExt workerThread;


    /**
     * Gets the permission object that represents the permission to perform this task.
     */
    protected abstract Permission getPermission();

    /**
     * Gets the {@link ACL} against which the permissions are checked.
     */
    protected abstract ACL getACL();


    public String getSearchUrl() {
        return getUrlName();
    }

    public TaskThreadExt getWorkerThread() {
        return workerThread;
    }

}


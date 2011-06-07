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

import hudson.console.AnnotatedLargeText;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.lang.ref.WeakReference;
import java.io.IOException;

import org.kohsuke.stapler.framework.io.LargeText;


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
public abstract class TaskAction extends TaskActionExt {
     
    /**
     * Hold the log of the tagging operation.
     */
    protected transient WeakReference<AnnotatedLargeText> log;

     

    /**
     * @deprecated as of 1.350
     *      Use {@link #obtainLog()}, which returns the same object in a more type-safe signature.
     */
    public LargeText getLog() {
        return obtainLog();
    }

    /**
     * Obtains the log file.
     *
     * <p>
     * The default implementation get this from {@link #workerThread},
     * so when it's complete, the log could be gone any time.
     *
     * <p>
     * Derived classes that persist the text should override this
     * method so that it fetches the file from disk.
     */
    public AnnotatedLargeText obtainLog() {
        WeakReference<AnnotatedLargeText> l = log;
        if(l==null) return null;
        return l.get();
    }

    /**
     * Handles incremental log output.
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        AnnotatedLargeText text = obtainLog();
        if(text!=null) {
            text.doProgressText(req,rsp);
            return;
        }
        rsp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Handles incremental log output.
     */
    public void doProgressiveHtml( StaplerRequest req, StaplerResponse rsp) throws IOException {
        AnnotatedLargeText text = obtainLog();
        if(text!=null) {
            text.doProgressiveHtml(req,rsp);
            return;
        }
        rsp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Clears the error status.
     */
    public synchronized void doClearError(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        if(workerThread!=null && !workerThread.isRunning())
            workerThread = null;
        rsp.sendRedirect(".");
    }
}


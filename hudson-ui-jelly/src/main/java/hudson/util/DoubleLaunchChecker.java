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

import hudson.model.HudsonExt;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import java.io.IOException;

/**
 * Makes sure that no other HudsonExt uses our <tt>HUDSON_HOME</tt> directory,
 * to forestall the problem of running multiple instances of HudsonExt that point to the same data directory.
 *
 * <p>
 * This set up error occasionally happens especialy when the user is trying to reassign the context path of the app,
 * and it results in a hard-to-diagnose error, so we actively check this.
 *
 * <p>
 * The mechanism is simple. This class occasionally updates a known file inside the hudson home directory,
 * and whenever it does so, it monitors the timestamp of the file to make sure no one else is updating
 * this file. In this way, while we cannot detect the problem right away, within a reasonable time frame
 * we can detect the collision.
 *
 * <p>
 * More traditional way of doing this is to use a lock file with PID in it, but unfortunately in Java,
 * there's no reliabe way to obtain PID.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.178
 */
public class DoubleLaunchChecker extends DoubleLaunchCheckerExt{
    

    public DoubleLaunchChecker() {
        super();
    }

    /**
     * Serve all URLs with the index view.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
        req.getView(this,"index.jelly").forward(req,rsp);
    }

    /**
     * Ignore the problem and go back to using HudsonExt.
     */
    public void doIgnore(StaplerRequest req, StaplerResponse rsp) throws IOException {
        ignore = true;
        HudsonExt.getInstance().servletContext.setAttribute("app",HudsonExt.getInstance());
        rsp.sendRedirect2(req.getContextPath()+'/');
    }
}

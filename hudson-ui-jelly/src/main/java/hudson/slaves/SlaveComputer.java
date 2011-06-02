/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.slaves;

import hudson.model.*;
import hudson.UtilExt;

import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpRedirect;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link ComputerExt} for {@link Slave}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveComputer extends SlaveComputerExt {

    public SlaveComputer(SlaveExt slave) {
        super(slave);

    }

    public HttpResponse doDoDisconnect(@QueryParameter String offlineMessage) throws IOException, ServletException {
        if (channel != null) {
            //does nothing in case computer is already disconnected
            checkPermission(HudsonExt.ADMINISTER);
            offlineMessage = UtilExt.fixEmptyAndTrim(offlineMessage);
            disconnect(OfflineCauseExt.create(Messages._SlaveComputer_DisconnectedBy(
                    HudsonExt.getAuthentication().getName(),
                    offlineMessage != null ? " : " + offlineMessage : "")));
        }
        return new HttpRedirect(".");
    }

    public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (channel != null) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        connect(true);

        // TODO: would be nice to redirect the user to "launching..." wait page,
        // then spend a few seconds there and poll for the completion periodically.
        rsp.sendRedirect("log");
    }
}

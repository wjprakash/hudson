/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.os.solaris;

import hudson.model.HudsonExt;
import hudson.util.HudsonIsRestarting;
import hudson.util.StreamTaskListener;
import hudson.util.jna.NativeAccessException;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.HttpRedirect;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encourages the user to migrate HUDSON_HOME on a ZFS file system. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.283
 */
public class ZFSInstaller extends ZFSInstallerExt {
    
    private static final Logger LOGGER = Logger.getLogger(ZFSInstaller.class.getName());
    
    /**
     * Called from the management screen.
     */
    public HttpResponse doAct(StaplerRequest req) throws ServletException, IOException {
        requirePOST();
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

        if(req.hasParameter("n")) {
            // we'll shut up
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        }

        return new HttpRedirect("confirm");
    }

    
    /**
     * Called from the confirmation screen to actually initiate the migration.
     */
    public void doStart(StaplerRequest req, StaplerResponse rsp, @QueryParameter String username, @QueryParameter String password) throws ServletException, IOException {
        requirePOST(); 
        HudsonExt hudson = HudsonExt.getInstance();
        hudson.checkPermission(HudsonExt.ADMINISTER);

        final String datasetName;
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        StreamTaskListener listener = new StreamTaskListener(log);
        try {
            datasetName = createZfsFileSystem(listener,username,password);
        } catch (Exception e) {
            e.printStackTrace(listener.error(e.getMessage()));

            if (e.getCause() instanceof NativeAccessException) {
                NativeAccessException ze = (NativeAccessException) e;
                if(ze.getCode() == NativeAccessException.PERMISSION) {
                    // permission problem. ask the user to give us the root password
                    req.setAttribute("message",log.toString());
                    rsp.forward(this,"askRootPassword",req);
                    return;
                }
            }

            // for other kinds of problems, report and bail out
            req.setAttribute("pre",true);
            sendError(log.toString(),req,rsp);
            return;
        }

        // file system creation successful, so restart

        hudson.servletContext.setAttribute("app",new HudsonIsRestarting());
        // redirect the user to the manage page
        rsp.sendRedirect2(req.getContextPath()+"/manage");

        // asynchronously restart, so that we can give a bit of time to the browser to load "restarting..." screen.
        new Thread("restart thread") {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                    
                    Map<String, String> properties = new HashMap<String, String>();
                    properties.put("ZFSInstaller.migrate", datasetName);

                    nativeUtils.restartJavaProcess(properties, true);
                } catch (NativeAccessException ex) {
                     LOGGER.log(Level.SEVERE, "Restart failed", ex);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "Restart failed",e);
                }
            }
        }.start();
    }
}

/**
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@Oracle.com
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
package hudson.lifecycle;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.HudsonExt;
import hudson.model.ManagementLink;
import hudson.util.StreamTaskListener;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import javax.servlet.ServletException;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.taskdefs.Move;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.apache.tools.ant.Project;

/**
 *
 * @author Winston Prakash
 */
public class WindowsInstallerLink extends WindowsInstallerLinkExt{
    
    protected WindowsInstallerLink(File hudsonWar) {
        super(hudsonWar);
    }
    
    /**
     * Performs installation.
     */
    public void doDoInstall(StaplerRequest req, StaplerResponse rsp, @QueryParameter("dir") String _dir) throws IOException, ServletException {
        if(installationDir!=null) {
            // installation already complete
            sendError("Installation is already complete",req,rsp);
            return;
        }
         
        
        try {
            if (!NativeUtils.getInstance().isDotNetInstalled(2, 0)) {
                sendError(".NET Framework 2.0 or later is required for this feature", req, rsp);
            }
        } catch (NativeAccessException exc) {
            sendError("Native function isDotNetInstalled() failed. " + NativeUtils.getInstance().getLastWindowsError(), req, rsp);
        }
        
        
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

        File dir = new File(_dir).getAbsoluteFile();
        dir.mkdirs();
        if(!dir.exists()) {
            sendError("Failed to create installation directory: "+dir,req,rsp);
            return;
        }

        try {
            // copy files over there
            copy(req, rsp, dir, getClass().getResource("/windows-service/hudson.exe"), "hudson.exe");
            copy(req, rsp, dir, getClass().getResource("/windows-service/hudson.xml"), "hudson.xml");
            if(!hudsonWar.getCanonicalFile().equals(new File(dir,"hudson.war").getCanonicalFile()))
                copy(req, rsp, dir, hudsonWar.toURI().toURL(), "hudson.war");

            // install as a service
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamTaskListener task = new StreamTaskListener(baos);
            task.getLogger().println("Installing a service");
            int r = WindowsSlaveInstaller.runElevated(
                    new File(dir, "hudson.exe"), "install", task, dir);
            if(r!=0) {
                sendError(baos.toString(),req,rsp);
                return;
            }

            // installation was successful
            installationDir = dir;
            rsp.sendRedirect(".");
        } catch (AbortException e) {
            // this exception is used as a signal to terminate processing. the error should have been already reported
        } catch (InterruptedException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Copies a single resource into the target folder, by the given name, and handle errors gracefully.
     */
    private void copy(StaplerRequest req, StaplerResponse rsp, File dir, URL src, String name) throws ServletException, IOException {
        try {
            FileUtils.copyURLToFile(src,new File(dir, name));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy "+name,e);
            sendError("Failed to copy "+name+": "+e.getMessage(),req,rsp);
            throw new AbortException();
        }
    }

    public void doRestart(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(installationDir==null) {
            // if the user reloads the page after HudsonExt has restarted,
            // it comes back here. In such a case, don't let this restart HudsonExt.
            // so just send them back to the top page
            rsp.sendRedirect(req.getContextPath()+"/");
            return;
        }
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

        rsp.forward(this,"_restart",req);
        final File oldRoot = HudsonExt.getInstance().getRootDir();

        // initiate an orderly shutdown after we finished serving this request
        new Thread("terminator") {
            public void run() {
                try {
                    Thread.sleep(1000);

                    // let the service start after we close our sockets, to avoid conflicts
                    Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
                        public void run() {
                            try {
                                if(!oldRoot.equals(installationDir)) {
                                    LOGGER.info("Moving data");
                                    Move mv = new Move();
                                    Project p = new Project();
                                    p.addBuildListener(createLogger());
                                    mv.setProject(p);
                                    FileSet fs = new FileSet();
                                    fs.setDir(oldRoot);
                                    fs.setExcludes("war/**"); // we can't really move the exploded war. 
                                    mv.addFileset(fs);
                                    mv.setTodir(installationDir);
                                    mv.setFailOnError(false); // plugins can also fail to move
                                    mv.execute();
                                }
                                LOGGER.info("Starting a Windows service");
                                StreamTaskListener task = StreamTaskListener.fromStdout();
                                int r = WindowsSlaveInstaller.runElevated(
                                        new File(installationDir, "hudson.exe"), "start", task, installationDir);
                                task.getLogger().println(r==0?"Successfully started":"start service failed. Exit code="+r);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        private DefaultLogger createLogger() {
                            DefaultLogger logger = new DefaultLogger();
                            logger.setOutputPrintStream(System.out);
                            logger.setErrorPrintStream(System.err);
                            return logger;
                        }
                    });

                    System.exit(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        sendError(e.getMessage(),req,rsp);
    }

    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message",message);
        req.setAttribute("pre",true);
        rsp.forward(HudsonExt.getInstance(),"error",req);
    }
    
    @Extension
    public static ManagementLink registerIfApplicable() {
        return WindowsInstallerLinkExt.registerIfApplicable();
    }
}

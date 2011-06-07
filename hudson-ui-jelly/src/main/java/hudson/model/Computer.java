/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Red Hat, Inc., Seiji Sogabe, Stephen Connolly, Thomas J. Black, Tom Huybrechts, CloudBees, Inc.
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

import hudson.StaplerUtils;
import hudson.UtilExt;
import hudson.cli.declarative.CLIMethod;
import hudson.console.AnnotatedLargeText;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.OfflineCauseExt;
import hudson.util.RemotingDiagnosticsExt;
import hudson.util.RunListExt;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;

/**
 * Represents the running state of a remote computer that holds {@link ExecutorExt}s.
 *
 * <p>
 * {@link ExecutorExt}s on one {@link ComputerExt} are transparently interchangeable
 * (that is the definition of {@link ComputerExt}.)
 *
 * <p>
 * This object is related to {@link NodeExt} but they have some significant difference.
 * {@link ComputerExt} primarily works as a holder of {@link ExecutorExt}s, so
 * if a {@link NodeExt} is configured (probably temporarily) with 0 executors,
 * you won't have a {@link ComputerExt} object for it.
 *
 * Also, even if you remove a {@link NodeExt}, it takes time for the corresponding
 * {@link ComputerExt} to be removed, if some builds are already in progress on that
 * node. Or when the node configuration is changed, unaffected {@link ComputerExt} object
 * remains intact, while all the {@link NodeExt} objects will go away.
 *
 * <p>
 * This object also serves UI (since {@link NodeExt} is an interface and can't have
 * related side pages.)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public  abstract class Computer extends ComputerExt {

    public Computer(NodeExt node) {
         super(node);
    }

     
    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText<Computer> getLogText() {
        return new AnnotatedLargeText<Computer>(getLogFile(), Charset.defaultCharset(), false, this);
    }

    /**
     * If the computer was offline (either temporarily or not),
     * this method will return the cause.
     *
     * @return
     *      null if the system was put offline without given a cause.
     */
    @Exported
    @Override
    public OfflineCauseExt getOfflineCause() {
        return super.getOfflineCause();
    }


    

    /**
     * Number of {@link ExecutorExt}s that are configured for this computer.
     *
     * <p>
     * When this value is decreased, it is temporarily possible
     * for {@link #executors} to have a larger number than this.
     */
    // ugly name to let EL access this
    @Exported
    @Override
    public int getNumExecutors() {
        return super.getNumExecutors();
    }

     

    @Exported
    @Override
    public LoadStatisticsExt getLoadStatistics() {
        return super.getLoadStatistics();
    }

    @Exported
    @Override
    public boolean isOffline() {
        return super.isOffline();
    }


    /**
     * This method is called to determine whether manual launching of the slave is allowed at this point in time.
     * @return {@code true} if manual launching of the slave is allowed at this point in time.
     */
    @Exported
    @Override
    public boolean isManualLaunchAllowed() {
        return super.isManualLaunchAllowed();
    }


    /**
     * Returns true if this computer is supposed to be launched via JNLP.
     * @deprecated since 2008-05-18.
     *     See {@linkplain #isLaunchSupported()} and {@linkplain ComputerLauncher}
     */
    @Exported
    @Deprecated
    @Override
    public boolean isJnlpAgent() {
        return super.isJnlpAgent();
    }

    /**
     * Returns true if this computer can be launched by HudsonExt proactively and automatically.
     *
     * <p>
     * For example, JNLP slaves return {@code false} from this, because the launch process
     * needs to be initiated from the slave side.
     */
    @Exported
    @Override
    public boolean isLaunchSupported() {
        return super.isLaunchSupported();
    }

    /**
     * Returns true if this node is marked temporarily offline by the user.
     *
     * <p>
     * In contrast, {@link #isOffline()} represents the actual online/offline
     * state. For example, this method may return false while {@link #isOffline()}
     * returns true if the slave agent failed to launch.
     *
     * @deprecated
     *      You should almost always want {@link #isOffline()}.
     *      This method is marked as deprecated to warn people when they
     *      accidentally call this method.
     */
    @Exported
    @Override
    public boolean isTemporarilyOffline() {
        return super.isTemporarilyOffline();
    }
 

    @Exported
    @Override
    public String getIcon() {
        return super.getIcon();
    }

     
    @Exported
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

      
    /**
     * Gets the read-only snapshot view of all {@link ExecutorExt}s.
     */
    @Exported
    @Override
    public List<ExecutorExt> getExecutors() {
        return super.getExecutors();
    }

    /**
     * Gets the read-only snapshot view of all {@link OneOffExecutor}s.
     */
    @Exported
    @Override
    public List<OneOffExecutor> getOneOffExecutors() {
        return super.getOneOffExecutors();
    }

    /**
     * Returns true if all the executors of this computer are idle.
     */
    @Exported
    @Override
    public final boolean isIdle() {
        return super.isIdle();
    }

     

    /**
     * Expose monitoring data for the remote API.
     */
    @Exported(inline=true)
    @Override
    public Map<String/*monitor name*/,Object> getMonitorData() {
        return super.getMonitorData();
    }

//
//
// UI
//
//
    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds());
    }
    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", getBuilds().failureOnly());
    }
    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunListExt runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }

    public HttpResponse doToggleOffline(@QueryParameter String offlineMessage) throws IOException, ServletException {
        checkPermission(HudsonExt.ADMINISTER);
        if(!temporarilyOffline) {
            offlineMessage = UtilExt.fixEmptyAndTrim(offlineMessage);
            setTemporarilyOffline(!temporarilyOffline,
                    OfflineCauseExt.create(hudson.slaves.Messages._SlaveComputer_DisconnectedBy(
                        HudsonExt.getAuthentication().getName(),
                        offlineMessage!=null ? " : " + offlineMessage : "")));
        } else {
            setTemporarilyOffline(!temporarilyOffline,null);
        }
        return HttpResponses.redirectToDot();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Dumps the contents of the export table.
     */
    public void doDumpExportTable( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        // this is a debug probe and may expose sensitive information
        checkPermission(HudsonExt.ADMINISTER);

        rsp.setContentType("text/plain");
        PrintWriter w = new PrintWriter(rsp.getCompressedWriter(req));
        VirtualChannel vc = getChannel();
        if (vc instanceof Channel) {
            w.println("Master to slave");
            ((Channel)vc).dumpExportTable(w);
            w.flush(); // flush here once so that even if the dump from the slave fails, the client gets some useful info

            w.println("\n\n\nSlave to master");
            w.print(vc.call(new DumpExportTableTask()));
        } else {
            w.println(Messages.Computer_BadChannel());
        }
        w.close();
    }

    /**
     * For system diagnostics.
     * RunExt arbitrary Groovy script.
     */
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        _doScript(req, rsp, "_script.jelly");
    }

    /**
     * RunExt arbitrary Groovy script and return result as plain text.
     */
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        _doScript(req, rsp, "_scriptText.jelly");
    }

    protected void _doScript( StaplerRequest req, StaplerResponse rsp, String view) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous,
        // so tie it to the admin access
        checkPermission(HudsonExt.ADMINISTER);

        String text = req.getParameter("script");
        if(text!=null) {
            try {
                req.setAttribute("output",
                RemotingDiagnosticsExt.executeGroovy(text,getChannel()));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        req.getView(this,view).forward(req,rsp);
    }

    /**
     * Accepts the update to the node configuration.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);
        
        final HudsonExt app = HudsonExt.getInstance();

        NodeExt result = getNode().getDescriptor().newInstance(req, req.getSubmittedForm());

        // replace the old NodeExt object by the new one
        synchronized (app) {
            List<NodeExt> nodes = new ArrayList<NodeExt>(app.getNodes());
            int i = nodes.indexOf(getNode());
            if(i<0) {
                StaplerUtils.sendError(this, "This slave appears to be removed while you were editing the configuration",req,rsp);
                return;
            }

            nodes.set(i,result);
            app.setNodes(nodes);
        }

        // take the user back to the slave top page.
        rsp.sendRedirect2("../"+result.getNodeName()+'/');
    }

    /**
     * Really deletes the slave.
     */
    @CLIMethod(name="delete-node")
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        HudsonExt.getInstance().removeNode(getNode());
        return new HttpRedirect("..");
    }

    /**
     * Handles incremental log.
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req,rsp);
    }
}

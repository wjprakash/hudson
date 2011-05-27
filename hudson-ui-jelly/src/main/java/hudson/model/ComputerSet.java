/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, Thomas J. Black
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

import hudson.BulkChange;
import hudson.Util;
import hudson.model.DescriptorExt.FormException;
import hudson.node_monitors.NodeMonitor;
import hudson.slaves.NodeDescriptorExt;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import java.io.IOException;

/**
 * Serves as the top of {@link ComputerExt}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class ComputerSet extends ComputerSetExt {
      

    @Exported
    public String getDisplayName() {
        return super.getDisplayName();
    }

    

    @Exported(name="computer",inline=true)
    public ComputerExt[] get_all() {
        return super.get_all();
    }

    

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    @Exported
    public int getTotalExecutors() {
         
        return super.getTotalExecutors();
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    @Exported
    public int getBusyExecutors() {
         
        return super.getBusyExecutors();
    }

    public ComputerExt getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return HudsonExt.getInstance().getComputer(token);
    }

    public void do_launchAll(StaplerRequest req, StaplerResponse rsp) throws IOException {
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

        for(ComputerExt c : get_all()) {
            if(c.isLaunchSupported())
                c.connect(true);
        }
        rsp.sendRedirect(".");
    }

    /**
     * Triggers the schedule update now.
     *
     * TODO: ajax on the client side to wait until the update completion might be nice.
     */
    public void doUpdateNow( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);
        
        for (NodeMonitor nodeMonitor : NodeMonitor.getAll()) {
            Thread t = nodeMonitor.triggerUpdate();
            t.setName(nodeMonitor.getColumnCaption());
        }
        rsp.forwardToPreviousPage(req);
    }

    /**
     * First check point in creating a new slave.
     */
    public synchronized void doCreateItem( StaplerRequest req, StaplerResponse rsp,
                                           @QueryParameter String name, @QueryParameter String mode,
                                           @QueryParameter String from ) throws IOException, ServletException {
        final HudsonExt app = HudsonExt.getInstance();
        app.checkPermission(HudsonExt.ADMINISTER);  // TODO: new permission?

        if(mode!=null && mode.equals("copy")) {
            name = checkName(name);

            Node src = app.getNode(from);
            if(src==null) {
                rsp.setStatus(SC_BAD_REQUEST);
                if(Util.fixEmpty(from)==null)
                    sendError(Messages.ComputerSet_SpecifySlaveToCopy(),req,rsp);
                else
                    sendError(Messages.ComputerSet_NoSuchSlave(from),req,rsp);
                return;
            }

            // copy through XStream
            String xml = HudsonExt.XSTREAM.toXML(src);
            Node result = (Node)HudsonExt.XSTREAM.fromXML(xml);
            result.setNodeName(name);
            result.holdOffLaunchUntilSave = true;

            app.addNode(result);

            // send the browser to the config page
            rsp.sendRedirect2(result.getNodeName()+"/configure");
        } else {
            // proceed to step 2
            if(mode==null) {
                rsp.sendError(SC_BAD_REQUEST);
                return;
            }

            NodeDescriptorExt d = NodeDescriptorExt.all().find(mode);
            d.handleNewNodePage(this,name,req,rsp);
        }
    }

    /**
     * Really creates a new slave.
     */
    public synchronized void doDoCreateItem( StaplerRequest req, StaplerResponse rsp,
                                           @QueryParameter String name,
                                           @QueryParameter String type ) throws IOException, ServletException, FormException {
        final HudsonExt app = HudsonExt.getInstance();
        app.checkPermission(HudsonExt.ADMINISTER);  // TODO: new permission?
        checkName(name);

        Node result = NodeDescriptorExt.all().find(type).newInstance(req, req.getSubmittedForm());
        app.addNode(result);

        // take the user back to the slave list top page
        rsp.sendRedirect2(".");
    }

    /**
     * Makes sure that the given name is good as a slave name.
     * @return trimmed name if valid; throws ParseException if not
     */
    public String checkName(String name) throws FailureExt {
        if(name==null)
            throw new FailureExt("Query parameter 'name' is required");

        name = name.trim();
        HudsonExt.checkGoodName(name);

        if(HudsonExt.getInstance().getNode(name)!=null)
            throw new FailureExt(Messages.ComputerSet_SlaveAlreadyExists(name));

        // looks good
        return name;
    }

     

    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        BulkChange bc = new BulkChange(MONITORS_OWNER);
        try {
            HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);
            monitors.rebuild(req,req.getSubmittedForm(),getNodeMonitorDescriptors());

            // add in the rest of instances are ignored instances
            for (DescriptorExt<NodeMonitor> d : NodeMonitor.all())
                if(monitors.get(d)==null) {
                    NodeMonitor i = createDefaultInstance(d, true);
                    if(i!=null)
                        monitors.add(i);
                }
            rsp.sendRedirect2(".");
        } finally {
            bc.commit();
        }
    }


    public Api getApi() {
        return new Api(this);
    }

}

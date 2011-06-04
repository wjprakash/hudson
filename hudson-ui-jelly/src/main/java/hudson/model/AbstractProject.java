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
package hudson.model;

import antlr.ANTLRException;
import com.sun.jdi.connect.Connector.Argument;
import hudson.FeedAdapter;
import hudson.FilePathExt;
import hudson.UtilExt;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.CauseExt.RemoteCause;
import hudson.model.CauseExt.UserCause;
import hudson.model.Descriptor.FormException;
import hudson.model.DescriptorExt.FormException;
import hudson.scm.ChangeLogSetExt;
import hudson.scm.ChangeLogSetExt.Entry;
import hudson.scm.SCMExt;
import hudson.scm.SCMS;
import hudson.tasks.BuildTriggerExt;
import hudson.tasks.MailerExt;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.DescribableListExt;
import hudson.util.FormValidation;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 *
 * @author Winston Prakash
 */
public abstract class AbstractProject extends AbstractProjectExt{
    
    protected AbstractProject(ItemGroup parent, String name) {
        super(parent,name);
    }
    
    /**
     * Does this project perform concurrent builds?
     * @since 1.319
     */
    @Exported
    public boolean isConcurrentBuild() {
        return super.isConcurrentBuild();
    }
    
    @Override
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        super.doConfigSubmit(req,rsp);

        updateTransientActions();

        Set<AbstractProjectExt> upstream = Collections.emptySet();
        if(req.getParameter("pseudoUpstreamTrigger")!=null) {
            upstream = new HashSet<AbstractProjectExt>(Items.fromNameList(req.getParameter("upstreamProjects"),AbstractProjectExt.class));
        }

        // dependency setting might have been changed by the user, so rebuild.
        HudsonExt.getInstance().rebuildDependencyGraph();

        // reflect the submission of the pseudo 'upstream build trriger'.
        // this needs to be done after we release the lock on 'this',
        // or otherwise we could dead-lock

        for (AbstractProjectExt<?,?> p : HudsonExt.getInstance().getAllItems(AbstractProjectExt.class)) {
            // Don't consider child projects such as MatrixConfiguration:
            if (!p.isConfigurable()) continue;
            boolean isUpstream = upstream.contains(p);
            synchronized(p) {
                // does 'p' include us in its BuildTrigger? 
                DescribableListExt<Publisher,DescriptorExt<Publisher>> pl = p.getPublishersList();
                BuildTriggerExt trigger = pl.get(BuildTriggerExt.class);
                List<AbstractProjectExt> newChildProjects = trigger == null ? new ArrayList<AbstractProjectExt>():trigger.getChildProjects();
                if(isUpstream) {
                    if(!newChildProjects.contains(this))
                        newChildProjects.add(this);
                } else {
                    newChildProjects.remove(this);
                }

                if(newChildProjects.isEmpty()) {
                    pl.remove(BuildTriggerExt.class);
                } else {
                    // here, we just need to replace the old one with the new one,
                    // but there was a regression (we don't know when it started) that put multiple BuildTriggers
                    // into the list.
                    // for us not to lose the data, we need to merge them all.
                    List<BuildTriggerExt> existingList = pl.getAll(BuildTriggerExt.class);
                    BuildTriggerExt existing;
                    switch (existingList.size()) {
                    case 0:
                        existing = null;
                        break;
                    case 1:
                        existing = existingList.get(0);
                        break;
                    default:
                        pl.removeAll(BuildTriggerExt.class);
                        Set<AbstractProjectExt> combinedChildren = new HashSet<AbstractProjectExt>();
                        for (BuildTriggerExt bt : existingList)
                            combinedChildren.addAll(bt.getChildProjects());
                        existing = new BuildTriggerExt(new ArrayList<AbstractProjectExt>(combinedChildren),existingList.get(0).getThreshold());
                        pl.add(existing);
                        break;
                    }

                    if(existing!=null && existing.hasSame(newChildProjects))
                        continue;   // no need to touch
                    pl.replace(new BuildTriggerExt(newChildProjects,
                        existing==null?ResultExt.SUCCESS:existing.getThreshold()));
                }
            }
        }

        // notify the queue as the project might be now tied to different node
        HudsonExt.getInstance().getQueue().scheduleMaintenance();

        // this is to reflect the upstream build adjustments done above
        HudsonExt.getInstance().rebuildDependencyGraph();
    }
    
    @Exported
    public SCMExt getScm() {
        return super.getScm();
    }
    
    /**
     * Gets the other {@link AbstractProjectExt}s that should be built
     * when a build of this project is completed.
     */
    @Exported
    public final List<AbstractProjectExt> getDownstreamProjects() {
        return  super.getDownstreamProjects();
    }

    @Exported
    public final List<AbstractProjectExt> getUpstreamProjects() {
        return super.getUpstreamProjects();
    }
    
     @Override
    protected HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget<R>(this,getBuilds(),HISTORY_ADAPTER);
    }
    
    
    //
//
// actions
//
//
    /**
     * Schedules a new build command.
     */
    public void doBuild( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationTokenExt.checkPermission(this, authToken, req, rsp);

        // if a build is parameterized, let that take over
        ParametersDefinitionPropertyExt pp = getProperty(ParametersDefinitionPropertyExt.class);
        if (pp != null) {
            pp._doBuild(req,rsp);
            return;
        }

        if (!isBuildable())
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR,new IOException(getFullName()+" is not buildable"));

        HudsonExt.getInstance().getQueue().schedule(this, getDelay(req), getBuildCause(req));
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Computes the build cause, using RemoteCause or UserCause as appropriate.
     */
    /*package*/ CauseActionExt getBuildCause(StaplerRequest req) {
        CauseExt cause;
        if (authToken != null && authToken.getToken() != null && req.getParameter("token") != null) {
            // Optional additional cause text when starting via token
            String causeText = req.getParameter("cause");
            cause = new RemoteCause(req.getRemoteAddr(), causeText);
        } else {
            cause = new UserCause();
        }
        return new CauseActionExt(cause);
    }

    /**
     * Computes the delay by taking the default value and the override in the request parameter into the account.
     */
    public int getDelay(StaplerRequest req) throws ServletException {
        String delay = req.getParameter("delay");
        if (delay==null)    return getQuietPeriod();

        try {
            // TODO: more unit handling
            if(delay.endsWith("sec"))   delay=delay.substring(0,delay.length()-3);
            if(delay.endsWith("secs"))  delay=delay.substring(0,delay.length()-4);
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            throw new ServletException("Invalid delay parameter value: "+delay);
        }
    }

    /**
     * Supports build trigger with parameters via an HTTP GET or POST.
     * Currently only String parameters are supported.
     */
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        BuildAuthorizationTokenExt.checkPermission(this, authToken, req, rsp);

        ParametersDefinitionPropertyExt pp = getProperty(ParametersDefinitionPropertyExt.class);
        if (pp != null) {
            pp.buildWithParameters(req,rsp);
        } else {
        	throw new IllegalStateException("This build is not parameterized!");
        }
    	
    }

    /**
     * Schedules a new SCM polling command.
     */
    public void doPolling( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BuildAuthorizationTokenExt.checkPermission(this, authToken, req, rsp);
        schedulePolling();
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(BUILD);

        HudsonExt.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);

        makeDisabled(req.getParameter("disable")!=null);

        jdk = req.getParameter("jdk");
        if(req.getParameter("hasCustomQuietPeriod")!=null) {
            quietPeriod = Integer.parseInt(req.getParameter("quiet_period"));
        } else {
            quietPeriod = null;
        }
        if(req.getParameter("hasCustomScmCheckoutRetryCount")!=null) {
            scmCheckoutRetryCount = Integer.parseInt(req.getParameter("scmCheckoutRetryCount"));
        } else {
            scmCheckoutRetryCount = null;
        }
        blockBuildWhenDownstreamBuilding = req.getParameter("blockBuildWhenDownstreamBuilding")!=null;
        blockBuildWhenUpstreamBuilding = req.getParameter("blockBuildWhenUpstreamBuilding")!=null;

        if(req.getParameter("hasSlaveAffinity")!=null) {
            assignedNode = UtilExt.fixEmptyAndTrim(req.getParameter("_.assignedLabelString"));
        } else {
            assignedNode = null;
        }
        
        if (req.getParameter("cleanWorkspaceRequired") != null) {
            cleanWorkspaceRequired = true;
        } else {
            cleanWorkspaceRequired = false;
        }
        
        canRoam = assignedNode==null;

        concurrentBuild = req.getSubmittedForm().has("concurrentBuild");

        authToken = BuildAuthorizationTokenExt.create(req);

        setScm(SCMS.parseSCM(req,this));

        for (Trigger t : triggers)
            t.stop();
        triggers = buildDescribable(req, Trigger.for_(this));
        for (Trigger t : triggers)
            t.start(this,true);
    }

    /**
     * @deprecated
     *      As of 1.261. Use {@link #buildDescribable(StaplerRequest, List)} instead.
     */
    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends DescriptorExt<T>> descriptors, String prefix) throws FormException, ServletException {
        return buildDescribable(req,descriptors);
    }

    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends DescriptorExt<T>> descriptors)
        throws FormException, ServletException {

        JSONObject data = req.getSubmittedForm();
        List<T> r = new Vector<T>();
        for (DescriptorExt<T> d : descriptors) {
            String safeName = d.getJsonSafeClassName();
            if (req.getParameter(safeName) != null) {
                T instance = d.newInstance(req, data.getJSONObject(safeName));
                r.add(instance);
            }
        }
        return r;
    }

    /**
     * Serves the workspace files.
     */
    public DirectoryBrowserSupportExt doWs( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        checkPermission(AbstractProjectExt.WORKSPACE);
        FilePathExt ws = getSomeWorkspace();
        if ((ws == null) || (!ws.exists())) {
            // if there's no workspace, report a nice error message
            // Would be good if when asked for *plain*, do something else!
            // (E.g. return 404, or send empty doc.)
            // Not critical; client can just check if content type is not text/plain,
            // which also serves to detect old versions of HudsonExt.
            req.getView(this,"noWorkspace.jelly").forward(req,rsp);
            return null;
        } else {
            return new DirectoryBrowserSupportExt(this, ws, getDisplayName()+" workspace", "folder.gif", true);
        }
    }

    /**
     * Wipes out the workspace.
     */
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, InterruptedException {
        if (cleanWorkspace()){
            return new HttpRedirect(".");
        }else{
            return new ForwardToView(this,"wipeOutWorkspaceBlocked.jelly");
        } 
    }
    
    /**
     * Validates the retry count Regex
     */
    public FormValidation doCheckRetryCount(@QueryParameter String value)throws IOException,ServletException{
        // retry count is optional so this is ok
        if(value == null || value.trim().equals(""))
            return FormValidation.ok();
        if (!value.matches("[0-9]*")) {
            return FormValidation.error("Invalid retry count");
        } 
        return FormValidation.ok();
    }
    
    @CLIMethod(name="disable-job")
    public HttpResponse doDisable() throws IOException, ServletException {
        requirePOST();
        checkPermission(CONFIGURE);
        makeDisabled(true);
        return new HttpRedirect(".");
    }

    @CLIMethod(name="enable-job")
    public HttpResponse doEnable() throws IOException, ServletException {
        requirePOST();
        checkPermission(CONFIGURE);
        makeDisabled(false);
        return new HttpRedirect(".");
    }
    
    /**
     * RSS feed for changes in this project.
     */
    public void doRssChangelog(  StaplerRequest req, StaplerResponse rsp  ) throws IOException, ServletException {
        class FeedItem {
            ChangeLogSetExt.Entry e;
            int idx;

            public FeedItem(Entry e, int idx) {
                this.e = e;
                this.idx = idx;
            }

            AbstractBuildExt<?,?> getBuild() {
                return e.getParent().build;
            }
        }

        List<FeedItem> entries = new ArrayList<FeedItem>();

        for(R r=getLastBuild(); r!=null; r=r.getPreviousBuild()) {
            int idx=0;
            for( ChangeLogSetExt.Entry e : r.getChangeSet())
                entries.add(new FeedItem(e,idx++));
        }

        RSS.forwardToRss(
            getDisplayName()+' '+getScm().getDescriptor().getDisplayName()+" changes",
            getUrl()+"changes",
            entries, new FeedAdapter<FeedItem>() {
                public String getEntryTitle(FeedItem item) {
                    return "#"+item.getBuild().number+' '+item.e.getMsg()+" ("+item.e.getAuthor()+")";
                }

                public String getEntryUrl(FeedItem item) {
                    return item.getBuild().getUrl()+"changes#detail"+item.idx;
                }

                public String getEntryID(FeedItem item) {
                    return getEntryUrl(item);
                }

                public String getEntryDescription(FeedItem item) {
                    StringBuilder buf = new StringBuilder();
                    for(String path : item.e.getAffectedPaths())
                        buf.append(path).append('\n');
                    return buf.toString();
                }

                public Calendar getEntryTimestamp(FeedItem item) {
                    return item.getBuild().getTimestamp();
                }

                public String getEntryAuthor(FeedItem entry) {
                    return MailerExt.descriptor().getAdminAddress();
                }
            },
            req, rsp );
    }
    
     /**
     * Used for CLI binding.
     */
    @CLIResolver
    public static AbstractProjectExt resolveForCLI(
            @Argument(required=true,metaVar="NAME",usage="Job name") String name) throws CmdLineException {
        AbstractProjectExt item = HudsonExt.getInstance().getItemByFullName(name, AbstractProjectExt.class);
        if (item==null)
            throw new CmdLineException(null,Messages.AbstractItem_NoSuchJobExists(name,AbstractProjectExt.findNearest(name).getFullName()));
        return item;
    }
    
    public static abstract class AbstractProjectDescriptor extends AbstractProjectDescriptorExt {
        public FormValidation doCheckAssignedLabelString(@QueryParameter String value) {
            if (UtilExt.fixEmpty(value)==null)
                return FormValidation.ok(); // nothing typed yet
            try {
                LabelExt.parseExpression(value);
            } catch (ANTLRException e) {
                return FormValidation.error(e,
                        Messages.AbstractProject_AssignedLabelString_InvalidBooleanExpression(e.getMessage()));
            }
            // TODO: if there's an atom in the expression that is empty, report it
            if (HudsonExt.getInstance().getLabel(value).isEmpty())
                return FormValidation.warning(Messages.AbstractProject_AssignedLabelString_NoMatch());
            return FormValidation.ok();
        }

       public AutoCompletionCandidatesExt doAutoCompleteAssignedLabelString(@QueryParameter String value) {
            AutoCompletionCandidatesExt c = new AutoCompletionCandidatesExt();
            Set<LabelExt> labels = HudsonExt.getInstance().getLabels();
            List<String> queries = new AutoCompleteSeeder(value).getSeeds();

            for (String term : queries) {
                for (LabelExt l : labels) {
                    if (l.getName().startsWith(term)) {
                        c.add(l.getName());
                    }
                }
            }
            return c;
        }

    }
}

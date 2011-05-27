/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.
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
import hudson.Functions;
import hudson.markup.MarkupFormatter;
import hudson.PluginManagerExt;
import hudson.StructuredForm;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import static hudson.Util.fixEmpty;
import hudson.cli.CLICommand;
import hudson.cli.CliEntryPoint;
import hudson.cli.CliManagerImpl;
import hudson.cli.declarative.CLIMethod;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Channel;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.BasicAuthenticationFilter;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Mailer;
import hudson.util.FormValidation;
import hudson.util.Futures;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import hudson.util.MultipartFormDataParser;
import hudson.util.RemotingDiagnostics;
import hudson.views.DefaultMyViewsTabBar;
import hudson.views.DefaultViewsTabBar;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.ui.AbstractProcessingFilter;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.jvnet.hudson.reactor.ReactorException;
import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.framework.adjunct.AdjunctManager;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;
import org.kohsuke.stapler.jelly.JellyRequestDispatcher;
import org.xml.sax.InputSource;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Root object of the system.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class Hudson extends HudsonExt implements ItemGroup<TopLevelItem>, StaplerProxy, StaplerFallback, ViewGroup, AccessControlled, DescriptorByNameOwner {

     /**
     * Currently active Views tab bar.
     */
    private volatile ViewsTabBar viewsTabBar = new DefaultViewsTabBar();

    /**
     * Currently active My Views tab bar.
     */
    private volatile MyViewsTabBar myViewsTabBar = new DefaultMyViewsTabBar();
    
    private static final Logger LOGGER = Logger.getLogger(Hudson.class.getName());
    
    protected transient final Map<UUID,FullDuplexHttpChannel> duplexChannels = new HashMap<UUID, FullDuplexHttpChannel>();
    /**
     * Load statistics of the entire system.
     */
    @Exported
    public transient final OverallLoadStatistics overallLoad = new OverallLoadStatistics();


    /**
     * {@link AdjunctManager}
     */
    private transient final AdjunctManager adjuncts = null;

    /**
     * Code that handles {@link ItemGroup} work.
     */
    private transient final ItemGroupMixIn itemGroupMixIn = new ItemGroupMixIn(this,this) {
        @Override
        protected void add(TopLevelItem item) {
            items.put(item.getName(),item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return Hudson.this.getRootDirFor(name);
        }

        /**
         *send the browser to the config page
         * use View to trim view/{default-view} from URL if possible
         */
        @Override
        protected String redirectAfterCreateItem(StaplerRequest req, TopLevelItem result) throws IOException {
            String redirect = result.getUrl()+"configure";
            List<Ancestor> ancestors = req.getAncestors();
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                Object o = ancestors.get(i).getObject();
                if (o instanceof View) {
                    redirect = req.getContextPath() + '/' + ((View)o).getUrl() + redirect;
                    break;
                }
            }
            return redirect;
        }
    };

   
     
    public Hudson(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root,context,null);
    }

    /**
     * @param pluginManager
     *      If non-null, use existing plugin manager.  create a new one.
     */
    public Hudson(File root, ServletContext context, PluginManagerExt pluginManager) throws IOException, InterruptedException, ReactorException {
    	 super(root, context, pluginManager);
    }

     public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    public MyViewsTabBar getMyViewsTabBar() {
        return myViewsTabBar;
    }

    @Exported
    public int getSlaveAgentPort() {
        return super.getSlaveAgentPort();
    }

     

    @Exported
    public String getDescription() {
        return super.getDescription();
    }

    public Api getApi() {
        return new Api(this);
    }
 
    /**
     * Gets the read-only list of all {@link View}s.
     */
    @Exported
    public synchronized Collection<View> getViews() {
        return super.getViews();
    }

    /**
     * Returns the primary {@link View} that renders the top-page of HudsonExt.
     */
    @Exported
    public View getPrimaryView() {
         return super.getPrimaryView();
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * Gets the absolute URL of HudsonExt,
     * such as "http://localhost/hudson/".
     *
     * <p>
     * This method first tries to use the manually configured value, then
     * fall back to {@link StaplerRequest#getRootPath()}.
     * It is done in this order so that it can work correctly even in the face
     * of a reverse proxy.
     *
     * @return
     *      This method returns null if this parameter is not configured by the user.
     *      The caller must gracefully deal with this situation.
     *      The returned URL will always have the trailing '/'.
     * @since 1.66
     * @see DescriptorExt#getCheckUrl(String)
     * @see #getRootUrlFromRequest()
     */
    public String getRootUrl() {
        // for compatibility. the actual data is stored in Mailer
        String url = Mailer.descriptor().getUrl();
        if(url!=null)   return url;

        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null)
            return getRootUrlFromRequest();
        return null;
    }

    /**
     * Gets the absolute URL of HudsonExt top page, such as "http://localhost/hudson/".
     *
     * <p>
     * Unlike {@link #getRootUrl()}, which uses the manually configured value,
     * this one uses the current request to reconstruct the URL. The benefit is
     * that this is immune to the configuration mistake (users often fail to set the root URL
     * correctly, especially when a migration is involved), but the downside
     * is that unless you are processing a request, this method doesn't work.
     *
     * @since 1.263
     */
    public String getRootUrlFromRequest() {
        StaplerRequest req = Stapler.getCurrentRequest();
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()+"://");
        buf.append(req.getServerName());
        if(req.getServerPort()!=80)
            buf.append(':').append(req.getServerPort());
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    public Object getDynamic(String token) {
        for (Action a : getActions())
            if(a.getUrlName().equals(token) || a.getUrlName().equals('/'+token))
                return a;
        for (Action a : getManagementLinks())
            if(a.getUrlName().equals(token))
                return a;
        return null;
    }


//
//
// actions
//
//
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        BulkChange bc = new BulkChange(this);
        try {
            checkPermission(ADMINISTER);

            JSONObject json = req.getSubmittedForm();

            // keep using 'useSecurity' field as the main configuration setting
            // until we get the new security implementation working
            // useSecurity = null;
            if (json.has("use_security")) {
                useSecurity = true;
                JSONObject security = json.getJSONObject("use_security");
                setSecurityRealm(SecurityRealm.all().newInstanceFromRadioList(security,"realm"));
                setAuthorizationStrategy(AuthorizationStrategy.all().newInstanceFromRadioList(security, "authorization"));

                if (security.has("markupFormatter")) {
                    markupFormatter = req.bindJSON(MarkupFormatter.class,security.getJSONObject("markupFormatter"));
                } else {
                    markupFormatter = null;
                }
            } else {
                useSecurity = null;
                setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                authorizationStrategy = AuthorizationStrategy.UNSECURED;
                markupFormatter = null;
            }

            if (json.has("csrf")) {
            	JSONObject csrf = json.getJSONObject("csrf");
                setCrumbIssuer(CrumbIssuer.all().newInstanceFromRadioList(csrf, "issuer"));
            } else {
            	setCrumbIssuer(null);
            }

            if (json.has("viewsTabBar")) {
                viewsTabBar = req.bindJSON(ViewsTabBar.class,json.getJSONObject("viewsTabBar"));
            } else {
                viewsTabBar = new DefaultViewsTabBar();
            }

            if (json.has("myViewsTabBar")) {
                myViewsTabBar = req.bindJSON(MyViewsTabBar.class,json.getJSONObject("myViewsTabBar"));
            } else {
                myViewsTabBar = new DefaultMyViewsTabBar();
            }

            primaryView = json.has("primaryView") ? json.getString("primaryView") : getViews().iterator().next().getViewName();

            noUsageStatistics = json.has("usageStatisticsCollected") ? null : true;

            {
                String v = req.getParameter("slaveAgentPortType");
                if(!isUseSecurity() || v==null || v.equals("random"))
                    slaveAgentPort = 0;
                else
                if(v.equals("disable"))
                    slaveAgentPort = -1;
                else {
                    try {
                        slaveAgentPort = Integer.parseInt(req.getParameter("slaveAgentPort"));
                    } catch (NumberFormatException e) {
                        throw new FormException(Messages.Hudson_BadPortNumber(req.getParameter("slaveAgentPort")),"slaveAgentPort");
                    }
                }

                // relaunch the agent
                if(tcpSlaveAgentListener==null) {
                    if(slaveAgentPort!=-1)
                        tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                } else {
                    if(tcpSlaveAgentListener.configuredPort!=slaveAgentPort) {
                        tcpSlaveAgentListener.shutdown();
                        tcpSlaveAgentListener = null;
                        if(slaveAgentPort!=-1)
                            tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                    }
                }
            }

            numExecutors = json.getInt("numExecutors");
            if(req.hasParameter("master.mode"))
                mode = Mode.valueOf(req.getParameter("master.mode"));
            else
                mode = Mode.NORMAL;

            label = json.optString("labelString","");

            quietPeriod = json.getInt("quiet_period");

            scmCheckoutRetryCount = json.getInt("retry_count");

            systemMessage = Util.nullify(req.getParameter("system_message"));

            jdks.clear();
            jdks.addAll(req.bindJSONToList(JDKExt.class,json.get("jdks")));

            boolean result = true;
            for( Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfig() )
                result &= configureDescriptor(req,json,d);

            for( JSONObject o : StructuredForm.toList(json,"plugin"))
                pluginManager.getPlugin(o.getString("name")).getPlugin().configure(req, o);

            clouds.rebuildHetero(req,json, Cloud.all(), "cloud");

            JSONObject np = json.getJSONObject("globalNodeProperties");
            if (np != null) {
                globalNodeProperties.rebuild(req, np, NodeProperty.for_(this));
            }

            version = VERSION;

            save();
            updateComputerList();
            if(result)
                rsp.sendRedirect(req.getContextPath()+'/');  // go to the top page
            else
                rsp.sendRedirect("configure"); // back to config
        } finally {
            bc.commit();
        }
    }

  
    public synchronized void doTestPost( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rsp.sendRedirect("foo");
    }

    private boolean configureDescriptor(StaplerRequest req, JSONObject json, Descriptor<?> d) throws FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigExecutorsSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(ADMINISTER);

        BulkChange bc = new BulkChange(this);
        try {
            JSONObject json = req.getSubmittedForm();

            setNumExecutors(Integer.parseInt(req.getParameter("numExecutors")));
            if(req.hasParameter("master.mode"))
                mode = Mode.valueOf(req.getParameter("master.mode"));
            else
                mode = Mode.NORMAL;

            setNodes(req.bindJSONToList(Slave.class,json.get("slaves")));
        } finally {
            bc.commit();
        }

        rsp.sendRedirect(req.getContextPath() + '/');  // go to the top page
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    /**
     * @deprecated as of 1.317
     *      Use {@link #doQuietDown()} instead.
     */
    public synchronized void doQuietDown(StaplerResponse rsp) throws IOException, ServletException {
        doQuietDown().generateResponse(null, rsp, this);
    }

    public synchronized HttpRedirect doQuietDown() throws IOException {
        try {
            return doQuietDown(false,0);
        } catch (InterruptedException e) {
            throw new AssertionError(); // impossible
        }
    }

    @CLIMethod(name="quiet-down")
    public HttpRedirect doQuietDown(
            @Option(name="-block",usage="Block until the system really quiets down and no builds are running") @QueryParameter boolean block,
            @Option(name="-timeout",usage="If non-zero, only block up to the specified number of milliseconds") @QueryParameter int timeout) throws InterruptedException, IOException {
        synchronized (this) {
            checkPermission(ADMINISTER);
            isQuietingDown = true;
        }
        if (block) {
            if (timeout > 0) timeout += System.currentTimeMillis();
            while (isQuietingDown
                   && (timeout <= 0 || System.currentTimeMillis() < timeout)
                   && !RestartListener.isAllReady()) {
                Thread.sleep(1000);
            }
        }
        return new HttpRedirect(".");
    }

    @CLIMethod(name="cancel-quiet-down")
    public synchronized HttpRedirect doCancelQuietDown() {
        checkPermission(ADMINISTER);
        isQuietingDown = false;
        getQueue().scheduleMaintenance();
        return new HttpRedirect(".");
    }

    /**
     * Backward compatibility. Redirect to the thread dump.
     */
    public void doClassicThreadDump(StaplerResponse rsp) throws IOException, ServletException {
        rsp.sendRedirect2("threadDump");
    }

    public synchronized ItemExt doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        return itemGroupMixIn.createTopLevelItem(req, rsp);
    }


    public synchronized void doCreateView( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req,rsp, this));
    }

    /**
     * Checks if the user was successfully authenticated.
     *
     * @see BasicAuthenticationFilter
     */
    public void doSecured( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(req.getUserPrincipal()==null) {
            // authentication must have failed
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // the user is now authenticated, so send him back to the target
        String path = req.getContextPath()+req.getOriginalRestOfPath();
        String q = req.getQueryString();
        if(q!=null)
            path += '?'+q;

        rsp.sendRedirect2(path);
    }

    /**
     * Called once the user logs in. Just forward to the top page.
     */
    public void doLoginEntry( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(req.getUserPrincipal()==null) {
            rsp.sendRedirect2("noPrincipal");
            return;
        }

        String from = req.getParameter("from");
        if(from!=null && from.startsWith("/") && !from.equals("/loginError")) {
            rsp.sendRedirect2(from);    // I'm bit uncomfortable letting users redircted to other sites, make sure the URL falls into this domain
            return;
        }

        String url = AbstractProcessingFilter.obtainFullRequestUrl(req);
        if(url!=null) {
            // if the login redirect is initiated by Acegi
            // this should send the user back to where s/he was from.
            rsp.sendRedirect2(url);
            return;
        }

        rsp.sendRedirect2(".");
    }

    /**
     * Logs out the user.
     */
    public void doLogout( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        securityRealm.doLogout(req, rsp);
    }


    public Slave.JnlpJar doJnlpJars(StaplerRequest req) {
        return new Slave.JnlpJar(req.getRestOfPath());
    }

    /**
     * RSS feed for log entries.
     *
     * @deprecated
     *   As on 1.267, moved to "/log/rss..."
     */
    public void doLogRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        String qs = req.getQueryString();
        rsp.sendRedirect2("./log/rss"+(qs==null?"":'?'+qs));
    }

    /**
     * Reloads the configuration.
     */
    @CLIMethod(name="reload-configuration")
    public synchronized HttpResponse doReload() throws IOException {
        checkPermission(ADMINISTER);

        // engage "loading ..." UI and then run the actual task in a separate thread
        servletContext.setAttribute("app", new HudsonIsLoading());

        new Thread("Hudson config reload thread") {
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                    reload();
                } catch (IOException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                } catch (ReactorException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                } catch (InterruptedException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                }
            }
        }.start();

        return HttpResponses.redirectViaContextPath("/");
    }


    /**
     * Do a finger-print check.
     */
    public void doDoFingerprintCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // Parse the request
        MultipartFormDataParser p = new MultipartFormDataParser(req);
        if(Hudson.getInstance().isUseCrumbs() && !Hudson.getInstance().getCrumbIssuer().validateCrumb(req, p)) {
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN,"No crumb found");
        }
        try {
            rsp.sendRedirect2(req.getContextPath()+"/fingerprint/"+
                Util.getDigestOf(p.getFileItem("name").getInputStream())+'/');
        } finally {
            p.cleanUp();
        }
    }

    /**
     * For debugging. Expose URL to perform GC.
     */
    public void doGc(StaplerResponse rsp) throws IOException {
        checkPermission(Hudson.ADMINISTER);
        System.gc();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("GCed");
    }

    

    /**
     * Handles HTTP requests for duplex channels for CLI.
     */
    public void doCli(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!"POST".equals(req.getMethod())) {
            // for GET request, serve _cli.jelly, assuming this is a browser
            checkPermission(READ);
            req.getView(this,"_cli.jelly").forward(req,rsp);
            return;
        }

        // do not require any permission to establish a CLI connection
        // the actual authentication for the connecting Channel is done by CLICommand

        UUID uuid = UUID.fromString(req.getHeader("Session"));
        rsp.setHeader("Hudson-Duplex",""); // set the header so that the client would know

        FullDuplexHttpChannel server;
        if(req.getHeader("Side").equals("download")) {
            duplexChannels.put(uuid,server=new FullDuplexHttpChannel(uuid, !hasPermission(ADMINISTER)) {
                protected void main(Channel channel) throws IOException, InterruptedException {
                    // capture the identity given by the transport, since this can be useful for SecurityRealm.createCliAuthenticator()
                    channel.setProperty(CLICommand.TRANSPORT_AUTHENTICATION,getAuthentication());
                    channel.setProperty(CliEntryPoint.class.getName(),new CliManagerImpl());
                }
            });
            try {
                server.download(req,rsp);
            } finally {
                duplexChannels.remove(uuid);
            }
        } else {
            duplexChannels.get(uuid).upload(req,rsp);
        }
    }

    /**
     * Binds /userContent/... to $HUDSON_HOME/userContent.
     */
    public DirectoryBrowserSupportExt doUserContent() {
        return new DirectoryBrowserSupportExt(this,getRootPath().child("userContent"),"User content","folder.gif",true);
    }

    /**
     * Perform a restart of HudsonExt, if we can.
     *
     * This first replaces "app" to {@link HudsonIsRestarting}
     */
    @CLIMethod(name="restart")
    public void doRestart(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(ADMINISTER);
        if (req != null && req.getMethod().equals("GET")) {
            req.getView(this,"_restart.jelly").forward(req,rsp);
            return;
        }

        restart();

        if (rsp != null) // null for CLI
            rsp.sendRedirect2(".");
    }

    /**
     * Queues up a restart of HudsonExt for when there are no builds running, if we can.
     *
     * This first replaces "app" to {@link HudsonIsRestarting}
     *
     * @since 1.332
     */
    @CLIMethod(name="safe-restart")
    public void doSafeRestart(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(ADMINISTER);
        if (req != null && req.getMethod().equals("GET")) {
            req.getView(this,"_safeRestart.jelly").forward(req,rsp);
            return;
        }

        safeRestart();

        if (rsp != null) // null for CLI
            rsp.sendRedirect2(".");
    }

     
    /**
     * Shutdown the system.
     * @since 1.161
     */
    public void doExit( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        checkPermission(ADMINISTER);
        LOGGER.severe(String.format("Shutting down VM as requested by %s from %s",
                getAuthentication().getName(), req.getRemoteAddr()));
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        PrintWriter w = rsp.getWriter();
        w.println("Shutting down");
        w.close();

        System.exit(0);
    }


    /**
     * Shutdown the system safely.
     * @since 1.332
     */
    public void doSafeExit( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        checkPermission(ADMINISTER);
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        PrintWriter w = rsp.getWriter();
        w.println("Shutting down as soon as all jobs are complete");
        w.close();
        isQuietingDown = true;
        final String exitUser = getAuthentication().getName();
        final String exitAddr = req.getRemoteAddr().toString();
        new Thread("safe-exit thread") {
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                    LOGGER.severe(String.format("Shutting down VM as requested by %s from %s",
                                                exitUser, exitAddr));
                    // Wait 'til we have no active executors.
                    while (isQuietingDown
                           && (overallLoad.computeTotalExecutors() > overallLoad.computeIdleExecutors())) {
                        Thread.sleep(5000);
                    }
                    // Make sure isQuietingDown is still true.
                    if (isQuietingDown) {
                        cleanUp();
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to shutdown Hudson",e);
                }
            }
        }.start();
    }


    /**
     * For system diagnostics.
     * Run arbitrary Groovy script.
     */
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doScript(req, rsp, req.getView(this, "_script.jelly"));
    }

    /**
     * Run arbitrary Groovy script and return result as plain text.
     */
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doScript(req, rsp, req.getView(this, "_scriptText.jelly"));
    }

    private void doScript(StaplerRequest req, StaplerResponse rsp, RequestDispatcher view) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous
        checkPermission(ADMINISTER);

        String text = req.getParameter("script");
        if (text != null) {
            try {
                req.setAttribute("output",
                        RemotingDiagnostics.executeGroovy(text, MasterComputer.localChannel));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        view.forward(req, rsp);
    }

    /**
     * Evaluates the Jelly script submitted by the client.
     *
     * This is useful for system administration as well as unit testing.
     */
    public void doEval(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(ADMINISTER);
        requirePOST();

        try {
            MetaClass mc = WebApp.getCurrent().getMetaClass(getClass());
            Script script = mc.classLoader.loadTearOff(JellyClassLoaderTearOff.class).createContext().compileScript(new InputSource(req.getReader()));
            new JellyRequestDispatcher(this,script).forward(req,rsp);
        } catch (JellyException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Sign up for the user account.
     */
    public void doSignup( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        req.getView(getSecurityRealm(), "signup.jelly").forward(req, rsp);
    }

    /**
     * Changes the icon size by changing the cookie
     */
    public void doIconSize( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        String qs = req.getQueryString();
        if(qs==null || !ICON_SIZE.matcher(qs).matches())
            throw new ServletException();
        Cookie cookie = new Cookie("iconSize", qs);
        cookie.setMaxAge(/* ~4 mo. */9999999); // #762
        rsp.addCookie(cookie);
        String ref = req.getHeader("Referer");
        if(ref==null)   ref=".";
        rsp.sendRedirect2(ref);
    }

    public void doFingerprintCleanup(StaplerResponse rsp) throws IOException {
        FingerprintCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    public void doWorkspaceCleanup(StaplerResponse rsp) throws IOException {
        WorkspaceCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    /**
     * If the user chose the default JDKExt, make sure we got 'java' in PATH.
     */
    public FormValidation doDefaultJDKCheck(StaplerRequest request, @QueryParameter String value) {
        if(!value.equals("(Default)"))
            // assume the user configured named ones properly in system config ---
            // or else system config should have reported form field validation errors.
            return FormValidation.ok();

        // default JDKExt selected. Does such java really exist?
        if(JDKExt.isDefaultJDKValid(Hudson.this))
            return FormValidation.ok();
        else
            return FormValidation.errorWithMarkup(Messages.Hudson_NoJavaInPath(request.getContextPath()));
    }

    /**
     * Makes sure that the given name is good as a job name.
     */
    public FormValidation doCheckJobName(@QueryParameter String value) {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        checkPermission(ItemExt.CREATE);

        if(fixEmpty(value)==null)
            return FormValidation.ok();

        try {
            checkJobName(value);
            return FormValidation.ok();
        } catch (FailureExt e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Checks if a top-level view with the given name exists.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        if(view==null) return FormValidation.ok();

        if(getView(view)==null)
            return FormValidation.ok();
        else
            return FormValidation.error(Messages.Hudson_ViewAlreadyExists(view));
    }

    /**
     * @deprecated as of 1.294
     *      Define your own check method, instead of relying on this generic one.
     */
    public void doFieldCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doFieldCheck(
                fixEmpty(req.getParameter("value")),
                fixEmpty(req.getParameter("type")),
                fixEmpty(req.getParameter("errorText")),
                fixEmpty(req.getParameter("warningText"))).generateResponse(req,rsp,this);
    }

    /**
     * Checks if the value for a field is set; if not an error or warning text is displayed.
     * If the parameter "value" is not set then the parameter "errorText" is displayed
     * as an error text. If the parameter "errorText" is not set, then the parameter "warningText"
     * is displayed as a warning text.
     * <p>
     * If the text is set and the parameter "type" is set, it will validate that the value is of the
     * correct type. Supported types are "number, "number-positive" and "number-negative".
     *
     * @deprecated as of 1.324
     *      Either use client-side validation (e.g. class="required number")
     *      or define your own check method, instead of relying on this generic one.
     */
    public FormValidation doFieldCheck(@QueryParameter(fixEmpty=true) String value,
                                       @QueryParameter(fixEmpty=true) String type,
                                       @QueryParameter(fixEmpty=true) String errorText,
                                       @QueryParameter(fixEmpty=true) String warningText) {
        if (value == null) {
            if (errorText != null)
                return FormValidation.error(errorText);
            if (warningText != null)
                return FormValidation.warning(warningText);
            return FormValidation.error("No error or warning text was set for fieldCheck().");
        }

        if (type != null) {
            try {
                if (type.equalsIgnoreCase("number")) {
                    NumberFormat.getInstance().parse(value);
                } else if (type.equalsIgnoreCase("number-positive")) {
                    if (NumberFormat.getInstance().parse(value).floatValue() <= 0)
                        return FormValidation.error(Messages.Hudson_NotAPositiveNumber());
                } else if (type.equalsIgnoreCase("number-negative")) {
                    if (NumberFormat.getInstance().parse(value).floatValue() >= 0)
                        return FormValidation.error(Messages.Hudson_NotANegativeNumber());
                }
            } catch (ParseException e) {
                return FormValidation.error(Messages.Hudson_NotANumber());
            }
        }

        return FormValidation.ok();
    }

    /**
     * Serves static resources placed along with Jelly view files.
     * <p>
     * This method can serve a lot of files, so care needs to be taken
     * to make this method secure. It's not clear to me what's the best
     * strategy here, though the current implementation is based on
     * file extensions.
     */
    public void doResources(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        // cut off the "..." portion of /resources/.../path/to/file
        // as this is only used to make path unique (which in turn
        // allows us to set a long expiration date
        path = path.substring(path.indexOf('/',1)+1);

        int idx = path.lastIndexOf('.');
        String extension = path.substring(idx+1);
        if(ALLOWED_RESOURCE_EXTENSIONS.contains(extension)) {
            URL url = pluginManager.uberClassLoader.getResource(path);
            if(url!=null) {
                long expires = MetaClass.NO_CACHE ? 0 : 365L * 24 * 60 * 60 * 1000; /*1 year*/
                rsp.serveFile(req,url,expires);
                return;
            }
        }
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }


    /**
     * Checks if container uses UTF-8 to decode URLs. See
     * http://wiki.hudson-ci.com/display/HUDSON/Tomcat#Tomcat-i18n
     */
    public FormValidation doCheckURIEncoding(StaplerRequest request) throws IOException {
        // expected is non-ASCII String
        final String expected = "\u57f7\u4e8b";
        final String value = fixEmpty(request.getParameter("value"));
        if (!expected.equals(value))
            return FormValidation.warningWithMarkup(Messages.Hudson_NotUsesUTF8ToDecodeURL());
        return FormValidation.ok();
    }

    public Object getTarget() {
        try {
            checkPermission(READ);
        } catch (AccessDeniedException e) {
            String rest = Stapler.getCurrentRequest().getRestOfPath();
            if(rest.startsWith("/login")
            || rest.startsWith("/logout")
            || rest.startsWith("/accessDenied")
            || rest.startsWith("/signup")
            || rest.startsWith("/jnlpJars/")
            || rest.startsWith("/tcpSlaveAgentListener")
            || rest.startsWith("/cli")
            || rest.startsWith("/whoAmI")
            || rest.startsWith("/federatedLoginService/")
            || rest.startsWith("/securityRealm"))
                return this;    // URLs that are always visible without READ permission
            throw e;
        }
        return this;
    }

    /**
     * Fallback to the primary view.
     */
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    public static final class MasterComputer extends Computer {
        private MasterComputer() {
            super(Hudson.getInstance());
        }

        /**
         * Returns "" to match with {@link HudsonExt#getNodeName()}.
         */
        @Override
        public String getName() {
            return "";
        }

        @Override
        public boolean isConnecting() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return Messages.Hudson_Computer_DisplayName();
        }

        @Override
        public String getCaption() {
            return Messages.Hudson_Computer_Caption();
        }

        @Override
        public String getUrl() {
            return "computer/(master)/";
        }

        public RetentionStrategy getRetentionStrategy() {
            return RetentionStrategy.NOOP;
        }

        /**
         * Report an error.
         */
        @Override
        public HttpResponse doDoDelete() throws IOException {
            throw HttpResponses.status(SC_BAD_REQUEST);
        }

        @Override
        public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // the master node isn't in the HudsonExt.getNodes(), so this method makes no sense.
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPermission(Permission permission) {
            // no one should be allowed to delete the master.
            // this hides the "delete" link from the /computer/(master) page.
            if(permission==ComputerExt.DELETE)
                return false;
            // Configuration of master node requires ADMINISTER permission
            return super.hasPermission(permission==ComputerExt.CONFIGURE ? Hudson.ADMINISTER : permission);
        }

        @Override
        public VirtualChannel getChannel() {
            return localChannel;
        }

        @Override
        public Charset getDefaultCharset() {
            return Charset.defaultCharset();
        }

        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            return logRecords;
        }

        public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this computer never returns null from channel, so
            // this method shall never be invoked.
            rsp.sendError(SC_NOT_FOUND);
        }

        /**
         * Redirect the master configuration to /configure.
         */
        public void doConfigure(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            rsp.sendRedirect2(req.getContextPath()+"/configure");
        }

        protected Future<?> _connect(boolean forceReconnect) {
            return Futures.precomputed(null);
        }

        /**
         * {@link LocalChannel} instance that can be used to execute programs locally.
         */
        public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);
    }

    
    /**
     * @deprecated since 2007-12-18.
     *      Use {@link #checkPermission(Permission)}
     */
    public static boolean adminCheck() throws IOException {
        return adminCheck(Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
    }
    
    /**
     * @deprecated since 2007-12-18.
     *      Define a custom {@link Permission} and check against ACL.
     *      See {@link #isAdmin()} for more instructions.
     */
    public static boolean isAdmin(StaplerRequest req) {
        return isAdmin();
    }


    /**
     * @deprecated since 2007-12-18.
     *      Use {@link #checkPermission(Permission)}
     */
    public static boolean adminCheck(StaplerRequest req,StaplerResponse rsp) throws IOException {
        if (isAdmin(req)) return true;

        rsp.sendError(StaplerResponse.SC_FORBIDDEN);
        return false;
    }

}

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

import antlr.ANTLRException;
import com.thoughtworks.xstream.XStream;
import hudson.BulkChange;
import hudson.DescriptorExtensionListExt;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListView;
import hudson.ExtensionPoint;
import hudson.FilePathExt;
import hudson.FunctionsExt;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.LocalPluginManager;
import hudson.Lookup;
import hudson.markup.MarkupFormatter;
import hudson.PluginExt;
import hudson.PluginManagerExt;
import hudson.PluginWrapperExt;
import hudson.ProxyConfiguration;
import hudson.TcpSlaveAgentListener;
import hudson.UDPBroadcastThread;
import hudson.UtilExt;
import hudson.XmlFile;
import hudson.cli.declarative.CLIResolver;
import hudson.init.InitMilestone;
import hudson.init.InitReactorListener;
import hudson.init.InitStrategy;
import hudson.lifecycle.Lifecycle;
import hudson.logging.LogRecorderManagerExt;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.markup.RawHtmlMarkupFormatterExt;
import hudson.model.labels.LabelAtomExt;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SCMListener;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.scm.RepositoryBrowserExt;
import hudson.scm.SCMExt;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategyExt;
import hudson.security.FederatedLoginService;
import hudson.security.HudsonFilter;
import hudson.security.LegacyAuthorizationStrategyExt;
import hudson.security.LegacySecurityRealmExt;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.SecurityMode;
import hudson.security.SecurityRealmExt;
import hudson.security.csrf.CrumbIssuerExt;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlaveExt;
import hudson.slaves.NodeDescriptorExt;
import hudson.slaves.NodeList;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCauseExt;
import hudson.slaves.RetentionStrategyExt;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AdministrativeError;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.ClockDifferenceExt;
import hudson.util.CopyOnWriteList;
import hudson.util.CopyOnWriteMap;
import hudson.util.DaemonThreadFactory;
import hudson.util.DescribableListExt;
import hudson.util.Futures;
import hudson.util.Iterators;
import hudson.util.Memoizer;
import hudson.util.RemotingDiagnosticsExt.HeapDumpExt;
import hudson.util.StreamTaskListener;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import hudson.util.Service;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorListener;
import org.jvnet.hudson.reactor.TaskGraphBuilder.Handle;
import org.kohsuke.args4j.Argument;

import javax.crypto.SecretKey;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import static hudson.init.InitMilestone.*;
import hudson.tasks.MailerExt;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.text.Collator;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.management.Descriptor;

/**
 * Root object of the system.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonExt extends NodeExt implements ItemGroup<TopLevelItem>, AccessControlled, DescriptorByNameOwner {

    private transient final QueueExt queue;
    /**
     * Stores various objects scoped to {@link HudsonExt}.
     */
    public transient final Lookup lookup = new Lookup();
    /**
     * {@link ComputerExt}s in this HudsonExt system. Read-only.
     */
    protected transient final Map<NodeExt, ComputerExt> computers = new CopyOnWriteMap.Hash<NodeExt, ComputerExt>();
    /**
     * We update this field to the current version of HudsonExt whenever we save {@code config.xml}.
     * This can be used to detect when an upgrade happens from one version to next.
     *
     * <p>
     * Since this field is introduced starting 1.301, "1.0" is used to represent every version
     * up to 1.300. This value may also include non-standard versions like "1.301-SNAPSHOT" or
     * "?", etc., so parsing needs to be done with a care.
     *
     * @since 1.301
     */
    // this field needs to be at the very top so that other components can look at this value even during unmarshalling
    protected String version = "1.0";
    /**
     * Number of executors of the master node.
     */
    protected int numExecutors = 2;
    /**
     * JobExt allocation strategy.
     */
    protected ModeExt mode = ModeExt.NORMAL;
    /**
     * False to enable anyone to do anything.
     * Left as a field so that we can still read old data that uses this flag.
     *
     * @see #authorizationStrategy
     * @see #securityRealm
     */
    protected Boolean useSecurity;
    /**
     * Controls how the
     * <a href="http://en.wikipedia.org/wiki/Authorization">authorization</a>
     * is handled in HudsonExt.
     * <p>
     * This ultimately controls who has access to what.
     *
     * Never null.
     */
    protected volatile AuthorizationStrategyExt authorizationStrategy = AuthorizationStrategyExt.UNSECURED;
    /**
     * Controls a part of the
     * <a href="http://en.wikipedia.org/wiki/Authentication">authentication</a>
     * handling in HudsonExt.
     * <p>
     * Intuitively, this corresponds to the user database.
     *
     * See {@link HudsonFilter} for the concrete authentication protocol.
     *
     * Never null. Always use {@link #setSecurityRealm(SecurityRealm)} to
     * update this field.
     *
     * @see #getSecurity()
     * @see #setSecurityRealm(SecurityRealm)
     */
    protected volatile SecurityRealmExt securityRealm = SecurityRealmExt.NO_AUTHENTICATION;
    /**
     * Message displayed in the top page.
     */
    protected String systemMessage;
    protected MarkupFormatter markupFormatter;
    /**
     * Root directory of the system.
     */
    public transient final File root;
    /**
     * Where are we in the initialization?
     */
    private transient volatile InitMilestone initLevel = InitMilestone.STARTED;
    /**
     * All {@link ItemExt}s keyed by their {@link ItemExt#getName() name}s.
     */
    /*package*/ transient final Map<String, TopLevelItem> items = new CopyOnWriteMap.Tree<String, TopLevelItem>(CaseInsensitiveComparator.INSTANCE);
    /**
     * The sole instance.
     */
    private static HudsonExt theInstance;
    protected transient volatile boolean isQuietingDown;
    private transient volatile boolean terminating;
    protected List<JDKExt> jdks = new ArrayList<JDKExt>();
    private transient volatile DependencyGraph dependencyGraph;
    /**
     * All {@link ExtensionList} keyed by their {@link ExtensionList#extensionType}.
     */
    private transient final Memoizer<Class, ExtensionList> extensionLists = new Memoizer<Class, ExtensionList>() {

        public ExtensionList compute(Class key) {
            return ExtensionList.create(HudsonExt.this, key);
        }
    };
    /**
     * All {@link DescriptorExtensionListExt} keyed by their {@link DescriptorExtensionListExt#describableType}.
     */
    private transient final Memoizer<Class, DescriptorExtensionListExt> descriptorLists = new Memoizer<Class, DescriptorExtensionListExt>() {

        public DescriptorExtensionListExt compute(Class key) {
            return DescriptorExtensionListExt.createDescriptorList(HudsonExt.this, key);
        }
    };
    /**
     * Active {@link Cloud}s.
     */
    public final CloudList clouds = new CloudList(this);
    /**
     * Root URL to be used by Hudson
     */
    private String rootUrl;

    public static class CloudList extends DescribableListExt<Cloud, DescriptorExt<Cloud>> {

        public CloudList(HudsonExt h) {
            super(h);
        }

        public CloudList() {// needed for XStream deserialization
        }

        public Cloud getByName(String name) {
            for (Cloud c : this) {
                if (c.name.equals(name)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        protected void onModified() throws IOException {
            super.onModified();
            HudsonExt.getInstance().trimLabels();
        }
    }
    /**
     * Set of installed cluster nodes.
     * <p>
     * We use this field with copy-on-write semantics.
     * This field has mutable list (to keep the serialization look clean),
     * but it shall never be modified. Only new completely populated slave
     * list can be set here.
     * <p>
     * The field name should be really {@code nodes}, but again the backward compatibility
     * prevents us from renaming.
     */
    private volatile NodeList slaves;
    /**
     * Quiet period.
     *
     * This is {@link Integer} so that we can initialize it to '5' for upgrading users.
     */
    /*package*/ Integer quietPeriod;
    /**
     * Global default for {@link AbstractProjectExt#getScmCheckoutRetryCount()}
     */
    /*package*/ int scmCheckoutRetryCount;
    /**
     * Name of the primary view.
     * <p>
     * Start with null, so that we can upgrade pre-1.269 data well.
     * @since 1.269
     */
    protected volatile String primaryView;
    private transient final FingerprintMap fingerprintMap = new FingerprintMap();
    /**
     * Loaded plugins.
     */
    public transient final PluginManagerExt pluginManager;
    public transient volatile TcpSlaveAgentListener tcpSlaveAgentListener;
    private transient UDPBroadcastThread udpBroadcastThread;
    /**
     * List of registered {@link ItemListener}s.
     * @deprecated as of 1.286
     */
    private transient final CopyOnWriteList<ItemListener> itemListeners = ExtensionListView.createCopyOnWriteList(ItemListener.class);
    /**
     * List of registered {@link SCMListener}s.
     */
    private transient final CopyOnWriteList<SCMListener> scmListeners = new CopyOnWriteList<SCMListener>();
    /**
     * List of registered {@link ComputerListener}s.
     * @deprecated as of 1.286
     */
    private transient final CopyOnWriteList<ComputerListener> computerListeners = ExtensionListView.createCopyOnWriteList(ComputerListener.class);
    /**
     * TCP slave agent port.
     * 0 for random, -1 to disable.
     */
    protected int slaveAgentPort = 0;
    /**
     * Whitespace-separated labels assigned to the master as a {@link NodeExt}.
     */
    protected String label = "";
    /**
     * {@link hudson.security.csrf.CrumbIssuer}
     */
    private volatile CrumbIssuerExt crumbIssuer;
    /**
     * All labels known to HudsonExt. This allows us to reuse the same label instances
     * as much as possible, even though that's not a strict requirement.
     */
    private transient final ConcurrentHashMap<String, LabelExt> labels = new ConcurrentHashMap<String, LabelExt>();
    /**
     * Load statistics of the entire system.
     */
    public transient final OverallLoadStatisticsExt overallLoad = new OverallLoadStatisticsExt();
    /**
     * {@link NodeProvisioner} that reacts to {@link OverallLoadStatistics}.
     */
    public transient final NodeProvisioner overallNodeProvisioner = new NodeProvisioner(null, overallLoad);
    public transient final ServletContext servletContext;
    /**
     * Transient action list. Useful for adding navigation items to the navigation bar
     * on the left.
     */
    private transient final List<Action> actions = new CopyOnWriteArrayList<Action>();
    /**
     * List of master node properties
     */
    private DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = new DescribableListExt<NodeProperty<?>, NodePropertyDescriptor>(this);
    /**
     * List of global properties
     */
    protected DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = new DescribableListExt<NodeProperty<?>, NodePropertyDescriptor>(this);
    /**
     * {@link AdministrativeMonitorExt}s installed on this system.
     *
     * @see AdministrativeMonitorExt
     */
    public transient final List<AdministrativeMonitorExt> administrativeMonitors = getExtensionList(AdministrativeMonitorExt.class);

    /*package*/ final CopyOnWriteArraySet<String> disabledAdministrativeMonitors = new CopyOnWriteArraySet<String>();
    /**
     * Code that handles {@link ItemGroup} work.
     */
    private transient final ItemGroupMixInExt itemGroupMixIn = new ItemGroupMixInExt(this, this) {

        @Override
        protected void add(TopLevelItem item) {
            items.put(item.getName(), item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return HudsonExt.this.getRootDirFor(name);
        }
    };

    @CLIResolver
    public static HudsonExt getInstance() {
        return theInstance;
    }
    /**
     * Secrete key generated once and used for a long time, beyond
     * container start/stop. Persisted outside <tt>config.xml</tt> to avoid
     * accidental exposure.
     */
    private transient final String secretKey;
    private transient final UpdateCenterExt updateCenter = new UpdateCenterExt();
    /**
     * True if the user opted out from the statistics tracking. We'll never send anything if this is true.
     */
    protected Boolean noUsageStatistics;
    /**
     * HTTP proxy configuration.
     */
    public transient volatile ProxyConfiguration proxy;
    /**
     * Bound to "/log".
     */
    private transient final LogRecorderManagerExt log = new LogRecorderManagerExt();

    public HudsonExt(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root, context, null);
    }

    /**
     * @param pluginManager
     *      If non-null, use existing plugin manager.  create a new one.
     */
    public HudsonExt(File root, ServletContext context, PluginManagerExt pluginManager) throws IOException, InterruptedException, ReactorException {
        // As hudson is starting, grant this process full control
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            this.root = root;
            this.servletContext = context;
            computeVersion(context);
            if (theInstance != null) {
                throw new IllegalStateException("second instance");
            }
            theInstance = this;

            // doing this early allows InitStrategy to set environment upfront
            final InitStrategy is = InitStrategy.get(Thread.currentThread().getContextClassLoader());

            Trigger.timer = new Timer("Hudson cron thread");
            queue = new QueueExt(CONSISTENT_HASH ? LoadBalancer.CONSISTENT_HASH : LoadBalancer.DEFAULT);

            try {
                dependencyGraph = DependencyGraph.EMPTY;
            } catch (InternalError e) {
                if (e.getMessage().contains("window server")) {
                    throw new Error("Looks like the server runs without X. Please specify -Djava.awt.headless=true as JVM option", e);
                }
                throw e;
            }

            // get or create the secret
            TextFile secretFile = new TextFile(new File(HudsonExt.getInstance().getRootDir(), "secret.key"));
            if (secretFile.exists()) {
                secretKey = secretFile.readTrim();
            } else {
                SecureRandom sr = new SecureRandom();
                byte[] random = new byte[32];
                sr.nextBytes(random);
                secretKey = UtilExt.toHexString(random);
                secretFile.write(secretKey);
            }

            try {
                proxy = ProxyConfiguration.load();
            } catch (IOException e) {
                LOGGER.log(SEVERE, "Failed to load proxy configuration", e);
            }

            if (pluginManager == null) {
                pluginManager = new LocalPluginManager(this);
            }
            this.pluginManager = pluginManager;

            // initialization consists of ...
            executeReactor(is,
                    pluginManager.initTasks(is), // loading and preparing plugins
                    loadTasks(), // load jobs
                    InitMilestone.ordering() // forced ordering among key milestones
                    );

            if (KILL_AFTER_LOAD) {
                System.exit(0);
            }

            if (slaveAgentPort != -1) {
                try {
                    tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                } catch (BindException e) {
                    new AdministrativeError(getClass().getName() + ".tcpBind",
                            "Failed to listen to incoming slave connection",
                            "Failed to listen to incoming slave connection. <a href='configure'>Change the port number</a> to solve the problem.", e);
                }
            } else {
                tcpSlaveAgentListener = null;
            }

            try {
                udpBroadcastThread = new UDPBroadcastThread(this);
                udpBroadcastThread.start();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Faild to broadcast over UDP", e);
            }


            updateComputerList();

            {// master is online now
                ComputerExt c = toComputer();
                if (c != null) {
                    for (ComputerListener cl : ComputerListener.all()) {
                        cl.onOnline(c, StreamTaskListener.fromStdout());
                    }
                }
            }

            for (ItemListener l : ItemListener.all()) {
                l.onLoaded();
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Executes a reactor.
     *
     * @param is
     *      If non-null, this can be consulted for ignoring some tasks. Only used during the initialization of HudsonExt.
     */
    private void executeReactor(final InitStrategy is, TaskBuilder... builders) throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(builders) {

            /**
             * Sets the thread name to the task for better diagnostics.
             */
            @Override
            protected void runTask(Task task) throws Exception {
                if (is != null && is.skipInitTask(task)) {
                    return;
                }

                SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);   // full access in the initialization thread
                String taskName = task.getDisplayName();

                Thread t = Thread.currentThread();
                String name = t.getName();
                if (taskName != null) {
                    t.setName(taskName);
                }
                try {
                    long start = System.currentTimeMillis();
                    super.runTask(task);
                    if (LOG_STARTUP_PERFORMANCE) {
                        LOGGER.info(String.format("Took %dms for %s by %s",
                                System.currentTimeMillis() - start, taskName, name));
                    }
                } finally {
                    t.setName(name);
                    SecurityContextHolder.clearContext();
                }
            }
        };

        ExecutorService es;
        if (PARALLEL_LOAD) {
            es = new ThreadPoolExecutor(
                    TWICE_CPU_NUM, TWICE_CPU_NUM, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());
        } else {
            es = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        }
        try {
            reactor.execute(es, buildReactorListener());
        } finally {
            es.shutdownNow();   // upon a successful return the executor queue should be empty. Upon an exception, we want to cancel all pending tasks
        }
    }

    /**
     * Aggregates all the listeners into one and returns it.
     *
     * <p>
     * At this point plugins are not loaded yet, so we fall back to the META-INF/services look up to discover implementations.
     * As such there's no way for plugins to participate into this process.
     */
    private ReactorListener buildReactorListener() throws IOException {
        List<ReactorListener> r = (List) Service.loadInstances(Thread.currentThread().getContextClassLoader(), InitReactorListener.class);
        r.add(new ReactorListener() {

            final Level level = Level.parse(System.getProperty(HudsonExt.class.getName() + ".initLogLevel", "FINE"));

            public void onTaskStarted(Task t) {
                LOGGER.log(level, "Started " + t.getDisplayName());
            }

            public void onTaskCompleted(Task t) {
                LOGGER.log(level, "Completed " + t.getDisplayName());
            }

            public void onTaskFailed(Task t, Throwable err, boolean fatal) {
                LOGGER.log(SEVERE, "Failed " + t.getDisplayName(), err);
            }

            public void onAttained(Milestone milestone) {
                Level lv = level;
                String s = "Attained " + milestone.toString();
                if (milestone instanceof InitMilestone) {
                    lv = Level.INFO; // noteworthy milestones --- at least while we debug problems further
                    initLevel = (InitMilestone) milestone;
                    s = initLevel.toString();
                }
                LOGGER.log(lv, s);
            }
        });
        return new ReactorListener.Aggregator(r);
    }

    public TcpSlaveAgentListener getTcpSlaveAgentListener() {
        return tcpSlaveAgentListener;
    }

    public int getSlaveAgentPort() {
        return slaveAgentPort;
    }

    /**
     * If you are calling this on HudsonExt something is wrong.
     *
     * @deprecated
     */
    @Deprecated
    @Override
    public String getNodeName() {
        return "";
    }

    public void setNodeName(String name) {
        throw new UnsupportedOperationException(); // not allowed
    }

    public String getNodeDescription() {
        return Messages.Hudson_NodeDescription();
    }

    public String getDescription() {
        return systemMessage;
    }

    public PluginManagerExt getPluginManager() {
        return pluginManager;
    }

    public UpdateCenterExt getUpdateCenter() {
        return updateCenter;
    }

    public boolean isUsageStatisticsCollected() {
        return noUsageStatistics == null || !noUsageStatistics;
    }

    public void setNoUsageStatistics(Boolean noUsageStatistics) throws IOException {
        this.noUsageStatistics = noUsageStatistics;
        save();
    }

    /**
     * Returns a secret key that survives across container start/stop.
     * <p>
     * This value is useful for implementing some of the security features.
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Gets {@linkplain #getSecretKey() the secret key} as a key for AES-128.
     * @since 1.308
     */
    public SecretKey getSecretKeyAsAES128() {
        return UtilExt.toAes128Key(secretKey);
    }

    /**
     * Gets the SCM descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<SCMExt> getScm(String shortClassName) {
        return findDescriptor(shortClassName, SCMExt.all());
    }

    /**
     * Gets the repository browser descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<RepositoryBrowserExt<?>> getRepositoryBrowser(String shortClassName) {
        return findDescriptor(shortClassName, RepositoryBrowserExt.all());
    }

    /**
     * Gets the builder descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<Builder> getBuilder(String shortClassName) {
        return findDescriptor(shortClassName, Builder.all());
    }

    /**
     * Gets the build wrapper descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<BuildWrapper> getBuildWrapper(String shortClassName) {
        return findDescriptor(shortClassName, BuildWrapper.all());
    }

    /**
     * Gets the publisher descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<Publisher> getPublisher(String shortClassName) {
        return findDescriptor(shortClassName, Publisher.all());
    }

    /**
     * Gets the trigger descriptor by name. Primarily used for making them web-visible.
     */
    public TriggerDescriptor getTrigger(String shortClassName) {
        return (TriggerDescriptor) findDescriptor(shortClassName, Trigger.all());
    }

    /**
     * Gets the retention strategy descriptor by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<RetentionStrategyExt<?>> getRetentionStrategy(String shortClassName) {
        return findDescriptor(shortClassName, RetentionStrategyExt.all());
    }

    /**
     * Gets the {@link JobPropertyDescriptorExt} by name. Primarily used for making them web-visible.
     */
    public JobPropertyDescriptorExt getJobProperty(String shortClassName) {
        // combining these two lines triggers javac bug. See issue #610.
        DescriptorExt d = findDescriptor(shortClassName, JobPropertyDescriptorExt.all());
        return (JobPropertyDescriptorExt) d;
    }

    /**
     * Exposes {@link DescriptorExt} by its name to URL.
     *
     * After doing all the {@code getXXX(shortClassName)} methods, I finally realized that
     * this just doesn't scale.
     *
     * @param id
     *      Either {@link DescriptorExt#getId()} (recommended) or the short name of a {@link Describable} subtype (for compatibility)
     */
    public DescriptorExt getDescriptor(String id) {
        // legacy descriptors that are reigstered manually doesn't show up in getExtensionList, so check them explicitly.
        for (DescriptorExt d : Iterators.sequence(getExtensionList(DescriptorExt.class), DescriptorExtensionListExt.listLegacyInstances())) {
            String name = d.getId();
            if (name.equals(id)) {
                return d;
            }
            if (name.substring(name.lastIndexOf('.') + 1).equals(id)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Alias for {@link #getDescriptor(String)}.
     */
    public DescriptorExt getDescriptorByName(String id) {
        return getDescriptor(id);
    }

    /**
     * Gets the {@link DescriptorExt} that corresponds to the given {@link Describable} type.
     * <p>
     * If you have an instance of {@code type} and call {@link Describable#getDescriptor()},
     * you'll get the same instance that this method returns.
     */
    public DescriptorExt getDescriptor(Class<? extends Describable> type) {
        for (DescriptorExt d : getExtensionList(DescriptorExt.class)) {
            if (d.clazz == type) {
                return d;
            }
        }
        return null;
    }

    /**
     * Works just like {@link #getDescriptor(Class)} but don't take no for an answer.
     *
     * @throws AssertionError
     *      If the descriptor is missing.
     * @since 1.326
     */
    public DescriptorExt getDescriptorOrDie(Class<? extends Describable> type) {
        DescriptorExt d = getDescriptor(type);
        if (d == null) {
            throw new AssertionError(type + " is missing its descriptor");
        }
        return d;
    }

    /**
     * Gets the {@link DescriptorExt} instance in the current HudsonExt by its type.
     */
    public <T extends DescriptorExt> T getDescriptorByType(Class<T> type) {
        for (DescriptorExt d : getExtensionList(DescriptorExt.class)) {
            if (d.getClass() == type) {
                return type.cast(d);
            }
        }
        return null;
    }

    /**
     * Gets the {@link SecurityRealm} descriptors by name. Primarily used for making them web-visible.
     */
    public DescriptorExt<SecurityRealmExt> getSecurityRealms(String shortClassName) {
        return findDescriptor(shortClassName, SecurityRealmExt.all());
    }

    /**
     * Finds a descriptor that has the specified name.
     */
    private <T extends Describable<T>> DescriptorExt<T> findDescriptor(String shortClassName, Collection<? extends DescriptorExt<T>> descriptors) {
        String name = '.' + shortClassName;
        for (DescriptorExt<T> d : descriptors) {
            if (d.clazz.getName().endsWith(name)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Gets all the installed {@link ItemListener}s.
     *
     * @deprecated as of 1.286.
     *      Use {@link ItemListener#all()}.
     */
    public CopyOnWriteList<ItemListener> getJobListeners() {
        return itemListeners;
    }

    /**
     * Gets all the installed {@link SCMListener}s.
     */
    public CopyOnWriteList<SCMListener> getSCMListeners() {
        return scmListeners;
    }

    /**
     * Gets all the installed {@link ComputerListener}s.
     *
     * @deprecated as of 1.286.
     *      Use {@link ComputerListener#all()}.
     */
    public CopyOnWriteList<ComputerListener> getComputerListeners() {
        return computerListeners;
    }

    /**
     * Gets the plugin object from its short name.
     *
     * <p>
     * This allows URL <tt>hudson/plugin/ID</tt> to be served by the views
     * of the plugin class.
     */
    public PluginExt getPlugin(String shortName) {
        PluginWrapperExt p = pluginManager.getPlugin(shortName);
        if (p == null) {
            return null;
        }
        return p.getPlugin();
    }

    /**
     * Gets the plugin object from its class.
     *
     * <p>
     * This allows easy storage of plugin information in the plugin singleton without
     * every plugin reimplementing the singleton pattern.
     *
     * @param clazz The plugin class (beware class-loader fun, this will probably only work
     * from within the hpi that defines the plugin class, it may or may not work in other cases)
     *
     * @return The plugin instance.
     */
    @SuppressWarnings("unchecked")
    public <P extends PluginExt> P getPlugin(Class<P> clazz) {
        PluginWrapperExt p = pluginManager.getPlugin(clazz);
        if (p == null) {
            return null;
        }
        return (P) p.getPlugin();
    }

    /**
     * Gets the plugin objects from their super-class.
     *
     * @param clazz The plugin class (beware class-loader fun)
     *
     * @return The plugin instances.
     */
    public <P extends PluginExt> List<P> getPlugins(Class<P> clazz) {
        List<P> result = new ArrayList<P>();
        for (PluginWrapperExt w : pluginManager.getPlugins(clazz)) {
            result.add((P) w.getPlugin());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Synonym to {@link #getNodeDescription()}.
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Gets the markup formatter used in the system.
     *
     * @return
     *      never null.
     * @since 1.391
     */
    public MarkupFormatter getMarkupFormatter() {
        return markupFormatter != null ? markupFormatter : RawHtmlMarkupFormatterExt.INSTANCE;
    }

    /**
     * Sets the markup formatter used in the system globally.
     *
     * @since 1.391
     */
    public void setMarkupFormatter(MarkupFormatter f) {
        this.markupFormatter = f;
    }

    /**
     * Sets the system message.
     */
    public void setSystemMessage(String message) throws IOException {
        this.systemMessage = message;
        save();
    }

    public FederatedLoginService getFederatedLoginService(String name) {
        for (FederatedLoginService fls : FederatedLoginService.all()) {
            if (fls.getUrlName().equals(name)) {
                return fls;
            }
        }
        return null;
    }

    public List<FederatedLoginService> getFederatedLoginServices() {
        return FederatedLoginService.all();
    }

    public Launcher createLauncher(TaskListener listener) {
        return new LocalLauncher(listener).decorateFor(this);
    }
    private final transient Object updateComputerLock = new Object();

    /**
     * Updates {@link #computers} by using {@link #getSlaves()}.
     *
     * <p>
     * This method tries to reuse existing {@link ComputerExt} objects
     * so that we won't upset {@link Executor}s running in it.
     */
    protected void updateComputerList() throws IOException {
        synchronized (updateComputerLock) {// just so that we don't have two code updating computer list at the same time
            Map<String, ComputerExt> byName = new HashMap<String, ComputerExt>();
            for (ComputerExt c : computers.values()) {
                if (c.getNode() == null) {
                    continue;   // this computer is gone
                }
                byName.put(c.getNode().getNodeName(), c);
            }

            Set<ComputerExt> old = new HashSet<ComputerExt>(computers.values());
            Set<ComputerExt> used = new HashSet<ComputerExt>();

            updateComputer(this, byName, used);
            for (NodeExt s : getNodes()) {
                updateComputer(s, byName, used);
            }

            // find out what computers are removed, and kill off all executors.
            // when all executors exit, it will be removed from the computers map.
            // so don't remove too quickly
            old.removeAll(used);
            for (ComputerExt c : old) {
                c.kill();
            }
        }
        getQueue().scheduleMaintenance();
        for (ComputerListener cl : ComputerListener.all()) {
            cl.onConfigurationChange();
        }
    }

    private void updateComputer(NodeExt n, Map<String, ComputerExt> byNameMap, Set<ComputerExt> used) {
        ComputerExt c;
        c = byNameMap.get(n.getNodeName());
        if (c != null) {
            c.setNode(n); // reuse
        } else {
            if (n.getNumExecutors() > 0) {
                computers.put(n, c = n.createComputer());
                if (!n.holdOffLaunchUntilSave && AUTOMATIC_SLAVE_LAUNCH) {
                    RetentionStrategyExt retentionStrategy = c.getRetentionStrategy();
                    if (retentionStrategy != null) {
                        // if there is a retention strategy, it is responsible for deciding to start the computer
                        retentionStrategy.start(c);
                    } else {
                        // we should never get here, but just in case, we'll fall back to the legacy behaviour
                        c.connect(true);
                    }
                }
            }
        }
        used.add(c);
    }

    /*package*/ void removeComputer(ComputerExt computer) {
        for (Entry<NodeExt, ComputerExt> e : computers.entrySet()) {
            if (e.getValue() == computer) {
                computers.remove(e.getKey());
                return;
            }
        }
        throw new IllegalStateException("Trying to remove unknown computer");
    }

    public String getFullName() {
        return "";
    }

    public String getFullDisplayName() {
        return "";
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * <p>
     * Adding {@link Action} is primarily useful for plugins to contribute
     * an item to the navigation bar of the top page. See existing {@link Action}
     * implementation for it affects the GUI.
     *
     * <p>
     * To register an {@link Action}, implement {@link RootAction} extension point, or write code like
     * {@code HudsonExt.getInstance().getActions().add(...)}.
     *
     * @return
     *      Live list where the changes can be made. Can be empty but never null.
     * @since 1.172
     */
    public List<Action> getActions() {
        return actions;
    }

    /**
     * Gets just the immediate children of {@link HudsonExt}.
     *
     * @see #getAllItems(Class)
     */
    public List<TopLevelItem> getItems() {
        List<TopLevelItem> viewableItems = new ArrayList<TopLevelItem>();
        for (TopLevelItem item : items.values()) {
            if (item.hasPermission(ItemExt.READ)) {
                viewableItems.add(item);
            }
        }

        return viewableItems;
    }

    /**
     * Returns the read-only view of all the {@link TopLevelItem}s keyed by their names.
     * <p>
     * This method is efficient, as it doesn't involve any copying.
     *
     * @since 1.296
     */
    public Map<String, TopLevelItem> getItemMap() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Gets just the immediate children of {@link HudsonExt} but of the given type.
     */
    public <T> List<T> getItems(Class<T> type) {
        List<T> r = new ArrayList<T>();
        for (TopLevelItem i : getItems()) {
            if (type.isInstance(i)) {
                r.add(type.cast(i));
            }
        }
        return r;
    }

    /**
     * Gets all the {@link ItemExt}s recursively in the {@link ItemGroup} tree
     * and filter them by the given type.
     */
    public <T extends ItemExt> List<T> getAllItems(Class<T> type) {
        List<T> r = new ArrayList<T>();

        Stack<ItemGroup> q = new Stack<ItemGroup>();
        q.push(this);

        while (!q.isEmpty()) {
            ItemGroup<?> parent = q.pop();
            for (ItemExt i : parent.getItems()) {
                if (type.isInstance(i)) {
                    if (i.hasPermission(ItemExt.READ)) {
                        r.add(type.cast(i));
                    }
                }
                if (i instanceof ItemGroup) {
                    q.push((ItemGroup) i);
                }
            }
        }

        return r;
    }

    /**
     * Gets the list of all the projects.
     *
     * <p>
     * Since {@link ProjectExt} can only show up under {@link HudsonExt},
     * no need to search recursively.
     */
    public List<ProjectExt> getProjects() {
        return UtilExt.createSubList(items.values(), ProjectExt.class);
    }

    /**
     * Gets the names of all the {@link JobExt}s.
     */
    public Collection<String> getJobNames() {
        List<String> names = new ArrayList<String>();
        for (JobExt j : getAllItems(JobExt.class)) {
            names.add(j.getFullName());
        }
        return names;
    }

    /**
     * Gets the names of all the {@link TopLevelItem}s.
     */
    public Collection<String> getTopLevelItemNames() {
        List<String> names = new ArrayList<String>();
        for (TopLevelItem j : items.values()) {
            names.add(j.getName());
        }
        return names;
    }

    /**
     * Returns true if the current running HudsonExt is upgraded from a version earlier than the specified version.
     *
     * <p>
     * This method continues to return true until the system configuration is saved, at which point
     * {@link #version} will be overwritten and HudsonExt forgets the upgrade history.
     *
     * <p>
     * To handle SNAPSHOTS correctly, pass in "1.N.*" to test if it's upgrading from the version
     * equal or younger than N. So say if you implement a feature in 1.301 and you want to check
     * if the installation upgraded from pre-1.301, pass in "1.300.*"
     *
     * @since 1.301
     */
    public boolean isUpgradedFromBefore(VersionNumber v) {
        try {
            return new VersionNumber(version).isOlderThan(v);
        } catch (IllegalArgumentException e) {
            // fail to parse this version number
            return false;
        }
    }

    /**
     * Gets the read-only list of all {@link ComputerExt}s.
     */
    public ComputerExt[] getComputers() {
        ComputerExt[] r = computers.values().toArray(new ComputerExt[computers.size()]);
        Arrays.sort(r, new Comparator<ComputerExt>() {

            final Collator collator = Collator.getInstance();

            public int compare(ComputerExt lhs, ComputerExt rhs) {
                if (lhs.getNode() == HudsonExt.this) {
                    return -1;
                }
                if (rhs.getNode() == HudsonExt.this) {
                    return 1;
                }
                return collator.compare(lhs.getDisplayName(), rhs.getDisplayName());
            }
        });
        return r;
    }

    /*package*/ ComputerExt getComputer(NodeExt n) {
        return computers.get(n);
    }

    @CLIResolver
    public ComputerExt getComputer(@Argument(required = true, metaVar = "NAME", usage = "Node name") String name) {
        if (name.equals("(master)")) {
            name = "";
        }

        for (ComputerExt c : computers.values()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /**
     * @deprecated
     *      UI method. Not meant to be used programatically.
     */
    public ComputerSetExt getComputer() {
        return new ComputerSetExt();
    }

    /**
     * Gets the label that exists on this system by the name.
     *
     * @return null if name is null.
     * @see LabelExt#parseExpression(String) (String)
     */
    public LabelExt getLabel(String expr) {
        if (expr == null) {
            return null;
        }
        while (true) {
            LabelExt l = labels.get(expr);
            if (l != null) {
                return l;
            }

            // non-existent
            try {
                labels.putIfAbsent(expr, LabelExt.parseExpression(expr));
            } catch (ANTLRException e) {
                // laxly accept it as a single label atom for backward compatibility
                return getLabelAtom(expr);
            }
        }
    }

    /**
     * Returns the label atom of the given name.
     */
    public LabelAtomExt getLabelAtom(String name) {
        if (name == null) {
            return null;
        }

        while (true) {
            LabelExt l = labels.get(name);
            if (l != null) {
                return (LabelAtomExt) l;
            }

            // non-existent
            LabelAtomExt la = new LabelAtomExt(name);
            if (labels.putIfAbsent(name, la) == null) {
                la.load();
            }
        }
    }

    /**
     * Gets all the active labels in the current system.
     */
    public Set<LabelExt> getLabels() {
        Set<LabelExt> r = new TreeSet<LabelExt>();
        for (LabelExt l : labels.values()) {
            if (!l.isEmpty()) {
                r.add(l);
            }
        }
        return r;
    }

    public Set<LabelAtomExt> getLabelAtoms() {
        Set<LabelAtomExt> r = new TreeSet<LabelAtomExt>();
        for (LabelExt l : labels.values()) {
            if (!l.isEmpty() && l instanceof LabelAtomExt) {
                r.add((LabelAtomExt) l);
            }
        }
        return r;
    }

    public QueueExt getQueue() {
        return queue;
    }

    @Override
    public String getDisplayName() {
        return Messages.Hudson_DisplayName();
    }

    public List<JDKExt> getJDKs() {
        if (jdks == null) {
            jdks = new ArrayList<JDKExt>();
        }
        return jdks;
    }

    /**
     * Gets the JDKExt installation of the given name, or returns null.
     */
    public JDKExt getJDK(String name) {
        if (name == null) {
            // if only one JDKExt is configured, "default JDKExt" should mean that JDKExt.
            List<JDKExt> jdks = getJDKs();
            if (jdks.size() == 1) {
                return jdks.get(0);
            }
            return null;
        }
        for (JDKExt j : getJDKs()) {
            if (j.getName().equals(name)) {
                return j;
            }
        }
        return null;
    }

    /**
     * Gets the slave node of the give name, hooked under this HudsonExt.
     *
     * @deprecated
     *      Use {@link #getNode(String)}. Since 1.252.
     */
    public SlaveExt getSlave(String name) {
        NodeExt n = getNode(name);
        if (n instanceof SlaveExt) {
            return (SlaveExt) n;
        }
        return null;
    }

    /**
     * Gets the slave node of the give name, hooked under this HudsonExt.
     */
    public NodeExt getNode(String name) {
        for (NodeExt s : getNodes()) {
            if (s.getNodeName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Gets a {@link Cloud} by {@link Cloud#name its name}, or null.
     */
    public Cloud getCloud(String name) {
        return clouds.getByName(name);
    }

    /**
     * @deprecated
     *      Use {@link #getNodes()}. Since 1.252.
     */
    public List<SlaveExt> getSlaves() {
        return (List) Collections.unmodifiableList(slaves);
    }

    /**
     * Returns all {@link NodeExt}s in the system, excluding {@link HudsonExt} instance itself which
     * represents the master.
     */
    public List<NodeExt> getNodes() {
        return Collections.unmodifiableList(slaves);
    }

    /**
     * Updates the slave list.
     *
     * @deprecated
     *      Use {@link #setNodes(List)}. Since 1.252.
     */
    public void setSlaves(List<SlaveExt> slaves) throws IOException {
        setNodes(slaves);
    }

    /**
     * Adds one more {@link NodeExt} to HudsonExt.
     */
    public synchronized void addNode(NodeExt n) throws IOException {
        if (n == null) {
            throw new IllegalArgumentException();
        }
        ArrayList<NodeExt> nl = new ArrayList<NodeExt>(this.slaves);
        if (!nl.contains(n)) // defensive check
        {
            nl.add(n);
        }
        setNodes(nl);
    }

    /**
     * Removes a {@link NodeExt} from HudsonExt.
     */
    public synchronized void removeNode(NodeExt n) throws IOException {
        ComputerExt c = n.toComputer();
        if (c != null) {
            c.disconnect(OfflineCauseExt.create(Messages._Hudson_NodeBeingRemoved()));
        }

        ArrayList<NodeExt> nl = new ArrayList<NodeExt>(this.slaves);
        nl.remove(n);
        setNodes(nl);
    }

    public void setNodes(List<? extends NodeExt> nodes) throws IOException {
        // make sure that all names are unique
        Set<String> names = new HashSet<String>();
        for (NodeExt n : nodes) {
            if (!names.add(n.getNodeName())) {
                throw new IllegalArgumentException(n.getNodeName() + " is defined more than once");
            }
        }
        this.slaves = new NodeList(nodes);
        updateComputerList();
        trimLabels();
        save();
    }

    public DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        return nodeProperties;
    }

    public DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> getGlobalNodeProperties() {
        return globalNodeProperties;
    }

    /**
     * Resets all labels and remove invalid ones.
     */
    private void trimLabels() {
        for (Iterator<LabelExt> itr = labels.values().iterator(); itr.hasNext();) {
            LabelExt l = itr.next();
            l.reset();
            if (l.isEmpty()) {
                itr.remove();
            }
        }
    }

    /**
     * Binds {@link AdministrativeMonitorExt}s to URL.
     */
    public AdministrativeMonitorExt getAdministrativeMonitor(String id) {
        for (AdministrativeMonitorExt m : administrativeMonitors) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public NodeDescriptorExt getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends NodeDescriptorExt {

        @Extension
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        public String getDisplayName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

    /**
     * Gets the system default quiet period.
     */
    public int getQuietPeriod() {
        return quietPeriod != null ? quietPeriod : 5;
    }

    /**
     * Gets the global SCM check out retry count.
     */
    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount;
    }

    /**
     * @deprecated
     *      Why are you calling a method that always returns ""?
     *      Perhaps you meant {@link #getRootUrl()}.
     */
    public String getUrl() {
        return "";
    }

    @Override
    public String getSearchUrl() {
        return "";
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    public File getRootDir() {
        return root;
    }

    public FilePathExt getWorkspaceFor(TopLevelItem item) {
        return new FilePathExt(new File(item.getRootDir(), WORKSPACE_DIRNAME));
    }

    public FilePathExt getRootPath() {
        return new FilePathExt(getRootDir());
    }

    @Override
    public FilePathExt createPath(String absolutePath) {
        return new FilePathExt((VirtualChannel) null, absolutePath);
    }

    public ClockDifferenceExt getClockDifference() {
        return ClockDifferenceExt.ZERO;
    }

    /**
     * For binding {@link LogRecorderManagerExt} to "/log".
     * Everything below here is admin-only, so do the check here.
     */
    public LogRecorderManagerExt getLog() {
        checkPermission(ADMINISTER);
        return log;
    }

    /**
     * A convenience method to check if there's some security
     * restrictions in place.
     */
    public boolean isUseSecurity() {
        return securityRealm != SecurityRealmExt.NO_AUTHENTICATION || authorizationStrategy != AuthorizationStrategyExt.UNSECURED;
    }

    /**
     * If true, all the POST requests to HudsonExt would have to have crumb in it to protect
     * HudsonExt from CSRF vulnerabilities.
     */
    public boolean isUseCrumbs() {
        return crumbIssuer != null;
    }

    /**
     * Returns the constant that captures the three basic security modes
     * in HudsonExt.
     */
    public SecurityMode getSecurity() {
        // fix the variable so that this code works under concurrent modification to securityRealm.
        SecurityRealmExt realm = securityRealm;

        if (realm == SecurityRealmExt.NO_AUTHENTICATION) {
            return SecurityMode.UNSECURED;
        }
        if (realm instanceof LegacySecurityRealmExt) {
            return SecurityMode.LEGACY;
        }
        return SecurityMode.SECURED;
    }

    /**
     * @return
     *      never null.
     */
    public SecurityRealmExt getSecurityRealm() {
        return securityRealm;
    }

    public void setSecurityRealm(SecurityRealmExt securityRealm) {
        if (securityRealm == null) {
            securityRealm = SecurityRealmExt.NO_AUTHENTICATION;
        }
        this.securityRealm = securityRealm;
        // reset the filters and proxies for the new SecurityRealm
        try {
            HudsonFilter filter = HudsonFilter.get(servletContext);
            if (filter == null) {
                // Fix for #3069: This filter is not necessarily initialized before the servlets.
                // when HudsonFilter does come back, it'll initialize itself.
                LOGGER.fine("HudsonFilter has not yet been initialized: Can't perform security setup for now");
            } else {
                LOGGER.fine("HudsonFilter has been previously initialized: Setting security up");
                filter.reset(securityRealm);
                LOGGER.fine("Security is now fully set up");
            }
        } catch (ServletException e) {
            // for binary compatibility, this method cannot throw a checked exception
            throw new AcegiSecurityException("Failed to configure filter", e) {
            };
        }
    }

    public void setAuthorizationStrategy(AuthorizationStrategyExt a) {
        if (a == null) {
            a = AuthorizationStrategyExt.UNSECURED;
        }
        authorizationStrategy = a;
    }

    public Lifecycle getLifecycle() {
        return Lifecycle.get();
    }

    /**
     * Returns {@link ExtensionList} that retains the discovered instances for the given extension type.
     *
     * @param extensionType
     *      The base type that represents the extension point. Normally {@link ExtensionPoint} subtype
     *      but that's not a hard requirement.
     * @return
     *      Can be an empty list but never null.
     */
    @SuppressWarnings({"unchecked"})
    public <T> ExtensionList<T> getExtensionList(Class<T> extensionType) {
        return extensionLists.get(extensionType);
    }

    /**
     * Used to bind {@link ExtensionList}s to URLs.
     *
     * @since 1.349
     */
    public ExtensionList getExtensionList(String extensionType) throws ClassNotFoundException {
        return getExtensionList(pluginManager.uberClassLoader.loadClass(extensionType));
    }

    /**
     * Returns {@link ExtensionList} that retains the discovered {@link DescriptorExt} instances for the given
     * kind of {@link Describable}.
     *
     * @return
     *      Can be an empty list but never null.
     */
    @SuppressWarnings({"unchecked"})
    public <T extends Describable<T>, D extends DescriptorExt<T>> DescriptorExtensionListExt<T, D> getDescriptorList(Class<T> type) {
        return descriptorLists.get(type);
    }

    /**
     * Returns the root {@link ACL}.
     *
     * @see AuthorizationStrategy#getRootACL()
     */
    @Override
    public ACL getACL() {
        return authorizationStrategy.getRootACL();
    }

    /**
     * @return
     *      never null.
     */
    public AuthorizationStrategyExt getAuthorizationStrategy() {
        return authorizationStrategy;
    }

    /**
     * Returns true if HudsonExt is quieting down.
     * <p>
     * No further jobs will be executed unless it
     * can be finished while other current pending builds
     * are still in progress.
     */
    public boolean isQuietingDown() {
        return isQuietingDown;
    }

    /**
     * Returns true if the container initiated the termination of the web application.
     */
    public boolean isTerminating() {
        return terminating;
    }

    /**
     * Gets the initialization milestone that we've already reached.
     *
     * @return
     *      {@link InitMilestone#STARTED} even if the initialization hasn't been started, so that this method
     *      never returns null.
     */
    public InitMilestone getInitLevel() {
        return initLevel;
    }

    public void setNumExecutors(int n) throws IOException {
        this.numExecutors = n;
        save();
    }

    /**
     * @deprecated
     *      Left only for the compatibility of URLs.
     *      Should not be invoked for any other purpose.
     */
    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    /**
     * @deprecated
     *      Used only for mapping jobs to URL in a case-insensitive fashion.
     */
    public TopLevelItem getJobCaseInsensitive(String name) {
        String match = FunctionsExt.toEmailSafeString(name);
        for (Entry<String, TopLevelItem> e : items.entrySet()) {
            if (FunctionsExt.toEmailSafeString(e.getKey()).equalsIgnoreCase(match)) {
                TopLevelItem item = e.getValue();
                return item.hasPermission(ItemExt.READ) ? item : null;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}.
     *
     * Note that the look up is case-insensitive.
     */
    public TopLevelItem getItem(String name) {
        TopLevelItem item = items.get(name);
        if (item == null || !item.hasPermission(ItemExt.READ)) {
            return null;
        }
        return item;
    }

    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    public File getRootDirFor(String name) {
        return new File(new File(getRootDir(), "jobs"), name);
    }

    /**
     * Gets the {@link ItemExt} object by its full name.
     * Full names are like path names, where each name of {@link ItemExt} is
     * combined by '/'.
     *
     * @return
     *      null if either such {@link ItemExt} doesn't exist under the given full name,
     *      or it exists but it's no an instance of the given type.
     */
    public <T extends ItemExt> T getItemByFullName(String fullName, Class<T> type) {
        StringTokenizer tokens = new StringTokenizer(fullName, "/");
        ItemGroup parent = this;

        if (!tokens.hasMoreTokens()) {
            return null;    // for example, empty full name.
        }
        while (true) {
            ItemExt item = parent.getItem(tokens.nextToken());
            if (!tokens.hasMoreTokens()) {
                if (type.isInstance(item)) {
                    return type.cast(item);
                } else {
                    return null;
                }
            }

            if (!(item instanceof ItemGroup)) {
                return null;    // this item can't have any children
            }
            parent = (ItemGroup) item;
        }
    }

    public ItemExt getItemByFullName(String fullName) {
        return getItemByFullName(fullName, ItemExt.class);
    }

    /**
     * Gets the user of the given name.
     *
     * @return
     *      This method returns a non-null object for any user name, without validation.
     */
    public UserExt getUser(String name) {
        return UserExt.get(name);
    }

    /**
     * Creates a new job.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    public synchronized TopLevelItem createProject(TopLevelItemDescriptorExt type, String name) throws IOException {
        return createProject(type, name, true);
    }

    /**
     * Creates a new job.
     * @param type DescriptorExt for job type
     * @param name Name for job
     * @param notify Whether to fire onCreated method for all ItemListeners
     * @throws IllegalArgumentException
     *      if a project of the give name already exists.
     */
    public synchronized TopLevelItem createProject(TopLevelItemDescriptorExt type, String name, boolean notify) throws IOException {
        return itemGroupMixIn.createProject(type, name, notify);
    }

    /**
     * Overwrites the existing item by new one.
     *
     * <p>
     * This is a short cut for deleting an existing job and adding a new one.
     */
    public synchronized void putItem(TopLevelItem item) throws IOException, InterruptedException {
        String name = item.getName();
        TopLevelItem old = items.get(name);
        if (old == item) {
            return; // noop
        }
        checkPermission(ItemExt.CREATE);
        if (old != null) {
            old.delete();
        }
        items.put(name, item);
        ItemListener.fireOnCreated(item);
    }

    /**
     * Creates a new job.
     *
     * <p>
     * This version infers the descriptor from the type of the top-level item.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    public synchronized <T extends TopLevelItem> T createProject(Class<T> type, String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptorExt) getDescriptor(type), name));
    }

    /**
     * Called by {@link JobExt#renameTo(String)} to update relevant data structure.
     * assumed to be synchronized on HudsonExt by the caller.
     */
    public void onRenamed(TopLevelItem job, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, job);
    }

    /**
     * Called in response to {@link JobExt#doDoDelete(StaplerRequest, StaplerResponse)}
     */
    public void onDeleted(TopLevelItem item) throws IOException {
        for (ItemListener l : ItemListener.all()) {
            l.onDeleted(item);
        }

        items.remove(item.getName());
        save();
    }

    public FingerprintMap getFingerprintMap() {
        return fingerprintMap;
    }

    // if no finger print matches, display "not found page".
    public Object getFingerprint(String md5sum) throws IOException {
        FingerprintExt r = fingerprintMap.get(md5sum);
        if (r == null) {
            return new NoFingerprintMatch(md5sum);
        } else {
            return r;
        }
    }

    /**
     * Gets a {@link FingerprintExt} object if it exists.
     * Otherwise null.
     */
    public FingerprintExt _getFingerprint(String md5sum) throws IOException {
        return fingerprintMap.get(md5sum);
    }

    /**
     * The file we save our configuration.
     */
    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(root, "config.xml"));
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public ModeExt getMode() {
        return mode;
    }

    public String getLabelString() {
        return UtilExt.fixNull(label).trim();
    }

    @Override
    public LabelAtomExt getSelfLabel() {
        return getLabelAtom("master");
    }

    public ComputerExt createComputer() {
        return new MasterComputer();
    }

    protected synchronized TaskBuilder loadTasks() throws IOException {
        File projectsDir = new File(root, "jobs");
        if (!projectsDir.isDirectory() && !projectsDir.mkdirs()) {
            if (projectsDir.exists()) {
                throw new IOException(projectsDir + " is not a directory");
            }
            throw new IOException("Unable to create " + projectsDir + "\nPermission issue? Please create this directory manually.");
        }
        File[] subdirs = projectsDir.listFiles(new FileFilter() {

            public boolean accept(File child) {
                return child.isDirectory() && Items.getConfigFile(child).exists();
            }
        });

        TaskGraphBuilder g = new TaskGraphBuilder();
        Handle loadHudson = g.requires(EXTENSIONS_AUGMENTED).attains(JOB_LOADED).add("Loading global config", new Executable() {

            public void run(Reactor session) throws Exception {
                XmlFile cfg = getConfigFile();
                if (cfg.exists()) {
                    // reset some data that may not exist in the disk file
                    // so that we can take a proper compensation action later.
                    primaryView = null;

                    // load from disk
                    cfg.unmarshal(HudsonExt.this);
                }

                // if we are loading old data that doesn't have this field
                if (slaves == null) {
                    slaves = new NodeList();
                }

                clouds.setOwner(HudsonExt.this);
                items.clear();
            }
        });

        for (final File subdir : subdirs) {
            g.requires(loadHudson).attains(JOB_LOADED).notFatal().add("Loading job " + subdir.getName(), new Executable() {

                public void run(Reactor session) throws Exception {
                    TopLevelItem item = (TopLevelItem) Items.load(HudsonExt.this, subdir);
                    items.put(item.getName(), item);
                }
            });
        }

        g.requires(JOB_LOADED).add("Finalizing set up", new Executable() {

            public void run(Reactor session) throws Exception {
                rebuildDependencyGraph();

                {// recompute label objects - populates the labels mapping.
                    for (NodeExt slave : slaves) // Note that not all labels are visible until the slaves have connected.
                    {
                        slave.getAssignedLabels();
                    }
                    getAssignedLabels();
                }

                // read in old data that doesn't have the security field set
                if (authorizationStrategy == null) {
                    if (useSecurity == null || !useSecurity) {
                        authorizationStrategy = AuthorizationStrategyExt.UNSECURED;
                    } else {
                        authorizationStrategy = new LegacyAuthorizationStrategyExt();
                    }
                }
                if (securityRealm == null) {
                    if (useSecurity == null || !useSecurity) {
                        setSecurityRealm(SecurityRealmExt.NO_AUTHENTICATION);
                    } else {
                        setSecurityRealm(new LegacySecurityRealmExt());
                    }
                } else {
                    // force the set to proxy
                    setSecurityRealm(securityRealm);
                }

                if (useSecurity != null && !useSecurity) {
                    // forced reset to the unsecure mode.
                    // this works as an escape hatch for people who locked themselves out.
                    authorizationStrategy = AuthorizationStrategyExt.UNSECURED;
                    setSecurityRealm(SecurityRealmExt.NO_AUTHENTICATION);
                }

                // Initialize the filter with the crumb issuer
                setCrumbIssuer(crumbIssuer);

                // auto register root actions
                for (Action a : getExtensionList(RootAction.class)) {
                    if (!actions.contains(a)) {
                        actions.add(a);
                    }
                }
            }
        });

        return g;
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    /**
     * Called to shut down the system.
     */
    public void cleanUp() {
        Set<Future<?>> pending = new HashSet<Future<?>>();
        terminating = true;
        for (ComputerExt c : computers.values()) {
            c.interrupt();
            c.kill();
            pending.add(c.disconnect(null));
        }
        if (udpBroadcastThread != null) {
            udpBroadcastThread.shutdown();
        }

        ExternalJobExt.reloadThread.interrupt();
        Trigger.timer.cancel();
        // TODO: how to wait for the completion of the last job?
        Trigger.timer = null;
        if (tcpSlaveAgentListener != null) {
            tcpSlaveAgentListener.shutdown();
        }

        if (pluginManager != null) // be defensive. there could be some ugly timing related issues
        {
            pluginManager.stop();
        }

        if (getRootDir().exists()) // if we are aborting because we failed to create HUDSON_HOME,
        // don't try to save. Issue #536
        {
            getQueue().save();
        }

        threadPoolForLoad.shutdown();
        for (Future<?> f : pending) {
            try {
                f.get(10, TimeUnit.SECONDS);    // if clean up operation didn't complete in time, we fail the test
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;  // someone wants us to die now. quick!
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down properly", e);
            } catch (TimeoutException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down properly", e);
            }
        }

        LogFactory.releaseAll();

        theInstance = null;
    }

    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            if (a.getUrlName().equals(token) || a.getUrlName().equals('/' + token)) {
                return a;
            }
        }
        for (Action a : getManagementLinks()) {
            if (a.getUrlName().equals(token)) {
                return a;
            }
        }
        return null;
    }

    public CrumbIssuerExt getCrumbIssuer() {
        return crumbIssuer;
    }

    public void setCrumbIssuer(CrumbIssuerExt issuer) {
        crumbIssuer = issuer;
    }

    /**
     * Creates a new job from its configuration XML. The type of the job created will be determined by
     * what's in this XML.
     * @since 1.319
     */
    public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        return itemGroupMixIn.createProjectFromXML(name, xml);
    }

    /**
     * Copys a job.
     *
     * @param src
     *      A {@link TopLevelItem} to be copied.
     * @param name
     *      Name of the newly created project.
     * @return
     *      Newly created {@link TopLevelItem}.
     */
    @SuppressWarnings({"unchecked"})
    public <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        return itemGroupMixIn.copy(src, name);
    }

    // a little more convenient overloading that assumes the caller gives us the right type
    // (or else it will fail with ClassCastException)
    public <T extends AbstractProjectExt<?, ?>> T copy(T src, String name) throws IOException {
        return (T) copy((TopLevelItem) src, name);
    }

    /**
     * Check if the given name is suitable as a name
     * for job, view, etc.
     *
     * @throws ParseException
     *      if the given name is not good
     */
    public static void checkGoodName(String name) throws FailureExt {
        if (name == null || name.length() == 0) {
            throw new FailureExt(Messages.Hudson_NoName());
        }

        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new FailureExt(Messages.Hudson_ControlCodeNotAllowed(toPrintableName(name)));
            }
            if ("?*/\\%!@#$^&|<>[]:;".indexOf(ch) != -1) {
                throw new FailureExt(Messages.Hudson_UnsafeChar(ch));
            }
        }

        // looks good
    }

    /**
     * Makes sure that the given name is good as a job name.
     * @return trimmed name if valid; throws ParseException if not
     */
    protected String checkJobName(String name) throws FailureExt {
        checkGoodName(name);
        name = name.trim();
        if (getItem(name) != null) {
            throw new FailureExt(Messages.Hudson_JobAlreadyExists(name));
        }
        // looks good
        return name;
    }

    private static String toPrintableName(String name) {
        StringBuilder printableName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                printableName.append("\\u").append((int) ch).append(';');
            } else {
                printableName.append(ch);
            }
        }
        return printableName.toString();
    }

    /**
     * Serves jar files for JNLP slave agents.
     */
    public SlaveExt.JnlpJar getJnlpJars(String fileName) {
        return new SlaveExt.JnlpJar(fileName);
    }

    /**
     * Reloads the configuration synchronously.
     */
    public void reload() throws IOException, InterruptedException, ReactorException {
        executeReactor(null, loadTasks());
        UserExt.reload();
        servletContext.setAttribute("app", this);
    }

    /**
     * Obtains the heap dump.
     */
    public HeapDumpExt getHeapDump() throws IOException {
        return new HeapDumpExt(this, MasterComputer.localChannel);
    }

    /**
     * Simulates OutOfMemoryError.
     * Useful to make sure OutOfMemoryHeapDump setting.
     */
    public void doSimulateOutOfMemory() throws IOException {
        checkPermission(ADMINISTER);

        System.out.println("Creating artificial OutOfMemoryError situation");
        List<Object> args = new ArrayList<Object>();
        while (true) {
            args.add(new byte[1024 * 1024]);
        }
    }

    /**
     * Binds /userContent/... to $HUDSON_HOME/userContent.
     */
    public DirectoryBrowserSupportExt doUserContent() {
        return new DirectoryBrowserSupportExt(this, getRootPath().child("userContent"), "User content", "folder.gif", true);
    }

    /**
     * Gets the absolute URL of Hudson,
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
     * @see Descriptor#getCheckUrl(String)
     * @see #getRootUrlFromRequest()
     */
    public String getRootUrl() {

        // for compatibility. the actual data is stored in Mailer
        String url = MailerExt.descriptor().getUrl();
        if (url != null) {
            return url;
        }
        //Return the root URL set else where
        return rootUrl;
    }

    /**
     * Set the root URL. Use this, if root URL is not explicitly set by User 
     * via Mailer interface
     * @param url 
     */
    public void setRootUrl(String url) {
        rootUrl = url;
    }

    /**
     * Performs a restart.
     */
    public void restart() throws RestartNotSupportedException {
        final Lifecycle lifecycle = Lifecycle.get();
        lifecycle.verifyRestartable(); // verify that HudsonExt is restartable

        new Thread("restart thread") {

            final String exitUser = getAuthentication().getName();

            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

                    // give some time for the browser to load the "reloading" page
                    Thread.sleep(5000);
                    LOGGER.severe(String.format("Restarting VM as requested by %s", exitUser));
                    for (RestartListener listener : RestartListener.all()) {
                        listener.onRestart();
                    }
                    lifecycle.restart();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson", e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson", e);
                }
            }
        }.start();
    }

    /**
     * Gets the {@link Authentication} object that represents the user
     * associated with the current request.
     */
    public static Authentication getAuthentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        // on Tomcat while serving the login page, this is null despite the fact
        // that we have filters. Looking at the stack trace, Tomcat doesn't seem to
        // run the request through filters when this is the login request.
        // see http://www.nabble.com/Matrix-authorization-problem-tp14602081p14886312.html
        if (a == null) {
            a = ANONYMOUS;
        }
        return a;
    }
    /**
     * Extension list that {@link #doResources(StaplerRequest, StaplerResponse)} can serve.
     * This set is mutable to allow plugins to add additional extensions.
     */
    public static final Set<String> ALLOWED_RESOURCE_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "js|css|jpeg|jpg|png|gif|html|htm".split("\\|")));

    /**
     * Does not check when system default encoding is "ISO-8859-1".
     */
    public static boolean isCheckURIEncodingEnabled() {
        return !"ISO-8859-1".equalsIgnoreCase(System.getProperty("file.encoding"));
    }

    /**
     * @deprecated
     *      Use {@link FunctionsExt#isWindows()}.
     */
    public static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    public static boolean isDarwin() {
        // according to http://developer.apple.com/technotes/tn2002/tn2110.html
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac");
    }

    /**
     * Rebuilds the dependency map.
     */
    public void rebuildDependencyGraph() {
        dependencyGraph = new DependencyGraph();
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    // for Jelly
    public List<ManagementLink> getManagementLinks() {
        return ManagementLink.all();
    }

    /**
     * Exposes the current user to <tt>/me</tt> URL.
     */
    public UserExt getMe() {
        UserExt u = UserExt.current();
        if (u == null) {
            throw new AccessDeniedException("/me is not available when not logged in");
        }
        return u;
    }

    public static final class MasterComputer extends ComputerExt {

        private MasterComputer() {
            super(HudsonExt.getInstance());
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

        public RetentionStrategyExt getRetentionStrategy() {
            return RetentionStrategyExt.NOOP;
        }

        @Override
        public boolean hasPermission(Permission permission) {
            // no one should be allowed to delete the master.
            // this hides the "delete" link from the /computer/(master) page.
            if (permission == ComputerExt.DELETE) {
                return false;
            }
            // Configuration of master node requires ADMINISTER permission
            return super.hasPermission(permission == ComputerExt.CONFIGURE ? HudsonExt.ADMINISTER : permission);
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

        protected Future<?> _connect(boolean forceReconnect) {
            return Futures.precomputed(null);
        }
        /**
         * {@link LocalChannel} instance that can be used to execute programs locally.
         */
        public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);
    }

    /**
     * Shortcut for {@code HudsonExt.getInstance().lookup.get(type)}
     */
    public static <T> T lookup(Class<T> type) {
        return HudsonExt.getInstance().lookup.get(type);
    }

    /**
     * Checks if the current user (for which we are processing the current request)
     * has the admin access.
     *
     * @deprecated since 2007-12-18.
     *      This method is deprecated when HudsonExt moved from simple Unix root-like model
     *      of "admin gets to do everything, and others don't have any privilege" to more
     *      complex {@link ACL} and {@link Permission} based scheme.
     *
     *      <p>
     *      For a quick migration, use {@code HudsonExt.getInstance().getACL().hasPermission(HudsonExt.ADMINISTER)}
     *      To check if the user has the 'administer' role in HudsonExt.
     *
     *      <p>
     *      But ideally, your plugin should first identify a suitable {@link Permission} (or create one,
     *      if appropriate), then identify a suitable {@link AccessControlled} object to check its permission
     *      against.
     */
    public static boolean isAdmin() {
        return HudsonExt.getInstance().getACL().hasPermission(ADMINISTER);
    }
    /**
     * Live view of recent {@link LogRecord}s produced by HudsonExt.
     */
    public static List<LogRecord> logRecords = Collections.emptyList(); // initialized to dummy value to avoid NPE
    /**
     * Thread-safe reusable {@link XStream}.
     */
    public static final XStream XSTREAM = new XStream2();
    private static final int TWICE_CPU_NUM = Runtime.getRuntime().availableProcessors() * 2;
    /**
     * Thread pool used to load configuration in parallel, to improve the start up time.
     * <p>
     * The idea here is to overlap the CPU and I/O, so we want more threads than CPU numbers.
     */
    /*package*/ transient final ExecutorService threadPoolForLoad = new ThreadPoolExecutor(
            TWICE_CPU_NUM, TWICE_CPU_NUM,
            5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());

    private static void computeVersion(ServletContext context) {
        // set the version
        Properties props = new Properties();
        try {
            InputStream is = HudsonExt.class.getResourceAsStream("hudson-version.properties");
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace(); // if the version properties is missing, that's OK.
        }
        String ver = props.getProperty("version");
        if (ver == null) {
            ver = "?";
        }
        VERSION = ver;
        context.setAttribute("version", ver);
        VERSION_HASH = UtilExt.getDigestOf(ver).substring(0, 8);

        if (ver.equals("?") || Boolean.getBoolean("hudson.script.noCache")) {
            RESOURCE_PATH = "";
        } else {
            RESOURCE_PATH = "/static/" + VERSION_HASH;
        }

        VIEW_RESOURCE_PATH = "/resources/" + VERSION_HASH;
    }
    /**
     * Version number of this HudsonExt.
     */
    public static String VERSION = "?";

    /**
     * Parses {@link #VERSION} into {@link VersionNumber}, or null if it's not parseable as a version number
     * (such as when HudsonExt is run with "mvn hudson-dev:run")
     */
    public static VersionNumber getVersion() {
        try {
            return new VersionNumber(VERSION);
        } catch (NumberFormatException e) {
            try {
                // for non-released version of HudsonExt, this looks like "1.345 (private-foobar), so try to approximate.
                int idx = VERSION.indexOf(' ');
                if (idx > 0) {
                    return new VersionNumber(VERSION.substring(0, idx));
                }
            } catch (NumberFormatException _) {
                // fall through
            }

            // totally unparseable
            return null;
        } catch (IllegalArgumentException e) {
            // totally unparseable
            return null;
        }
    }
    /**
     * Hash of {@link #VERSION}.
     */
    public static String VERSION_HASH;
    /**
     * Prefix to static resources like images and javascripts in the war file.
     * Either "" or strings like "/static/VERSION", which avoids HudsonExt to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    public static String RESOURCE_PATH = "";
    /**
     * Prefix to resources alongside view scripts.
     * Strings like "/resources/VERSION", which avoids HudsonExt to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    public static String VIEW_RESOURCE_PATH = "/resources/TBD";
    public static boolean PARALLEL_LOAD = !"false".equals(System.getProperty(HudsonExt.class.getName() + ".parallelLoad"));
    public static boolean KILL_AFTER_LOAD = Boolean.getBoolean(HudsonExt.class.getName() + ".killAfterLoad");
    public static boolean LOG_STARTUP_PERFORMANCE = Boolean.getBoolean(HudsonExt.class.getName() + ".logStartupPerformance");
    private static final boolean CONSISTENT_HASH = true; // Boolean.getBoolean(HudsonExt.class.getName()+".consistentHash");
    /**
     * Enabled by default as of 1.337. Will keep it for a while just in case we have some serious problems.
     */
    public static boolean FLYWEIGHT_SUPPORT = !"false".equals(System.getProperty(HudsonExt.class.getName() + ".flyweightSupport"));
    /**
     * Tentative switch to activate the concurrent build behavior.
     * When we merge this back to the trunk, this allows us to keep
     * this feature hidden for a while until we iron out the kinks.
     * @see AbstractProjectExt#isConcurrentBuild()
     */
    public static boolean CONCURRENT_BUILD = true;
    /**
     * Switch to enable people to use a shorter workspace name.
     */
    private static final String WORKSPACE_DIRNAME = System.getProperty(HudsonExt.class.getName() + ".workspaceDirName", "workspace");
    /**
     * Automatically try to launch a slave when HudsonExt is initialized or a new slave is created.
     */
    public static boolean AUTOMATIC_SLAVE_LAUNCH = true;
    private static final Logger LOGGER = Logger.getLogger(HudsonExt.class.getName());
    protected static final Pattern ICON_SIZE = Pattern.compile("\\d+x\\d+");
    public static final PermissionGroup PERMISSIONS = Permission.HUDSON_PERMISSIONS;
    public static final Permission ADMINISTER = Permission.HUDSON_ADMINISTER;
    public static final Permission READ = new Permission(PERMISSIONS, "Read", Messages._Hudson_ReadPermission_Description(), Permission.READ);
    /**
     * {@link Authentication} object that represents the anonymous user.
     * Because Acegi creates its own {@link AnonymousAuthenticationToken} instances, the code must not
     * expect the singleton semantics. This is just a convenient instance.
     *
     * @since 1.343
     */
    public static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "anonymous", "anonymous", new GrantedAuthority[]{new GrantedAuthorityImpl("anonymous")});

    static {
        XSTREAM.alias("hudson", HudsonExt.class);
        XSTREAM.alias("slave", DumbSlaveExt.class);
        XSTREAM.alias("jdk", JDKExt.class);
        
        // this seems to be necessary to force registration of converter early enough
        ModeExt.class.getEnumConstants();

        // double check that initialization order didn't do any harm
        assert PERMISSIONS != null;
        assert ADMINISTER != null;
    }
}

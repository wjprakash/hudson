/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, Tom Huybrechts, Winston Prakash
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
package hudson;

import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import static hudson.init.InitMilestone.PLUGINS_LISTED;

import hudson.PluginWrapperExt.Dependency;
import hudson.init.InitStrategy;
import hudson.init.InitializerFinder;
import hudson.model.AbstractModelObjectExt;
import hudson.model.HudsonExt;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.Service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages {@link PluginWrapperExt}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class PluginManagerExt extends AbstractModelObjectExt {
    /**
     * All discovered plugins.
     */
    protected final List<PluginWrapperExt> plugins = new ArrayList<PluginWrapperExt>();

    /**
     * All active plugins.
     */
    protected final List<PluginWrapperExt> activePlugins = new ArrayList<PluginWrapperExt>();

    protected final List<FailedPlugin> failedPlugins = new ArrayList<FailedPlugin>();

    /**
     * Plug-in root directory.
     */
    public final File rootDir;

    /**
     * @deprecated as of 1.355
     *      {@link PluginManagerExt} can now live longer than {@link HudsonExt} instance, so
     *      use {@code HudsonExt.getInstance().servletContext} instead.
     */
    public final ServletContext context;

    /**
     * {@link ClassLoader} that can load all the publicly visible classes from plugins
     * (and including the classloader that loads HudsonExt itself.)
     *
     */
    // implementation is minimal --- just enough to run XStream
    // and load plugin-contributed classes.
    public final ClassLoader uberClassLoader = new UberClassLoader();

    /**
     * Once plugin is uploaded, this flag becomes true.
     * This is used to report a message that HudsonExt needs to be restarted
     * for new plugins to take effect.
     */
    public volatile boolean pluginUploaded = false;

    /**
     * The initialization of {@link PluginManagerExt} splits into two parts;
     * one is the part about listing them, extracting them, and preparing classloader for them.
     * The 2nd part is about creating instances. Once the former completes this flags become true,
     * as the 2nd part can be repeated for each HudsonExt instance.
     */
    private boolean pluginListed = false;
    
    /**
     * Strategy for creating and initializing plugins
     */
    private final PluginStrategy strategy;

    public PluginManagerExt(ServletContext context, File rootDir) {
        this.context = context;

        this.rootDir = rootDir;
        if(!rootDir.exists())
            rootDir.mkdirs();
        
        strategy = createPluginStrategy();
    }

    /**
     * Called immediately after the construction.
     * This is a separate method so that code executed from here will see a valid value in
     * {@link HudsonExt#pluginManager}. 
     */
    public TaskBuilder initTasks(final InitStrategy initStrategy) {
        TaskBuilder builder;
        if (!pluginListed) {
            builder = new TaskGraphBuilder() {
                List<File> archives;
                Collection<String> bundledPlugins;

                {
                    Handle loadBundledPlugins = add("Loading bundled plugins", new Executable() {
                        public void run(Reactor session) throws Exception {
                            bundledPlugins = loadBundledPlugins();
                        }
                    });

                    Handle listUpPlugins = requires(loadBundledPlugins).add("Listing up plugins", new Executable() {
                        public void run(Reactor session) throws Exception {
                            archives = initStrategy.listPluginArchives(PluginManagerExt.this);
                        }
                    });

                    requires(listUpPlugins).attains(PLUGINS_LISTED).add("Preparing plugins",new Executable() {
                        public void run(Reactor session) throws Exception {
                            // once we've listed plugins, we can fill in the reactor with plugin-specific initialization tasks
                            TaskGraphBuilder g = new TaskGraphBuilder();

                            final Map<String,File> inspectedShortNames = new HashMap<String,File>();

                            for( final File arc : archives ) {
                                g.followedBy().notFatal().attains(PLUGINS_LISTED).add("Inspecting plugin " + arc, new Executable() {
                                    public void run(Reactor session1) throws Exception {
                                        try {
                                            PluginWrapperExt p = strategy.createPluginWrapper(arc);
                                            if (isDuplicate(p)) return;

                                            p.isBundled = bundledPlugins.contains(arc.getName());
                                            plugins.add(p);
                                            if(p.isActive())
                                                activePlugins.add(p);
                                        } catch (IOException e) {
                                            failedPlugins.add(new FailedPlugin(arc.getName(),e));
                                            throw e;
                                        }
                                    }

                                    /**
                                     * Inspects duplication. this happens when you run hpi:run on a bundled plugin,
                                     * as well as putting numbered hpi files, like "cobertura-1.0.hpi" and "cobertura-1.1.hpi"
                                     */
                                    private boolean isDuplicate(PluginWrapperExt p) {
                                        String shortName = p.getShortName();
                                        if (inspectedShortNames.containsKey(shortName)) {
                                            LOGGER.info("Ignoring "+arc+" because "+inspectedShortNames.get(shortName)+" is already loaded");
                                            return true;
                                        }

                                        inspectedShortNames.put(shortName,arc);
                                        return false;
                                    }
                                });
                            }

                            g.requires(PLUGINS_PREPARED).add("Checking cyclic dependencies",new Executable() {
                                /**
                                 * Makes sure there's no cycle in dependencies.
                                 */
                                public void run(Reactor reactor) throws Exception {
                                    try {
                                        new CyclicGraphDetector<PluginWrapperExt>() {
                                            @Override
                                            protected List<PluginWrapperExt> getEdges(PluginWrapperExt p) {
                                                List<PluginWrapperExt> next = new ArrayList<PluginWrapperExt>();
                                                addTo(p.getDependencies(),next);
                                                addTo(p.getOptionalDependencies(),next);
                                                return next;
                                            }

                                            private void addTo(List<Dependency> dependencies, List<PluginWrapperExt> r) {
                                                for (Dependency d : dependencies) {
                                                    PluginWrapperExt p = getPlugin(d.shortName);
                                                    if (p!=null)
                                                        r.add(p);
                                                }
                                            }
                                        }.run(getPlugins());
                                    } catch (CycleDetectedException e) {
                                        stop(); // disable all plugins since classloading from them can lead to StackOverflow
                                        throw e;    // let HudsonExt fail
                                    }
                                    Collections.sort(plugins);
                                }
                            });

                            session.addAll(g.discoverTasks(session));

                            pluginListed = true; // technically speaking this is still too early, as at this point tasks are merely scheduled, not necessarily executed.
                        }
                    });
                }
            };
        } else {
            builder = TaskBuilder.EMPTY_BUILDER;
        }

        final InitializerFinder initializerFinder = new InitializerFinder(uberClassLoader);        // misc. stuff

        // lists up initialization tasks about loading plugins.
        return TaskBuilder.union(initializerFinder, // this scans @Initializer in the core once
            builder,new TaskGraphBuilder() {{
            requires(PLUGINS_LISTED).attains(PLUGINS_PREPARED).add("Loading plugins",new Executable() {
                /**
                 * Once the plugins are listed, schedule their initialization.
                 */
                public void run(Reactor session) throws Exception {
                    HudsonExt.getInstance().lookup.set(PluginInstanceStore.class,new PluginInstanceStore());
                    TaskGraphBuilder g = new TaskGraphBuilder();

                    // schedule execution of loading plugins
                    for (final PluginWrapperExt p : activePlugins.toArray(new PluginWrapperExt[activePlugins.size()])) {
                        g.followedBy().notFatal().attains(PLUGINS_PREPARED).add("Loading plugin " + p.getShortName(), new Executable() {
                            public void run(Reactor session) throws Exception {
                                try {
                                    p.resolvePluginDependencies();
                                    strategy.load(p);
                                } catch (IOException e) {
                                    failedPlugins.add(new FailedPlugin(p.getShortName(), e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    throw e;
                                }
                            }
                        });
                    }

                    // schedule execution of initializing plugins
                    for (final PluginWrapperExt p : activePlugins.toArray(new PluginWrapperExt[activePlugins.size()])) {
                        g.followedBy().notFatal().attains(PLUGINS_STARTED).add("Initializing plugin " + p.getShortName(), new Executable() {
                            public void run(Reactor session) throws Exception {
                                try {
                                    p.getPlugin().postInitialize();
                                } catch (Exception e) {
                                    failedPlugins.add(new FailedPlugin(p.getShortName(), e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    throw e;
                                }
                            }
                        });
                    }

                    g.followedBy().attains(PLUGINS_STARTED).add("Discovering plugin initialization tasks", new Executable() {
                        public void run(Reactor reactor) throws Exception {
                            // rescan to find plugin-contributed @Initializer
                            reactor.addAll(initializerFinder.discoverTasks(reactor));
                        }
                    });

                    // register them all
                    session.addAll(g.discoverTasks(session));
                }
            });
        }});
    }

    /**
     * If the war file has any "/WEB-INF/plugins/*.hpi", extract them into the plugin directory.
     *
     * @return
     *      File names of the bundled plugins. Like {"ssh-slaves.hpi","subvesrion.hpi"}
     * @throws Exception
     *      Any exception will be reported and halt the startup.
     */
    protected abstract Collection<String> loadBundledPlugins() throws Exception;

    /**
     * Copies the bundled plugin from the given URL to the destination of the given file name (like 'abc.hpi'),
     * with a reasonable up-to-date check. A convenience method to be used by the {@link #loadBundledPlugins()}.
     */
    protected void copyBundledPlugin(URL src, String fileName) throws IOException {
        long lastModified = src.openConnection().getLastModified();
        File file = new File(rootDir, fileName);
        File pinFile = new File(rootDir, fileName+".pinned");

        // update file if:
        //  - no file exists today
        //  - bundled version and current version differs (by timestamp), and the file isn't pinned.
        if (!file.exists() || (file.lastModified() != lastModified && !pinFile.exists())) {
            FileUtils.copyURLToFile(src, file);
            file.setLastModified(src.openConnection().getLastModified());
            // lastModified is set for two reasons:
            // - to avoid unpacking as much as possible, but still do it on both upgrade and downgrade
            // - to make sure the value is not changed after each restart, so we can avoid
            // unpacking the plugin itself in ClassicPluginStrategy.explode
        }
    }

    /**
     * Creates a hudson.PluginStrategy, looking at the corresponding system property. 
     */
    private PluginStrategy createPluginStrategy() {
        // Allow the user to specify a the plugin strategy to use with a system property
        String strategyName = System.getProperty(PluginStrategy.class.getName());

        if (strategyName == null) {
            // Now let's check the default that's been set in the hudson-core.properties file
            try {
                Properties hudsonCoreProperties = new Properties();
                hudsonCoreProperties.load(getClass().getResourceAsStream("/hudson/hudson-core.properties"));
                strategyName = hudsonCoreProperties.getProperty("hudson.PluginStrategy");
            } catch (IOException ex) {
                LOGGER.info("Plugin Manager: " + ex.getLocalizedMessage());
            }
        }

        if (strategyName != null) {
            try {
                Class<?> klazz = getClass().getClassLoader().loadClass(strategyName);
                Object strategy = klazz.getConstructor(PluginManagerExt.class).newInstance(this);
                if (strategy instanceof PluginStrategy) {
                    LOGGER.info("Plugin strategy: " + strategyName);
                    return (PluginStrategy) strategy;
                } else {
                    LOGGER.warning("Plugin strategy (" + strategyName
                            + ") is not an instance of hudson.PluginStrategy");
                }
            } catch (ClassNotFoundException e) {
                LOGGER.warning("Plugin strategy class not found: "
                        + strategyName);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not instantiate plugin strategy: "
                        + strategyName + ". Falling back to ClassicPluginStrategy", e);
            }
            LOGGER.info("Falling back to ClassicPluginStrategy");
        }

        // default and fallback
        return new ClassicPluginStrategy(this);
    }

    public PluginStrategy getPluginStrategy() {
        return strategy;
    }

    /**
     * Returns true if any new plugin was added, which means a restart is required
     * for the change to take effect.
     */
    public boolean isPluginUploaded() {
        return pluginUploaded;
    }
    
    public List<PluginWrapperExt> getPlugins() {
        return plugins;
    }

    public List<FailedPlugin> getFailedPlugins() {
        return failedPlugins;
    }

    public PluginWrapperExt getPlugin(String shortName) {
        for (PluginWrapperExt p : plugins) {
            if(p.getShortName().equals(shortName))
                return p;
        }
        return null;
    }

    /**
     * Get the plugin instance that implements a specific class, use to find your plugin singleton.
     * Note: beware the classloader fun.
     * @param pluginClazz The class that your plugin implements.
     * @return The plugin singleton or <code>null</code> if for some reason the plugin is not loaded.
     */
    public PluginWrapperExt getPlugin(Class<? extends PluginExt> pluginClazz) {
        for (PluginWrapperExt p : plugins) {
            if(pluginClazz.isInstance(p.getPlugin()))
                return p;
        }
        return null;
    }

    /**
     * Get the plugin instances that extend a specific class, use to find similar plugins.
     * Note: beware the classloader fun.
     * @param pluginSuperclass The class that your plugin is derived from.
     * @return The list of plugins implementing the specified class.
     */
    public List<PluginWrapperExt> getPlugins(Class<? extends PluginExt> pluginSuperclass) {
        List<PluginWrapperExt> result = new ArrayList<PluginWrapperExt>();
        for (PluginWrapperExt p : plugins) {
            if(pluginSuperclass.isInstance(p.getPlugin()))
                result.add(p);
        }
        return Collections.unmodifiableList(result);
    }

    public String getDisplayName() {
        return Messages.PluginManager_DisplayName();
    }

    public String getSearchUrl() {
        return "pluginManager";
    }

    /**
     * Discover all the service provider implementations of the given class,
     * via <tt>META-INF/services</tt>.
     */
    public <T> Collection<Class<? extends T>> discover( Class<T> spi ) {
        Set<Class<? extends T>> result = new HashSet<Class<? extends T>>();

        for (PluginWrapperExt p : activePlugins) {
            Service.load(spi, p.classLoader, result);
        }

        return result;
    }

    /**
     * Orderly terminates all the plugins.
     */
    public void stop() {
        for (PluginWrapperExt p : activePlugins) {
            p.stop();
            p.releaseClassLoader();
        }
        activePlugins.clear();
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(uberClassLoader);
    }

     

    /**
     * {@link ClassLoader} that can see all plugins.
     */
    public final class UberClassLoader extends ClassLoader {
        /**
         * Make generated types visible.
         * Keyed by the generated class name.
         */
        private ConcurrentMap<String, WeakReference<Class>> generatedClasses = new ConcurrentHashMap<String, WeakReference<Class>>();

        public UberClassLoader() {
            super(PluginManagerExt.class.getClassLoader());
        }

        public void addNamedClass(String className, Class c) {
            generatedClasses.put(className,new WeakReference<Class>(c));
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            WeakReference<Class> wc = generatedClasses.get(name);
            if (wc!=null) {
                Class c = wc.get();
                if (c!=null)    return c;
                else            generatedClasses.remove(name,wc);
            }

            // first, use the context classloader so that plugins that are loading
            // can use its own classloader first.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if(cl!=null && cl!=this)
                try {
                    return cl.loadClass(name);
                } catch(ClassNotFoundException e) {
                    // not found. try next
                }

            for (PluginWrapperExt p : activePlugins) {
                try {
                    return p.classLoader.loadClass(name);
                } catch (ClassNotFoundException e) {
                    //not found. try next
                }
            }
            // not found in any of the classloader. delegate.
            throw new ClassNotFoundException(name);
        }

        @Override
        protected URL findResource(String name) {
            for (PluginWrapperExt p : activePlugins) {
                URL url = p.classLoader.getResource(name);
                if(url!=null)
                    return url;
            }
            return null;
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            List<URL> resources = new ArrayList<URL>();
            for (PluginWrapperExt p : activePlugins) {
                resources.addAll(Collections.list(p.classLoader.getResources(name)));
            }
            return Collections.enumeration(resources);
        }

        @Override
        public String toString()
        {
            // only for debugging purpose
            return "classLoader " +  getClass().getName();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PluginManagerExt.class.getName());

    /**
     * Remembers why a plugin failed to deploy.
     */
    public static final class FailedPlugin {
        public final String name;
        public final Exception cause;

        public FailedPlugin(String name, Exception cause) {
            this.name = name;
            this.cause = cause;
        }

        public String getExceptionString() {
            return FunctionsExt.printThrowable(cause);
        }
    }

    /**
     * Stores {@link PluginExt} instances.
     */
    /*package*/ static final class PluginInstanceStore {
        final Map<PluginWrapperExt,PluginExt> store = new Hashtable<PluginWrapperExt,PluginExt>();
    }
}

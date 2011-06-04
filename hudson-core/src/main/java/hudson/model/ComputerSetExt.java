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

import hudson.DescriptorExtensionListExt;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.node_monitors.NodeMonitorExt;
import hudson.util.DescribableListExt;

import java.io.File;
import java.io.IOException;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves as the top of {@link ComputerExt}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 *
 * @author Kohsuke Kawaguchi
 */
public class ComputerSetExt extends AbstractModelObjectExt {
    /**
     * This is the owner that persists {@link #monitors}.
     */
    protected static final Saveable MONITORS_OWNER = new Saveable() {
        public void save() throws IOException {
            getConfigFile().write(monitors);
            SaveableListener.fireOnChange(this, getConfigFile());
        }
    };

    protected static final DescribableListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>> monitors
            = new DescribableListExt<NodeMonitorExt, DescriptorExt<NodeMonitorExt>>(MONITORS_OWNER);

    public String getDisplayName() {
        return Messages.ComputerSet_DisplayName();
    }

    /**
     * @deprecated as of 1.301
     *      Use {@link #getMonitors()}.
     */
    public static List<NodeMonitorExt> get_monitors() {
        return monitors.toList();
    }

    public ComputerExt[] get_all() {
        return HudsonExt.getInstance().getComputers();
    }

    /**
     * Exposing {@link NodeMonitor#all()} for Jelly binding.
     */
    public DescriptorExtensionListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>> getNodeMonitorDescriptors() {
        return NodeMonitorExt.all();
    }

    public static DescribableListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>> getMonitors() {
        return monitors;
    }

    /**
     * Returns a subset pf {@link #getMonitors()} that are {@linkplain NodeMonitor#isIgnored() not ignored}.
     */
    public static Map<DescriptorExt<NodeMonitorExt>,NodeMonitorExt> getNonIgnoredMonitors() {
        Map<DescriptorExt<NodeMonitorExt>,NodeMonitorExt> r = new HashMap<DescriptorExt<NodeMonitorExt>, NodeMonitorExt>();
        for (NodeMonitorExt m : monitors) {
            if(!m.isIgnored())
                r.put(m.getDescriptor(),m);
        }
        return r;
    }

    /**
     * Gets all the slave names.
     */
    public List<String> get_slaveNames() {
        return new AbstractList<String>() {
            final List<NodeExt> nodes = HudsonExt.getInstance().getNodes();

            public String get(int index) {
                return nodes.get(index).getNodeName();
            }

            public int size() {
                return nodes.size();
            }
        };
    }

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    public int getTotalExecutors() {
        int r=0;
        for (ComputerExt c : get_all()) {
            if(c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    public int getBusyExecutors() {
        int r=0;
        for (ComputerExt c : get_all()) {
            if(c.isOnline())
                r += c.countBusy();
        }
        return r;
    }

    /**
     * {@code getTotalExecutors()-getBusyExecutors()}, plus executors that are being brought online.
     */
    public int getIdleExecutors() {
        int r=0;
        for (ComputerExt c : get_all())
            if(c.isOnline() || c.isConnecting())
                r += c.countIdle();
        return r;
    }

    public String getSearchUrl() {
        return "/computers/";
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
     * {@link NodeMonitor}s are persisted in this file.
     */
    private static XmlFile getConfigFile() {
        return new XmlFile(new File(HudsonExt.getInstance().getRootDir(),"nodeMonitors.xml"));
    }

 
    /**
     * Just to force the execution of the static initializer.
     */
    public static void initialize() {}

    private static final Logger LOGGER = Logger.getLogger(ComputerSetExt.class.getName());

    static {
        try {
            DescribableListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>> r
                    = new DescribableListExt<NodeMonitorExt, DescriptorExt<NodeMonitorExt>>(Saveable.NOOP);

            // load persisted monitors
            XmlFile xf = getConfigFile();
            if(xf.exists()) {
                DescribableListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>> persisted =
                        (DescribableListExt<NodeMonitorExt,DescriptorExt<NodeMonitorExt>>) xf.read();
                r.replaceBy(persisted.toList());
            }

            // if we have any new monitors, let's add them
            for (DescriptorExt<NodeMonitorExt> d : NodeMonitorExt.all())
                if(r.get(d)==null) {
                    NodeMonitorExt i = createDefaultInstance(d,false);
                    if(i!=null)
                        r.add(i);
                }
            monitors.replaceBy(r.toList());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to instanciate NodeMonitors",e);
        }
    }

    protected static NodeMonitorExt createDefaultInstance(DescriptorExt<NodeMonitorExt> d, boolean ignored) {
        try {
            NodeMonitorExt nm = d.clazz.newInstance();
            nm.setIgnored(ignored);
            return nm;
        } catch (InstantiationException e) {
            LOGGER.log(Level.SEVERE, "Failed to instanciate "+d.clazz,e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, "Failed to instanciate "+d.clazz,e);
        }
        return null;
    }
}

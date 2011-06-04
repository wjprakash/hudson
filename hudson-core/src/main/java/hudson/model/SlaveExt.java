/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Martin Eigenbrodt, Stephen Connolly, Tom Huybrechts
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

import hudson.FilePathExt;
import hudson.Launcher;
import hudson.UtilExt;
import hudson.Launcher.RemoteLauncher;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.CommandLauncherExt;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlaveExt;
import hudson.slaves.JNLPLauncherExt;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategyExt;
import hudson.slaves.SlaveComputerExt;
import hudson.util.ClockDifferenceExt;
import hudson.util.DescribableListExt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


import org.apache.commons.io.IOUtils;

/**
 * Information about a HudsonExt slave node.
 *
 * <p>
 * Ideally this would have been in the <tt>hudson.slaves</tt> package,
 * but for compatibility reasons, it can't.
 *
 * <p>
 * TODO: move out more stuff to {@link DumbSlave}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SlaveExt extends NodeExt implements Serializable {

    /**
     * Name of this slave node.
     */
    protected String name;
    /**
     * Description of this node.
     */
    private final String description;
    /**
     * Path to the root of the workspace
     * from the view point of this node, such as "/hudson"
     */
    protected final String remoteFS;
    /**
     * Number of executors of this node.
     */
    protected int numExecutors = 2;
    /**
     * Job allocation strategy.
     */
    private ModeExt mode;
    /**
     * Slave availablility strategy.
     */
    private RetentionStrategyExt retentionStrategy;
    /**
     * The starter that will startup this slave.
     */
    private ComputerLauncher launcher;
    /**
     * Whitespace-separated labels.
     */
    private String label = "";
    private /*almost final*/ DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = new DescribableListExt<NodeProperty<?>, NodePropertyDescriptor>(HudsonExt.getInstance());
    /**
     * Lazily computed set of labels from {@link #label}.
     */
    private transient volatile Set<LabelExt> labels;

    public SlaveExt(String name, String nodeDescription, String remoteFS, String numExecutors,
            ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategyExt retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        this(name, nodeDescription, remoteFS, UtilExt.tryParseNumber(numExecutors, 1).intValue(), mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    /**
     * @deprecated since 2009-02-20.
     */
    @Deprecated
    public SlaveExt(String name, String nodeDescription, String remoteFS, int numExecutors,
            ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategyExt retentionStrategy) throws IOException {
        this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }

    public SlaveExt(String name, String nodeDescription, String remoteFS, int numExecutors,
            ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategyExt retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        this.name = name;
        this.description = nodeDescription;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.remoteFS = UtilExt.fixNull(remoteFS).trim();
        this.label = UtilExt.fixNull(labelString).trim();
        this.launcher = launcher;
        this.retentionStrategy = retentionStrategy;
        getAssignedLabels();    // compute labels now

        this.nodeProperties.replaceBy(nodeProperties);

    }

    public ComputerLauncher getLauncher() {
        return launcher == null ? new JNLPLauncherExt() : launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getNodeName() {
        return name;
    }

    public void setNodeName(String name) {
        this.name = name;
    }

    public String getNodeDescription() {
        return description;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public ModeExt getMode() {
        return mode;
    }

    public void setMode(ModeExt mode) {
        this.mode = mode;
    }

    public DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        return nodeProperties;
    }

    public RetentionStrategyExt getRetentionStrategy() {
        return retentionStrategy == null ? RetentionStrategyExt.Always.INSTANCE : retentionStrategy;
    }

    public void setRetentionStrategy(RetentionStrategyExt availabilityStrategy) {
        this.retentionStrategy = availabilityStrategy;
    }

    public String getLabelString() {
        return UtilExt.fixNull(label).trim();
    }

    public ClockDifferenceExt getClockDifference() throws IOException, InterruptedException {
        VirtualChannel channel = getChannel();
        if (channel == null) {
            throw new IOException(getNodeName() + " is offline");
        }

        long startTime = System.currentTimeMillis();
        long slaveTime = channel.call(new GetSystemTime());
        long endTime = System.currentTimeMillis();

        return new ClockDifferenceExt((startTime + endTime) / 2 - slaveTime);
    }

    public ComputerExt createComputer() {
        return new SlaveComputerExt(this);
    }

    public FilePathExt getWorkspaceFor(TopLevelItem item) {
        FilePathExt r = getWorkspaceRoot();
        if (r == null) {
            return null;    // offline
        }
        return r.child(item.getName());
    }

    public FilePathExt getRootPath() {
        return createPath(remoteFS);
    }

    /**
     * Root directory on this slave where all the job workspaces are laid out.
     * @return
     *      null if not connected.
     */
    public FilePathExt getWorkspaceRoot() {
        FilePathExt r = getRootPath();
        if (r == null) {
            return null;
        }
        return r.child(WORKSPACE_ROOT);
    }

    /**
     * Web-bound object used to serve jar files for JNLP.
     */
    public static class JnlpJar {

        private final String fileName;

        public JnlpJar(String fileName) {
            this.fileName = fileName;
        }

        protected URLConnection connect() throws IOException {
            URL res = getURL();
            return res.openConnection();
        }

        public URL getURL() throws MalformedURLException {
            URL res = HudsonExt.getInstance().servletContext.getResource("/WEB-INF/" + fileName);
            if (res == null) {
                // during the development this path doesn't have the files.
                res = new URL(new File(".").getAbsoluteFile().toURI().toURL(), "target/generated-resources/WEB-INF/" + fileName);
            }
            return res;
        }

        public byte[] readFully() throws IOException {
            InputStream in = connect().getInputStream();
            try {
                return IOUtils.toByteArray(in);
            } finally {
                in.close();
            }
        }
    }

    public Launcher createLauncher(TaskListener listener) {
        SlaveComputerExt c = getComputer();
        return new RemoteLauncher(listener, c.getChannel(), c.isUnix()).decorateFor(this);
    }

    /**
     * Gets the corresponding computer object.
     */
    public SlaveComputerExt getComputer() {
        return (SlaveComputerExt) toComputer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SlaveExt that = (SlaveExt) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    private Object readResolve() {
        // convert the old format to the new one
        if (command != null && agentCommand == null) {
            if (command.length() > 0) {
                command += ' ';
            }
            agentCommand = command + "java -jar ~/bin/slave.jar";
        }
        if (command != null || localFS != null) {
            OldDataMonitorExt.report(HudsonExt.getInstance(), "1.69");
        }
        if (launcher == null) {
            launcher = (agentCommand == null || agentCommand.trim().length() == 0)
                    ? new JNLPLauncherExt()
                    : new CommandLauncherExt(agentCommand);
        }
        if (nodeProperties == null) {
            nodeProperties = new DescribableListExt<NodeProperty<?>, NodePropertyDescriptor>(HudsonExt.getInstance());
        }
        return this;
    }
    
    //
    // backward compatibility
    //
    
    /**
     * In HudsonExt < 1.69 this was used to store the local file path
     * to the remote workspace. No longer in use.
     *
     * @deprecated
     *      ... but still in use during the transition.
     */
    private File localFS;
    /**
     * In HudsonExt < 1.69 this was used to store the command
     * to connect to the remote machine, like "ssh myslave".
     *
     * @deprecated
     */
    private transient String command;
    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private transient String agentCommand;

    /**
     * Obtains the system clock.
     */
    private static final class GetSystemTime implements Callable<Long, RuntimeException> {

        public Long call() {
            return System.currentTimeMillis();
        }
        private static final long serialVersionUID = 1L;
    }
    /**
     * Determines the workspace root file name for those who really really need the shortest possible path name.
     */
    private static final String WORKSPACE_ROOT = System.getProperty(SlaveExt.class.getName() + ".workspaceRoot", "workspace");
}

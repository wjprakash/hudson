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
import hudson.Util;
import hudson.Launcher.RemoteLauncher;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeDescriptorExt;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

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

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
public abstract class Slave extends SlaveExt {
     
    
    

    /**
     * Lazily computed set of labels from {@link #label}.
     */
    private transient volatile Set<LabelExt> labels;

    @DataBoundConstructor
    public Slave(String name, String nodeDescription, String remoteFS, String numExecutors,
                 ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this(name,nodeDescription,remoteFS,Util.tryParseNumber(numExecutors, 1).intValue(),mode,labelString,launcher,retentionStrategy, nodeProperties);
    }

    /**
     * @deprecated since 2009-02-20.
     */
    @Deprecated
    public Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
            ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException, IOException {
    	this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }
    
    public Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
                 ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);

        if (name.equals(""))
            throw new FormException(Messages.Slave_InvalidConfig_NoName(), null);

//        if (remoteFS.equals(""))
//            throw new FormException(Messages.Slave_InvalidConfig_NoRemoteDir(name), null);

        if (numExecutors <= 0)
            throw new FormException(Messages.Slave_InvalidConfig_Executors(name), null);
    }

    

    /**
     * Web-bound object used to serve jar files for JNLP.
     */
    public static final class JnlpJar extends SlaveExt.JnlpJar implements HttpResponse {
        
        public JnlpJar(String fileName) {
            super(fileName);
        }

        public void doIndex( StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            URLConnection con = connect();
            // since we end up redirecting users to jnlpJars/foo.jar/, set the content disposition
            // so that browsers can download them in the right file name.
            // see http://support.microsoft.com/kb/260519 and http://www.boutell.com/newfaq/creating/forcedownload.html
            rsp.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            InputStream in = con.getInputStream();
            rsp.serveFile(req, in, con.getLastModified(), con.getContentLength(), "*.jar" );
            in.close();
        }

        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            doIndex(req,rsp);
        }

    }
 

    public SlaveDescriptor getDescriptor() {
        DescriptorExt d = HudsonExt.getInstance().getDescriptorOrDie(getClass());
        if (d instanceof SlaveDescriptor)
            return (SlaveDescriptor) d;
        throw new IllegalStateException(d.getClass()+" needs to extend from SlaveDescriptor");
    }

    public static abstract class SlaveDescriptor extends NodeDescriptorExt {
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        /**
         * Performs syntactical check on the remote FS for slaves.
         */
        public FormValidation doCheckRemoteFS(@QueryParameter String value) throws IOException, ServletException {
            if(Util.fixEmptyAndTrim(value)==null)
                return FormValidation.error(Messages.Slave_Remote_Director_Mandatory());

            if(value.startsWith("\\\\") || value.startsWith("/net/"))
                return FormValidation.warning(Messages.Slave_Network_Mounted_File_System_Warning());

            return FormValidation.ok();
        }
    }

}

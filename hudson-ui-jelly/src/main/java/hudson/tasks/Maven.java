/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly, Tom Huybrechts, Yahoo! Inc.
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
package hudson.tasks;

import hudson.Extension;
import hudson.CopyOnWrite;
import hudson.model.AbstractProjectExt;
import hudson.model.Descriptor.FormException;
import hudson.model.HudsonExt;
import hudson.tools.ToolDescriptorExt;
import hudson.tools.ToolInstallation;
import hudson.tools.DownloadFromUrlInstallerExt;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.util.List;
import java.util.Collections;

/**
 * Build by using Maven.
 *
 * @author Kohsuke Kawaguchi
 */
public class Maven extends MavenExt {
     

    public Maven(String targets,String name) {
        this(targets,name,null,null,null,false);
    }

    public Maven(String targets, String name, String pom, String properties, String jvmOptions) {
	this(targets, name, pom, properties, jvmOptions, false);
    }
    
    @DataBoundConstructor
    public Maven(String targets,String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository) {
        super(targets, name, pom, properties, jvmOptions, usePrivateRepository);
    }

    

    /**
     * Gets the Maven to invoke,
     * or null to invoke the default one.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : getDescriptor().getInstallations() ) {
            if(mavenName !=null && mavenName.equals(i.getName()))
                return i;
        }
        return null;
    }

     

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * @deprecated as of 1.286
     *      Use {@link HudsonExt#getDescriptorByType(Class)} to obtain the current instance.
     *      For compatibility, this field retains the last created {@link DescriptorImpl}.
     *      TODO: fix sonar plugin that depends on this. That's the only plugin that depends on this field.
     */
    public static DescriptorImpl DESCRIPTOR;

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile MavenInstallation[] installations = new MavenInstallation[0];

        public DescriptorImpl() {
            DESCRIPTOR = this;
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/maven.html";
        }

        public String getDisplayName() {
            return Messages.Maven_DisplayName();
        }

        public MavenInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(MavenInstallation... installations) {
            this.installations = installations;
            save();
        }

        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(Maven.class,formData);
        }
    }

    /**
     * Represents a Maven installation in a system.
     */
    public static final class MavenInstallation extends MavenExt.MavenInstallation  {
        
        @DataBoundConstructor
        public MavenInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, home, properties);
        }
 

        @Extension
        public static class DescriptorImpl extends ToolDescriptorExt<MavenInstallation> {
            @Override
            public String getDisplayName() {
                return "Maven";
            }

            @Override
            public List<? extends ToolInstaller> getDefaultInstallers() {
                return Collections.singletonList(new MavenInstaller(null));
            }

            @Override
            public MavenInstallation[] getInstallations() {
                return HudsonExt.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
            }

            @Override
            public void setInstallations(MavenInstallation... installations) {
                HudsonExt.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(installations);
            }

            /**
             * Checks if the MAVEN_HOME is valid.
             */
            public FormValidation doCheckMavenHome(@QueryParameter File value) {
                // this can be used to check the existence of a file on the server, so needs to be protected
                if(!HudsonExt.getInstance().hasPermission(HudsonExt.ADMINISTER))
                    return FormValidation.ok();

                if(value.getPath().equals(""))
                    return FormValidation.ok();

                if(!value.isDirectory())
                    return FormValidation.error(Messages.Maven_NotADirectory(value));

                File maven1File = new File(value,MAVEN_1_INSTALLATION_COMMON_FILE);
                File maven2File = new File(value,MAVEN_2_INSTALLATION_COMMON_FILE);

                if(!maven1File.exists() && !maven2File.exists())
                    return FormValidation.error(Messages.Maven_NotMavenDirectory(value));

                return FormValidation.ok();
            }

            public FormValidation doCheckName(@QueryParameter String value) {
                return FormValidation.validateRequired(value);
            }
        }
    }

    /**
     * Automatic Maven installer from apache.org.
     */
    public static class MavenInstaller extends DownloadFromUrlInstallerExt {
        @DataBoundConstructor
        public MavenInstaller(String id) {
            super(id);
        }

        @Extension
        public static final class DescriptorImpl extends DownloadFromUrlInstallerExt.DescriptorImpl<MavenInstaller> {
            public String getDisplayName() {
                return Messages.InstallFromApache();
            }

            @Override
            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return toolType==MavenInstallation.class;
            }
        }
    }

     
}

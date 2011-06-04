/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts, Yahoo! Inc.
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

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.model.AbstractProjectExt;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.HudsonExt;
import hudson.tools.ToolDescriptorExt;
import hudson.tools.ToolInstallation;
import hudson.tools.DownloadFromUrlInstallerExt;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.util.List;
import java.util.Collections;

/**
 * Ant launcher.
 *
 * @author Kohsuke Kawaguchi
 */
public class Ant extends AntExt {
     
    @DataBoundConstructor
    public Ant(String targets,String antName, String antOpts, String buildFile, String properties) {
        super(targets, antName, antOpts, buildFile, properties);
    }

	 

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
         
        @CopyOnWrite
        private volatile AntInstallation[] installations = new AntInstallation[0];

        public DescriptorImpl() {
            load();
        }

        protected DescriptorImpl(Class<? extends AntExt> clazz) {
            super(clazz);
        }

        /**
         * Obtains the {@link AntInstallation.DescriptorImpl} instance.
         */
        public AntInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(AntInstallation.DescriptorImpl.class);
        }

        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/ant.html";
        }

        public String getDisplayName() {
            return Messages.Ant_DisplayName();
        }

        public AntInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(AntInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }
        
        public Ant newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return (Ant)req.bindJSON(clazz,formData);
        }

    }

    /**
     * Represents the Ant installation on the system.
     */
    public static final class AntInstallation extends  AntExt.AntInstallation{
         
        @DataBoundConstructor
        public AntInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, home, properties);
        }

        /**
         * @deprecated as of 1.308
         *      Use {@link #AntInstallation(String, String, List)}
         */
        public AntInstallation(String name, String home) {
            this(name,home,Collections.<ToolProperty<?>>emptyList());
        }

         

        @Extension
        public static class DescriptorImpl extends ToolDescriptorExt<AntInstallation> {

            @Override
            public String getDisplayName() {
                return "Ant";
            }

            // for compatibility reasons, the persistence is done by Ant.DescriptorImpl  
            @Override
            public AntInstallation[] getInstallations() {
                return Hudson.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
            }

            @Override
            public void setInstallations(AntInstallation... installations) {
                HudsonExt.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(installations);
            }

            @Override
            public List<? extends ToolInstaller> getDefaultInstallers() {
                return Collections.singletonList(new AntInstaller(null));
            }

            /**
             * Checks if the ANT_HOME is valid.
             */
            public FormValidation doCheckHome(@QueryParameter File value) {
                // this can be used to check the existence of a file on the server, so needs to be protected
                if(!HudsonExt.getInstance().hasPermission(HudsonExt.ADMINISTER))
                    return FormValidation.ok();

                if(value.getPath().equals(""))
                    return FormValidation.ok();

                if(!value.isDirectory())
                    return FormValidation.error(Messages.Ant_NotADirectory(value));

                File antJar = new File(value,"lib/ant.jar");
                if(!antJar.exists())
                    return FormValidation.error(Messages.Ant_NotAntDirectory(value));

                return FormValidation.ok();
            }

            public FormValidation doCheckName(@QueryParameter String value) {
                return FormValidation.validateRequired(value);
            }
        }

    }

    /**
     * Automatic Ant installer from apache.org.
     */
    public static class AntInstaller extends DownloadFromUrlInstallerExt {
        @DataBoundConstructor
        public AntInstaller(String id) {
            super(id);
        }

        @Extension
        public static final class DescriptorImpl extends DownloadFromUrlInstallerExt.DescriptorImpl<AntInstaller> {
            public String getDisplayName() {
                return Messages.InstallFromApache();
            }

            @Override
            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return toolType==AntInstallation.class;
            }
        }
    }
}

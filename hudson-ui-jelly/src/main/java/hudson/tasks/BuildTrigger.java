/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt
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
import hudson.UtilExt;
import hudson.security.AccessControlled;
import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor.FormException;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.Items;
import hudson.model.ProjectExt;
import hudson.model.ResultExt;
import hudson.model.listeners.ItemListener;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers builds of other projects.
 *
 * <p>
 * Despite what the name suggests, this class doesn't actually trigger other jobs
 * as a part of {@link #perform} method. Its main job is to simply augument
 * {@link DependencyGraph}. Jobs are responsible for triggering downstream jobs
 * on its own, because dependencies may come from other sources.
 *
 * <p>
 * This class, however, does provide the {@link #execute(AbstractBuildExt, BuildListener, BuildTrigger)}
 * method as a convenience method to invoke downstream builds.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildTrigger extends BuildTriggerExt {

    private static final Logger LOGGER = Logger.getLogger(BuildTrigger.class.getName());

    @DataBoundConstructor
    public BuildTrigger(String childProjects, boolean evenIfUnstable) {
        this(childProjects, evenIfUnstable ? ResultExt.UNSTABLE : ResultExt.SUCCESS);
    }

    public BuildTrigger(String childProjects, ResultExt threshold) {
        super(childProjects, threshold);
    }

    public BuildTrigger(List<AbstractProjectExt> childProjects, ResultExt threshold) {
        this((Collection<AbstractProjectExt>) childProjects, threshold);
    }

    public BuildTrigger(Collection<? extends AbstractProjectExt> childProjects, ResultExt threshold) {
        this(Items.toNameList(childProjects), threshold);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public String getDisplayName() {
            return Messages.BuildTrigger_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new BuildTrigger(
                    formData.getString("childProjects"),
                    formData.has("evenIfUnstable") && formData.getBoolean("evenIfUnstable"));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }

        public boolean showEvenIfUnstableOption(Class<? extends AbstractProjectExt> jobType) {
            // UGLY: for promotion process, this option doesn't make sense. 
            return !jobType.getName().contains("PromotionProcess");
        }

        /**
         * Form validation method.
         */
        public FormValidation doCheck(@AncestorInPath AccessControlled subject, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!subject.hasPermission(ItemExt.CONFIGURE)) {
                return FormValidation.ok();
            }

            StringTokenizer tokens = new StringTokenizer(UtilExt.fixNull(value), ",");
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                ItemExt item = HudsonExt.getInstance().getItemByFullName(projectName, ItemExt.class);
                if (item == null) {
                    return FormValidation.error(Messages.BuildTrigger_NoSuchProject(projectName, AbstractProjectExt.findNearest(projectName).getName()));
                }
                if (!(item instanceof AbstractProjectExt)) {
                    return FormValidation.error(Messages.BuildTrigger_NotBuildable(projectName));
                }
            }

            return FormValidation.ok();
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {

            @Override
            public void onRenamed(ItemExt item, String oldName, String newName) {
                // update BuildTrigger of other projects that point to this object.
                // can't we generalize this?
                for (ProjectExt<?, ?> p : HudsonExt.getInstance().getProjects()) {
                    BuildTrigger t = p.getPublishersList().get(BuildTrigger.class);
                    if (t != null) {
                        if (t.onJobRenamed(oldName, newName)) {
                            try {
                                p.save();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to persist project setting during rename from " + oldName + " to " + newName, e);
                            }
                        }
                    }
                }
            }
        }
    }
}

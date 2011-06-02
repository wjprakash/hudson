/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Michael B. Donohue, Yahoo!, Inc.
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
package hudson.tasks.test;

import hudson.model.AbstractProjectExt;
import hudson.Extension;
import hudson.UtilExt;
import static hudson.UtilExt.fixNull;
import hudson.model.Descriptor.FormException;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 * Aggregates downstream test reports into a single consolidated report,
 * so that people can see the overall test results in one page
 * when tests are scattered across many different jobs.
 *
 * @author Kohsuke Kawaguchi
 */
public class AggregatedTestResultPublisher extends AggregatedTestResultPublisherExt {
    
    public AggregatedTestResultPublisher(String jobs) {
        super(jobs);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;    // for all types
        }

        @Override
        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/tasks/aggregate-test/help.html";
        }

        public FormValidation doCheck(@AncestorInPath AbstractProjectExt project, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(ItemExt.CONFIGURE))  return FormValidation.ok();

            for (String name : UtilExt.tokenize(fixNull(value), ",")) {
                name = name.trim();
                if(HudsonExt.getInstance().getItemByFullName(name)==null)
                    return FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoSuchProject(name,AbstractProjectExt.findNearest(name).getName()));
            }
            
            return FormValidation.ok();
        }

        public AggregatedTestResultPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            JSONObject s = formData.getJSONObject("specify");
            if(s.isNullObject())
                return new AggregatedTestResultPublisher(null);
            else
                return new AggregatedTestResultPublisher(s.getString("jobs"));
        }
    }

}

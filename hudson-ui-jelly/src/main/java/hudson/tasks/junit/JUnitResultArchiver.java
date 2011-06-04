/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt,
 * Tom Huybrechts, Yahoo!, Inc., Richard Hierlmeier
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
package hudson.tasks.junit;

import hudson.Extension;
import hudson.FilePathExt;
import hudson.matrix.MatrixAggregatable;
import hudson.model.AbstractProjectExt;
import hudson.model.Descriptor.FormException;
import hudson.model.DescriptorExt;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.DescribableListExt;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.Serializable;

/**
 * Generates HTML report from JUnit test result XML files.
 * 
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends JUnitResultArchiverExt implements Serializable,
        MatrixAggregatable {

    /**
     * left for backwards compatibility
     * @deprecated since 2009-08-09.
     */
    @Deprecated
    public JUnitResultArchiver(String testResults) {
        this(testResults, false, null);
    }

    @Deprecated
    public JUnitResultArchiver(String testResults,
            DescribableListExt<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers) {
        this(testResults, false, testDataPublishers);
    }

    @DataBoundConstructor
    public JUnitResultArchiver(
            String testResults,
            boolean keepLongStdio,
            DescribableListExt<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers) {
        super(testResults, keepLongStdio, testDataPublishers);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return Messages.JUnitResultArchiver_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/tasks/junit/report.html";
        }

        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            String testResults = formData.getString("testResults");
            boolean keepLongStdio = formData.getBoolean("keepLongStdio");
            DescribableListExt<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers = new DescribableListExt<TestDataPublisher, DescriptorExt<TestDataPublisher>>(Saveable.NOOP);

            testDataPublishers.rebuild(req, formData, TestDataPublisher.all());

            return new JUnitResultArchiver(testResults, keepLongStdio, testDataPublishers);
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheckTestResults(
                @AncestorInPath AbstractProjectExt project,
                @QueryParameter String value) throws IOException {
            return FilePathExt.validateFileMask(project.getSomeWorkspace(), value);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }
    }
}

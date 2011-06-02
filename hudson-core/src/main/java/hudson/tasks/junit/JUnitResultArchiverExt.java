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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePathExt;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuildExt;
import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.CheckPoint;
import hudson.model.DescriptorExt;
import hudson.model.ResultExt;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultActionExt.Data;
import hudson.tasks.test.TestResultAggregator;
import hudson.tasks.test.TestResultProjectActionExt;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Generates HTML report from JUnit test result XML files.
 * 
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiverExt extends Recorder implements Serializable,
        MatrixAggregatable {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;
    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     * @since 1.358
     */
    private final boolean keepLongStdio;
    /**
     * {@link TestDataPublisher}s configured for this archiver, to process the recorded data.
     * For compatibility reasons, can be null.
     * @since 1.320
     */
    private final DescribableList<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers;

    /**
     * left for backwards compatibility
     * @deprecated since 2009-08-09.
     */
    @Deprecated
    public JUnitResultArchiverExt(String testResults) {
        this(testResults, false, null);
    }

    @Deprecated
    public JUnitResultArchiverExt(String testResults,
            DescribableList<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers) {
        this(testResults, false, testDataPublishers);
    }

    public JUnitResultArchiverExt(
            String testResults,
            boolean keepLongStdio,
            DescribableList<TestDataPublisher, DescriptorExt<TestDataPublisher>> testDataPublishers) {
        this.testResults = testResults;
        this.keepLongStdio = keepLongStdio;
        this.testDataPublishers = testDataPublishers;
    }

    /**
     * In progress. Working on delegating the actual parsing to the JUnitParser.
     */
    protected TestResultExt parse(String expandedTestResults, AbstractBuildExt build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        return new JUnitParser(isKeepLongStdio()).parse(expandedTestResults, build, launcher, listener);
    }

    @Override
    public boolean perform(AbstractBuildExt build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
        TestResultActionExt action;

        final String testResults = build.getEnvironment(listener).expand(this.testResults);

        try {
            TestResultExt result = parse(testResults, build, launcher, listener);

            try {
                action = new TestResultActionExt(build, result, listener);
            } catch (NullPointerException npe) {
                throw new AbortException(Messages.JUnitResultArchiver_BadXML(testResults));
            }
            result.freeze(action);
            if (result.getPassCount() == 0 && result.getFailCount() == 0) {
                throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
            }

            // TODO: Move into JUnitParser [BUG 3123310]
            List<Data> data = new ArrayList<Data>();
            if (testDataPublishers != null) {
                for (TestDataPublisher tdp : testDataPublishers) {
                    Data d = tdp.getTestData(build, launcher, listener, result);
                    if (d != null) {
                        data.add(d);
                    }
                }
            }

            action.setData(data);

            CHECKPOINT.block();

        } catch (AbortException e) {
            if (build.getResult() == ResultExt.FAILURE) // most likely a build failed before it gets to the test phase.
            // don't report confusing error message.
            {
                return true;
            }

            listener.getLogger().println(e.getMessage());
            build.setResult(ResultExt.FAILURE);
            return true;
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to archive test reports"));
            build.setResult(ResultExt.FAILURE);
            return true;
        }

        build.getActions().add(action);
        CHECKPOINT.report();

        if (action.getResult().getFailCount() > 0) {
            build.setResult(ResultExt.UNSTABLE);
        }

        return true;
    }

    /**
     * Not actually used, but left for backward compatibility
     * 
     * @deprecated since 2009-08-10.
     */
    protected TestResultExt parseResult(DirectoryScanner ds, long buildTime)
            throws IOException {
        return new TestResultExt(buildTime, ds);
    }

    /**
     * This class does explicit checkpointing.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getTestResults() {
        return testResults;
    }

    public DescribableList<TestDataPublisher, DescriptorExt<TestDataPublisher>> getTestDataPublishers() {
        return testDataPublishers;
    }

    @Override
    public Collection<Action> getProjectActions(AbstractProjectExt<?, ?> project) {
        return Collections.<Action>singleton(new TestResultProjectActionExt(project));
    }

    public MatrixAggregator createAggregator(MatrixBuildExt build,
            Launcher launcher, BuildListener listener) {
        return new TestResultAggregator(build, launcher, listener);
    }

    /**
     * @return the keepLongStdio
     */
    public boolean isKeepLongStdio() {
        return keepLongStdio;
    }
    /**
     * Test result tracks the diff from the previous run, hence the checkpoint.
     */
    private static final CheckPoint CHECKPOINT = new CheckPoint(
            "JUnit result archiving");
    private static final long serialVersionUID = 1L;

}

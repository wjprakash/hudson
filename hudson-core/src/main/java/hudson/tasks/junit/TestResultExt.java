/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, id:cactusman, Tom Huybrechts, Yahoo!, Inc.
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
import hudson.UtilExt;
import hudson.model.AbstractBuildExt;
import hudson.model.RunExt;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObjectExt;
import hudson.tasks.test.AbstractTestResultActionExt;
import hudson.util.IOException2;
import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Root of all the test results for one build.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultExt extends MetaTabulatedResult {
    private static final Logger LOGGER = Logger.getLogger(TestResultExt.class.getName());

    /**
     * List of all {@link SuiteResult}s in this test.
     * This is the core data structure to be persisted in the disk.
     */
    private final List<SuiteResultExt> suites = new ArrayList<SuiteResultExt>();

    /**
     * {@link #suites} keyed by their names for faster lookup.
     */
    private transient Map<String,SuiteResultExt> suitesByName;

    /**
     * Results tabulated by package.
     */
    private transient Map<String,PackageResultExt> byPackages;

    // set during the freeze phase
    private transient AbstractTestResultActionExt parentAction;

    private transient TestObjectExt parent;

    /**
     * Number of all tests.
     */
    private transient int totalTests;

    private transient int skippedTests;
    
    private float duration;
    
    /**
     * Number of failed/error tests.
     */
    private transient List<CaseResultExt> failedTests;

    private final boolean keepLongStdio;
    
    /**
     * Creates an empty result.
     */
    public TestResultExt() {
        keepLongStdio = false;
    }

    @Deprecated
    public TestResultExt(long buildTime, DirectoryScanner results) throws IOException {
        this(buildTime, results, false);
    }

    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @since 1.358
     */
    public TestResultExt(long buildTime, DirectoryScanner results, boolean keepLongStdio) throws IOException {
        this.keepLongStdio = keepLongStdio;
        parse(buildTime, results);
    }
    
    public TestObjectExt getParent() {
    	return parent;
    }
    
    @Override
    public void setParent(TestObjectExt parent) {
        this.parent = parent;
    }
    
    @Override
    public TestResultExt getTestResult() {
    	return this;
    }

    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     */
    public void parse(long buildTime, DirectoryScanner results) throws IOException {
        String[] includedFiles = results.getIncludedFiles();
        File baseDir = results.getBasedir();

        boolean parsed=false;

        for (String value : includedFiles) {
            File reportFile = new File(baseDir, value);
            // only count files that were actually updated during this build
            if ( (buildTime-3000/*error margin*/ <= reportFile.lastModified()) || !checkTimestamps) {
                if(reportFile.length()==0) {
                    // this is a typical problem when JVM quits abnormally, like OutOfMemoryError during a test.
                    SuiteResultExt sr = new SuiteResultExt(reportFile.getName(), "", "");
                    sr.addCase(new CaseResultExt(sr,"<init>","Test report file "+reportFile.getAbsolutePath()+" was length 0"));
                    add(sr);
                } else {
                    parse(reportFile);
                }
                parsed = true;
            }
        }

        if(!parsed) {
            long localTime = System.currentTimeMillis();
            if(localTime < buildTime-1000) /*margin*/
                // build time is in the the future. clock on this slave must be running behind
                throw new AbortException(
                    "Clock on this slave is out of sync with the master, and therefore \n" +
                    "I can't figure out what test results are new and what are old.\n" +
                    "Please keep the slave clock in sync with the master.");

            File f = new File(baseDir,includedFiles[0]);
            throw new AbortException(
                String.format(
                "Test reports were found but none of them are new. Did tests run? \n"+
                "For example, %s is %s old\n", f,
                UtilExt.getTimeSpanString(buildTime-f.lastModified())));
        }
    }

    private void add(SuiteResultExt sr) {
        for (SuiteResultExt s : suites) {
            // a common problem is that people parse TEST-*.xml as well as TESTS-TestSuite.xml
            // see http://www.nabble.com/Problem-with-duplicate-build-execution-td17549182.html for discussion
            if(s.getName().equals(sr.getName()) && eq(s.getTimestamp(),sr.getTimestamp()))
                return; // duplicate
        }
        suites.add(sr);
        duration += sr.getDuration();
    }

    private boolean eq(Object lhs, Object rhs) {
        return lhs != null && rhs != null && lhs.equals(rhs);
    }

    /**
     * Parses an additional report file.
     */
    public void parse(File reportFile) throws IOException {
        try {
            for (SuiteResultExt suiteResult : SuiteResultExt.parse(reportFile, keepLongStdio))
                add(suiteResult);
        } catch (RuntimeException e) {
            throw new IOException2("Failed to read "+reportFile,e);
        } catch (DocumentException e) {
            if (!reportFile.getPath().endsWith(".xml")) {
                throw new IOException2("Failed to read "+reportFile+"\n"+
                    "Is this really a JUnit report file? Your configuration must be matching too many files",e);
            } else {
                SuiteResultExt sr = new SuiteResultExt(reportFile.getName(), "", "");
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                String error = "Failed to read test report file "+reportFile.getAbsolutePath()+"\n"+writer.toString();
                sr.addCase(new CaseResultExt(sr,"<init>",error));
                add(sr);
                throw new IOException2("Failed to read "+reportFile,e);
            }
        }
    }

    public String getDisplayName() {
        return Messages.TestResult_getDisplayName();
    }

    @Override
    public AbstractBuildExt<?,?> getOwner() {
        return (parentAction == null? null: parentAction.owner);
    }

    @Override
    public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
        if (getId().equals(id) || (id == null)) {
            return this;
        }
        
        String firstElement = null;
        String subId = null;
        int sepIndex = id.indexOf('/');
        if (sepIndex < 0) {
            firstElement = id;
            subId = null;
        } else {
            firstElement = id.substring(0, sepIndex);
            subId = id.substring(sepIndex + 1);
            if (subId.length() == 0) {
                subId = null;
            }
        }

        String packageName = null;
        if (firstElement.equals(getId())) {
            sepIndex = subId.indexOf('/');
            if (sepIndex < 0) {
                packageName = subId;
                subId = null; 
            } else {
                packageName = subId.substring(0, sepIndex);
                subId = subId.substring(sepIndex + 1);
            }
        } else {
            packageName = firstElement;
            subId = null; 
        }
        PackageResultExt child = byPackage(packageName);
        if (child != null) {
            if (subId != null) {
                return child.findCorrespondingResult(subId);
            } else {
                return child;
            }
        } else {
            return null;
    }
    }

    @Override
    public String getTitle() {
        return Messages.TestResult_getTitle();
    }

    @Override
    public String getChildTitle() {
        return Messages.TestResult_getChildTitle();
    }

    @Override
    public float getDuration() {
        return duration; 
    }
    
    @Override
    public int getPassCount() {
        return totalTests-getFailCount()-getSkipCount();
    }

    @Override
    public int getFailCount() {
        if(failedTests==null)
            return 0;
        else
        return failedTests.size();
    }

    @Override
    public int getSkipCount() {
        return skippedTests;
    }

    @Override
    public List<CaseResultExt> getFailedTests() {
        return failedTests;
    }

    /**
     * Gets the "children" of this test result that passed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public Collection<? extends hudson.tasks.test.TestResult> getPassedTests() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * Gets the "children" of this test result that were skipped
     *
     * @return the children of this test result, if any, or an empty list
     */
    @Override
    public Collection<? extends hudson.tasks.test.TestResult> getSkippedTests() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Override
    public int getFailedSince() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * If this test failed, then return the run
     * when this test started failing.
     */
    @Override
    public RunExt<?, ?> getFailedSinceRun() {
        throw new UnsupportedOperationException();  // TODO: implement!(FIXME: generated)
    }

    /**
     * The stdout of this test.
     * <p/>
     * <p/>
     * Depending on the tool that produced the XML report, this method works somewhat inconsistently.
     * With some tools (such as Maven surefire plugin), you get the accurate information, that is
     * the stdout from this test case. With some other tools (such as the JUnit task in Ant), this
     * method returns the stdout produced by the entire test suite.
     * <p/>
     * <p/>
     * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
     *
     * @since 1.294
     */
    @Override
    public String getStdout() {
        StringBuilder sb = new StringBuilder();
        for (SuiteResultExt suite: suites) {
            sb.append("Standard Out (stdout) for Suite: " + suite.getName());
            sb.append(suite.getStdout());
        }
        return sb.toString();
    }

    /**
     * The stderr of this test.
     *
     * @see #getStdout()
     * @since 1.294
     */
    @Override
    public String getStderr() {
        StringBuilder sb = new StringBuilder();
        for (SuiteResultExt suite: suites) {
            sb.append("Standard Error (stderr) for Suite: " + suite.getName());
            sb.append(suite.getStderr());
        }
        return sb.toString();
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    @Override
    public String getErrorStackTrace() {
        return "No error stack traces available at this level. Drill down to individual tests to find stack traces."; 
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    @Override
    public String getErrorDetails() {
        return "No error details available at this level. Drill down to individual tests to find details.";
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    @Override
    public boolean isPassed() {
       return (getFailCount() == 0);
    }

    @Override
    public Collection<PackageResultExt> getChildren() {
        return byPackages.values();
    }

    /**
     * Whether this test result has children.
     */
    @Override
    public boolean hasChildren() {
        return !suites.isEmpty(); 
    }

    public Collection<SuiteResultExt> getSuites() {
        return suites;
    }

    @Override
    public String getName() {
        return "junit";
    }

    public PackageResultExt byPackage(String packageName) {
        return byPackages.get(packageName);
    }

    public SuiteResultExt getSuite(String name) {
        return suitesByName.get(name);
    }
    
     @Override
     public void setParentAction(AbstractTestResultActionExt action) {
        this.parentAction = action;
        tally(); // I want to be sure to inform our children when we get an action. 
     }

     @Override
     public AbstractTestResultActionExt getParentAction() {
         return this.parentAction;
     }
     
    /**
     * Recount my children.
     */
    @Override
    public void tally() {
        /// Empty out data structures
        // TODO: free children? memmory leak? 
        suitesByName = new HashMap<String,SuiteResultExt>();
        failedTests = new ArrayList<CaseResultExt>();
        byPackages = new TreeMap<String,PackageResultExt>();

        totalTests = 0;
        skippedTests = 0;

        // Ask all of our children to tally themselves
        for (SuiteResultExt s : suites) {
            s.setParent(this); // kluge to prevent double-counting the results
            suitesByName.put(s.getName(),s);
            List<CaseResultExt> cases = s.getCases();

            for (CaseResultExt cr: cases) {
                cr.setParentAction(this.parentAction);
                cr.setParentSuiteResult(s);
                cr.tally();
                String pkg = cr.getPackageName(), spkg = safe(pkg);
                PackageResultExt pr = byPackage(spkg);
                if(pr==null)
                    byPackages.put(spkg,pr=new PackageResultExt(this,pkg));
                pr.add(cr);
            }
        }

        for (PackageResultExt pr : byPackages.values()) {
            pr.tally();
            skippedTests += pr.getSkipCount();
            failedTests.addAll(pr.getFailedTests());
            totalTests += pr.getTotalCount();
        }
    }

    /**
     * Builds up the transient part of the data structure
     * from results {@link #parse(File) parsed} so far.
     *
     * <p>
     * After the data is frozen, more files can be parsed
     * and then freeze can be called again.
     */
    public void freeze(TestResultActionExt parent) {
        this.parentAction = parent;
        if(suitesByName==null) {
            // freeze for the first time
            suitesByName = new HashMap<String,SuiteResultExt>();
            totalTests = 0;
            failedTests = new ArrayList<CaseResultExt>();
            byPackages = new TreeMap<String,PackageResultExt>();
        }

        for (SuiteResultExt s : suites) {
            if(!s.freeze(this))      // this is disturbing: has-a-parent is conflated with has-been-counted
                continue;

            suitesByName.put(s.getName(),s);

            totalTests += s.getCases().size();
            for(CaseResultExt cr : s.getCases()) {
                if(cr.isSkipped())
                    skippedTests++;
                else if(!cr.isPassed())
                    failedTests.add(cr);

                String pkg = cr.getPackageName(), spkg = safe(pkg);
                PackageResultExt pr = byPackage(spkg);
                if(pr==null)
                    byPackages.put(spkg,pr=new PackageResultExt(this,pkg));
                pr.add(cr);
            }
        }

        Collections.sort(failedTests,CaseResultExt.BY_AGE);

        for (PackageResultExt pr : byPackages.values())
            pr.freeze();
    }

    private static final long serialVersionUID = 1L;
    private static final boolean checkTimestamps = true; // TODO: change to System.getProperty  

}

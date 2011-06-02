/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Seiji Sogabe, Tom Huybrechts, Yahoo!, Inc.
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

import org.dom4j.Element;
import org.kohsuke.stapler.export.Exported;

/**
 * One test result.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CaseResult extends CaseResultExt {

    CaseResult(SuiteResultExt parent, Element testCase, String testClassName, boolean keepLongStdio) {
        super(parent, testCase, testClassName, keepLongStdio);
    }

    /**
     * Used to create a fake failure, when Hudson fails to load data from XML files.
     */
    CaseResult(SuiteResultExt parent, String testName, String errorStackTrace) {
        super(parent, testName, errorStackTrace);
    }

    /**
     * Gets the name of the test, which is returned from {@code TestCase.getName()}
     *
     * <p>
     * Note that this may contain any URL-unfriendly character.
     */
    @Exported(visibility = 999)
    public @Override
    String getName() {
        return super.getName();
    }

    /**
     * Gets the duration of the test, in seconds
     */
    @Exported(visibility = 9)
    @Override
    public float getDuration() {
        return super.getDuration();
    }

    /**
     * Gets the class name of a test class.
     */
    @Exported(visibility = 9)
    @Override
    public String getClassName() {
        return super.getClassName();
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Exported(visibility = 9)
    @Override
    public int getFailedSince() {
        return super.getFailedSince();
    }

    /**
     * Gets the number of consecutive builds (including this)
     * that this test case has been failing.
     */
    @Exported(visibility = 9)
    @Override
    public int getAge() {
        return super.getAge();
    }

    /**
     * The stdout of this test.
     *
     * <p>
     * Depending on the tool that produced the XML report, this method works somewhat inconsistently.
     * With some tools (such as Maven surefire plugin), you get the accurate information, that is
     * the stdout from this test case. With some other tools (such as the JUnit task in Ant), this
     * method returns the stdout produced by the entire test suite.
     *
     * <p>
     * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
     * @since 1.294
     */
    @Exported
    @Override
    public String getStdout() {
        return super.getStdout();
    }

    /**
     * The stderr of this test.
     *
     * @see #getStdout()
     * @since 1.294
     */
    @Exported
    @Override
    public String getStderr() {
        return super.getStderr();
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    @Exported
    @Override
    public String getErrorStackTrace() {
        return super.getErrorStackTrace();
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    @Exported
    @Override
    public String getErrorDetails() {
        return super.getErrorDetails();
    }

    /**
     * Tests whether the test was skipped or not.  TestNG allows tests to be
     * skipped if their dependencies fail or they are part of a group that has
     * been configured to be skipped.
     * @return true if the test was not executed, false otherwise.
     */
    @Exported(visibility = 9)
    @Override
    public boolean isSkipped() {
        return super.isSkipped();
    }

    @Exported(name = "status", visibility = 9) // because stapler notices suffix 's' and remove it
    @Override
    public Status getStatus() {
        return super.getStatus();
    }
}

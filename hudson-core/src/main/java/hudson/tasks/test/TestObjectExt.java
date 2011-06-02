/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Tom Huybrechts, Yahoo!, Inc.
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

import hudson.UtilExt;
import hudson.model.*;
import hudson.tasks.junit.HistoryExt;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestResultActionExt;

import com.google.common.collect.MapMaker;

import java.util.*;
import java.util.logging.Logger;

/**
 * Base class for all test result objects.
 * For compatibility with code that expects this class to be in hudson.tasks.junit,
 * we've created a pure-abstract class, hudson.tasks.junit.TestObject. That
 * stub class is deprecated; instead, people should use this class.  
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class TestObjectExt extends hudson.tasks.junit.TestObjectExt {

    private static final Logger LOGGER = Logger.getLogger(TestObjectExt.class.getName());
    private volatile transient String id;

    public abstract AbstractBuildExt<?, ?> getOwner();

    /**
     * Reverse pointer of {@link TabulatedResult#getChildren()}.
     */
    public abstract TestObjectExt getParent();

    @Override
    public final String getId() {
        if (id == null) {
            StringBuilder buf = new StringBuilder();
            buf.append(getSafeName());

            TestObjectExt parent = getParent();
            if (parent != null) {
                String parentId = parent.getId();
                if ((parentId != null) && (parentId.length() > 0)) {
                    buf.insert(0, '/');
                    buf.insert(0, parent.getId());
                }
            }
            id = buf.toString();
        }
        return id;
    }

    /**
     * Returns url relative to TestResult
     */
    @Override
    public String getUrl() {
        return '/' + getId();
    }

    /**
     * Returns the top level test result data.
     *
     * @deprecated This method returns a JUnit specific class. Use
     * {@link #getTopLevelTestResult()} instead for a more general interface.
     * @return
     */
    @Override
    public hudson.tasks.junit.TestResultExt getTestResult() {
        TestObjectExt parent = getParent();

        return (parent == null ? null : getParent().getTestResult());
    }

    /**
     * Returns the top level test result data.
     * 
     * @return
     */    
    public TestResult getTopLevelTestResult() {
        TestObjectExt parent = getParent();

        return (parent == null ? null : getParent().getTopLevelTestResult());
    }
    

    /**
     * Subclasses may override this method if they are
     * associated with a particular subclass of
     * AbstractTestResultAction. 
     *
     * @return  the test result action that connects this test result to a particular build
     */
    @Override
    public AbstractTestResultActionExt getTestResultAction() {
        AbstractBuildExt<?, ?> owner = getOwner();
        if (owner != null) {
            return owner.getAction(AbstractTestResultActionExt.class);
        } else {
            LOGGER.warning("owner is null when trying to getTestResultAction.");
            return null;
        }
    }

    /**
     * Get a list of all TestActions associated with this TestObject. 
     * @return
     */
    @Override
    public List<TestAction> getTestActions() {
        AbstractTestResultActionExt atra = getTestResultAction();
        if ((atra != null) && (atra instanceof TestResultActionExt)) {
            TestResultActionExt tra = (TestResultActionExt) atra;
            return tra.getActions(this);
        } else {
            return new ArrayList<TestAction>();
        }
    }

    /**
     * Gets a test action of the class passed in. 
     * @param klazz
     * @param <T> an instance of the class passed in
     * @return
     */
    @Override
    public <T> T getTestAction(Class<T> klazz) {
        for (TestAction action : getTestActions()) {
            if (klazz.isAssignableFrom(action.getClass())) {
                return klazz.cast(action);
            }
        }
        return null;
    }

    /**
     * Gets the counterpart of this {@link TestResult} in the previous run.
     *
     * @return null if no such counter part exists.
     */
    public abstract TestResult getPreviousResult();

    /**
     * Gets the counterpart of this {@link TestResult} in the specified run.
     *
     * @return null if no such counter part exists.
     */
    public abstract TestResult getResultInBuild(AbstractBuildExt<?, ?> build);

    /**
     * Find the test result corresponding to the one identified by <code>id></code>
     * withint this test result.
     *
     * @param id The path to the original test result
     * @return A corresponding test result, or null if there is no corresponding
     * result.
     */
    public abstract TestResult findCorrespondingResult(String id);

    /**
     * Time took to run this test. In seconds.
     */
    public abstract float getDuration();

    /**
     * Returns the string representation of the {@link #getDuration()}, in a
     * human readable format.
     */
    @Override
    public String getDurationString() {
        return UtilExt.getTimeSpanString((long) (getDuration() * 1000));
    }

    @Override
    public String getDescription() {
        AbstractTestResultActionExt action = getTestResultAction();
        if (action != null) {
            return action.getDescription(this);
        }
        return "";
    }

    @Override
    public void setDescription(String description) {
        AbstractTestResultActionExt action = getTestResultAction();
        if (action != null) {
            action.setDescription(this, description);
        }
    }

    /**
     * Gets the name of this object.
     */
    @Override
    public/* abstract */ String getName() {
        return "";
    }

    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    @Override
    public String getSafeName() {
        return safe(getName());
    }

    @Override
    public String getSearchUrl() {
        return getSafeName();
    }

    /**
     * #2988: uniquifies a {@link #getSafeName} amongst children of the parent.
     */
    protected final synchronized String uniquifyName(
            Collection<? extends TestObjectExt> siblings, String base) {
        String uniquified = base;
        int sequence = 1;
        for (TestObjectExt sibling : siblings) {
            if (sibling != this && uniquified.equals(UNIQUIFIED_NAMES.get(sibling))) {
                uniquified = base + '_' + ++sequence;
            }
        }
        UNIQUIFIED_NAMES.put(this, uniquified);
        return uniquified;
    }
    private static final Map<TestObjectExt, String> UNIQUIFIED_NAMES = new MapMaker().weakKeys().makeMap();

    /**
     * Replaces URL-unsafe characters.
     */
    public static String safe(String s) {
        // 3 replace calls is still 2-3x faster than a regex replaceAll
        return s.replace('/', '_').replace('\\', '_').replace(':', '_');
    }

    /**
     * Gets the total number of passed tests.
     */
    public abstract int getPassCount();

    /**
     * Gets the total number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of skipped tests.
     */
    public abstract int getSkipCount();

    /**
     * Gets the total number of tests.
     */
    @Override
    public int getTotalCount() {
        return getPassCount() + getFailCount() + getSkipCount();
    }

    @Override
    public HistoryExt getHistory() {
        return new HistoryExt(this);
    }

    private static final long serialVersionUID = 1L;
}

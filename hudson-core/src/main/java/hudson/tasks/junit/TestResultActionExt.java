/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Yahoo!, Inc.
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

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.model.AbstractBuildExt;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.test.AbstractTestResultActionExt;
import hudson.tasks.test.TestObjectExt;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Action} that displays the JUnit test result.
 *
 * <p>
 * The actual test reports are isolated by {@link WeakReference}
 * so that it doesn't eat up too much memory.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultActionExt extends AbstractTestResultActionExt<TestResultActionExt> {
    private transient WeakReference<TestResultExt> result;

    // Hudson < 1.25 didn't set these fields, so use Integer
    // so that we can distinguish between 0 tests vs not-computed-yet.
    private int failCount;
    private int skipCount;
    private Integer totalCount;
    private List<Data> testData = new ArrayList<Data>();

    public TestResultActionExt(AbstractBuildExt owner, TestResultExt result, BuildListener listener) {
        super(owner);
        setResult(result, listener);
    }

    /**
     * Overwrites the {@link TestResult} by a new data set.
     */
    public synchronized void setResult(TestResultExt result, BuildListener listener) {
        result.freeze(this);

        totalCount = result.getTotalCount();
        failCount = result.getFailCount();
        skipCount = result.getSkipCount();

        // persist the data
        try {
            getDataFile().write(result);
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError("Failed to save the JUnit test result"));
        }

        this.result = new WeakReference<TestResultExt>(result);
    }

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM,new File(owner.getRootDir(), "junitResult.xml"));
    }

    public synchronized TestResultExt getResult() {
        TestResultExt r;
        if(result==null) {
            r = load();
            result = new WeakReference<TestResultExt>(r);
        } else {
            r = result.get();
        }

        if(r==null) {
            r = load();
            result = new WeakReference<TestResultExt>(r);
        }
        if(totalCount==null) {
            totalCount = r.getTotalCount();
            failCount = r.getFailCount();
            skipCount = r.getSkipCount();
        }
        return r;
    }

    @Override
    public int getFailCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return failCount;
    }

    @Override
    public int getSkipCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return skipCount;
    }

    @Override
    public int getTotalCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return totalCount;
    }

     @Override
     public List<CaseResultExt> getFailedTests() {
          return getResult().getFailedTests();
     }

    /**
     * Loads a {@link TestResult} from disk.
     */
    private TestResultExt load() {
        TestResultExt r;
        try {
            r = (TestResultExt)getDataFile().read();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load "+getDataFile(),e);
            r = new TestResultExt();   // return a dummy
        }
        r.freeze(this);
        return r;
    }
    
    public List<TestAction> getActions(TestObjectExt object) {
    	List<TestAction> result = new ArrayList<TestAction>();
	// Added check for null testData to avoid NPE from issue 4257.
	if (testData!=null) {
        for (Data data : testData) {
            result.addAll(data.getTestAction(object));
        }
    }
	return Collections.unmodifiableList(result);
	
    }
    public void setData(List<Data> testData) {
	this.testData = testData;
    }

    /**
     * Resolves {@link TestAction}s for the given {@link TestObject}.
     *
     * <p>
     * This object itself is persisted as a part of {@link AbstractBuildExt}, so it needs to be XStream-serializable.
     *
     * @see TestDataPublisher
     */
    public static abstract class Data {
    	/**
    	 * Returns all TestActions for the testObject.
         * 
         * @return
         *      Can be empty but never null. The caller must assume that the returned list is read-only.
    	 */
    	public abstract List<? extends TestAction> getTestAction(hudson.tasks.junit.TestObjectExt testObject);
    }

    public Object readResolve() {
        super.readResolve(); // let it do the post-deserialization work
    	if (testData == null) {
    		testData = new ArrayList<Data>();
    	}
    	
    	return this;
    }
    
    private static final Logger logger = Logger.getLogger(TestResultActionExt.class.getName());

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResultExt.class);
        XSTREAM.alias("suite",SuiteResultExt.class);
        XSTREAM.alias("case",CaseResultExt.class);
        XSTREAM.registerConverter(new HeapSpaceStringConverter(),100);
    }

}

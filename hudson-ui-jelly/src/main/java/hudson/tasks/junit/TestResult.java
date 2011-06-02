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

import org.apache.tools.ant.DirectoryScanner;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * Root of all the test results for one build.
 *
 * @author Kohsuke Kawaguchi
 */
public final class TestResult extends TestResultExt {
     
    
    /**
     * Creates an empty result.
     */
    public TestResult() {
        super();
    }

    @Deprecated
    public TestResult(long buildTime, DirectoryScanner results) throws IOException {
        this(buildTime, results, false);
    }

    /**
     * Collect reports from the given {@link DirectoryScanner}, while
     * filtering out all files that were created before the given time.
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     * @since 1.358
     */
    public TestResult(long buildTime, DirectoryScanner results, boolean keepLongStdio) throws IOException {
        super(buildTime, results, keepLongStdio);
    }
    
    

    @Exported(visibility=999)
    @Override
    public float getDuration() {
        return super.getDuration(); 
    }
    
    @Exported(visibility=999)
    @Override
    public int getPassCount() {
        return super.getPassCount(); 
    }

    @Exported(visibility=999)
    @Override
    public int getFailCount() {
        return super.getFailCount(); 
    }

    @Exported(visibility=999)
    @Override
    public int getSkipCount() {
        return super.getSkipCount(); 
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Xavier Le Vourch, Tom Huybrechts, Yahoo!, Inc.
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

import hudson.tasks.test.TestObjectExt;
import hudson.util.IOException2;
import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Result of one test suite.
 *
 * <p>
 * The notion of "test suite" is rather arbitrary in JUnit ant task.
 * It's basically one invocation of junit.
 *
 * <p>
 * This object is really only used as a part of the persisted
 * object tree.
 *
 * @author Kohsuke Kawaguchi
 */
public class SuiteResultExt implements Serializable {
    private final String file;
    private final String name;
    private final String stdout;
    private final String stderr;
    private float duration;
    /**
     * The 'timestamp' attribute of  the test suite.
     * AFAICT, this is not a required attribute in XML, so the value may be null.
     */
    private String timestamp;

    /**
     * All test cases.
     */
    private final List<CaseResultExt> cases = new ArrayList<CaseResultExt>();
    private transient hudson.tasks.junit.TestResultExt parent;

    SuiteResultExt(String name, String stdout, String stderr) {
        this.name = name;
        this.stderr = stderr;
        this.stdout = stdout;
        this.file = null;
    }

    /**
     * Parses the JUnit XML file into {@link SuiteResult}s.
     * This method returns a collection, as a single XML may have multiple &lt;testsuite>
     * elements wrapped into the top-level &lt;testsuites>.
     */
    static List<SuiteResultExt> parse(File xmlReport, boolean keepLongStdio) throws DocumentException, IOException {
        List<SuiteResultExt> r = new ArrayList<SuiteResultExt>();

        // parse into DOM
        SAXReader saxReader = new SAXReader();
        // install EntityResolver for resolving DTDs, which are in files created by TestNG.
        XMLEntityResolver resolver = new XMLEntityResolver();
        saxReader.setEntityResolver(resolver);
        Document result = saxReader.read(xmlReport);
        Element root = result.getRootElement();

        getTestSuites(root, r, xmlReport, keepLongStdio);

        return r;
    }

    /**
     * Gets the "testsuite" elements that contain at least one "testcase" element.
     * Finds all the elements recursively in order to find nested "testsuite" elements.
     * Bug 6546 
     * @see http://issues.hudson-ci.org/browse/HUDSON-6545
     * @param element XML element to examine
     * @param r List of SuiteResult
     * @param xmlReport A Junit XML report file
     * @param keepLongStdio if true, retain a suite's complete stdout/stderr even if this is huge and the suite passed
     */
    static private void getTestSuites(Element element, List<SuiteResultExt> r, File xmlReport, boolean keepLongStdio) throws DocumentException, IOException {
        if(element.elements("testcase").size() != 0) {
            r.add(new SuiteResultExt(xmlReport, element, keepLongStdio));
        }
        for (Element suite : (List<Element>)element.elements("testsuite")) {
            getTestSuites(suite, r, xmlReport, keepLongStdio);
        }
    }


    /**
     * @param xmlReport
     *      A JUnit XML report file whose top level element is 'testsuite'.
     * @param suite
     *      The parsed result of {@code xmlReport}
     */
    private SuiteResultExt(File xmlReport, Element suite, boolean keepLongStdio) throws DocumentException, IOException {
    	this.file = xmlReport.getAbsolutePath();
        String name = suite.attributeValue("name");
        if(name==null)
            // some user reported that name is null in their environment.
            // see http://www.nabble.com/Unexpected-Null-Pointer-Exception-in-Hudson-1.131-tf4314802.html
            name = '('+xmlReport.getName()+')';
        else {
            String pkg = suite.attributeValue("package");
            if(pkg!=null&& pkg.length()>0)   name=pkg+'.'+name;
        }
        this.name = TestObjectExt.safe(name);
        this.timestamp = suite.attributeValue("timestamp");

        Element ex = suite.element("error");
        if(ex!=null) {
            // according to junit-noframes.xsl l.229, this happens when the test class failed to load
            addCase(new CaseResultExt(this, suite, "<init>", keepLongStdio));
        }

        for (Element e : (List<Element>)suite.elements("testcase")) {
            // http://issues.hudson-ci.org/browse/HUDSON-1233 indicates that
            // when <testsuites> is present, we are better off using @classname on the
            // individual testcase class.

            // http://issues.hudson-ci.org/browse/HUDSON-1463 indicates that
            // @classname may not exist in individual testcase elements. We now
            // also test if the testsuite element has a package name that can be used
            // as the class name instead of the file name which is default.
            String classname = e.attributeValue("classname");
            if (classname == null) {
                classname = suite.attributeValue("name");
            }

            // http://issues.hudson-ci.org/browse/HUDSON-1233 and
            // http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
            // are at odds with each other --- when both are present,
            // one wants to use @name from <testsuite>,
            // the other wants to use @classname from <testcase>.

            addCase(new CaseResultExt(this, e, classname, keepLongStdio));
        }

        String stdout = suite.elementText("system-out");
        String stderr = suite.elementText("system-err");
        if (stdout==null && stderr==null) {
            // Surefire never puts stdout/stderr in the XML. Instead, it goes to a separate file
            Matcher m = SUREFIRE_FILENAME.matcher(xmlReport.getName());
            if (m.matches()) {
                // look for ***-output.txt from TEST-***.xml
                File mavenOutputFile = new File(xmlReport.getParentFile(),m.group(1)+"-output.txt");
                if (mavenOutputFile.exists()) {
                    try {
                        stdout = FileUtils.readFileToString(mavenOutputFile);
                    } catch (IOException e) {
                        throw new IOException2("Failed to read "+mavenOutputFile,e);
                    }
                }
            }
        }

        this.stdout = CaseResultExt.possiblyTrimStdio(cases, keepLongStdio, stdout);
        this.stderr = CaseResultExt.possiblyTrimStdio(cases, keepLongStdio, stderr);
    }

    /*package*/ void addCase(CaseResultExt cr) {
        cases.add(cr);
        duration += cr.getDuration(); 
    }

    public String getName() {
        return name;
    }

    public float getDuration() {
        return duration; 
    }

    /**
     * The stdout of this test.
     *
     * @since 1.281
     * @see CaseResult#getStdout()
     */
    public String getStdout() {
        return stdout;
    }

    /**
     * The stderr of this test.
     * 
     * @since 1.281
     * @see CaseResult#getStderr()
     */
    public String getStderr() {
        return stderr;
    }
    
    /**
     * The absolute path to the original test report. OS-dependent.
     */
    public String getFile() {
		return file;
	}

	public hudson.tasks.junit.TestResultExt getParent() {
        return parent;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public List<CaseResultExt> getCases() {
        return cases;
    }

    public SuiteResultExt getPreviousResult() {
        hudson.tasks.test.TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        if(pr instanceof hudson.tasks.junit.TestResultExt)
            return ((hudson.tasks.junit.TestResultExt)pr).getSuite(name);
        return null;
    }

    /**
     * Returns the {@link CaseResult} whose {@link CaseResult#getName()}
     * is the same as the given string.
     *
     * <p>
     * Note that test name needs not be unique.
     */
    public CaseResultExt getCase(String name) {
        for (CaseResultExt c : cases) {
            if(c.getName().equals(name))
                return c;
        }
        return null;
    }
    
	public Set<String> getClassNames() {
		Set<String> result = new HashSet<String>();
		for (CaseResultExt c : cases) {
			result.add(c.getClassName());
		}
		return result;
	}

    /** KLUGE. We have to call this to prevent freeze()
     * from calling c.freeze() on all its children,
     * because that in turn calls c.getOwner(),
     * which requires a non-null parent. 
     * @param parent
     */
    void setParent(hudson.tasks.junit.TestResultExt parent) {
        this.parent = parent;
    }

    /*package*/ boolean freeze(hudson.tasks.junit.TestResultExt owner) {
        if(this.parent!=null)
            return false;   // already frozen

        this.parent = owner;
        for (CaseResultExt c : cases)
            c.freeze(this);
        return true;
    }

    private static final long serialVersionUID = 1L;

    private static final Pattern SUREFIRE_FILENAME = Pattern.compile("TEST-(.+)\\.xml");
}

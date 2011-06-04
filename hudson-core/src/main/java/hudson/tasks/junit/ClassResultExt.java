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

import hudson.model.AbstractBuildExt;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.TestObjectExt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cumulative test result of a test class.
 *
 * @author Kohsuke Kawaguchi
 */
public class ClassResultExt extends TabulatedResult implements Comparable<ClassResultExt> {
    private final String className; // simple name

    private final List<CaseResultExt> cases = new ArrayList<CaseResultExt>();

    private int passCount,failCount,skipCount;
    
    private float duration; 

    private final PackageResultExt parent;

    ClassResultExt(PackageResultExt parent, String className) {
        this.parent = parent;
        this.className = className;
    }

    @Override
    public AbstractBuildExt<?, ?> getOwner() {
        return (parent==null ? null: parent.getOwner()); 
    }

    public PackageResultExt getParent() {
        return parent;
    }

    @Override
    public ClassResultExt getPreviousResult() {
        if(parent==null)   return null;
        TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        if(pr instanceof PackageResultExt) {
            return ((PackageResultExt)pr).getClassResult(getName());
    }
        return null;
    }

    @Override
    public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
        String myID = safe(getName());
        int base = id.indexOf(myID);
        String caseName;
        if (base > 0) {
            int caseNameStart = base + myID.length() + 1;
            caseName = id.substring(caseNameStart);
        } else {
            caseName = id;
    }

        CaseResultExt child = getCaseResult(caseName);
        if (child != null) {
            return child;
        }

        return null;
    }

    @Override
    public String getTitle() {
        return Messages.ClassResult_getTitle(getName());
    }

    @Override
    public String getChildTitle() {
        return "Class Reults"; 
    }

    @Override
    public String getName() {
        int idx = className.lastIndexOf('.');
        if(idx<0)       return className;
        else            return className.substring(idx+1);
    }

    public @Override String getSafeName() {
        return uniquifyName(parent.getChildren(), safe(getName()));
    }
    
    public CaseResultExt getCaseResult(String name) {
        for (CaseResultExt c : cases) {
            if(c.getSafeName().equals(name))
                return c;
        }
        return null;
    }

    public List<CaseResultExt> getChildren() {
        return cases;
    }

    public boolean hasChildren() {
        return ((cases != null) && (cases.size() > 0));
    }

    // TODO: wait for stapler 1.60     @Exported
    @Override
    public float getDuration() {
        return duration; 
    }
    
    @Override
    public int getPassCount() {
        return passCount;
    }

    @Override
    public int getFailCount() {
        return failCount;
    }

    @Override
    public int getSkipCount() {
        return skipCount;
    }

    public void add(CaseResultExt r) {
        cases.add(r);
    }

    /**
     * Recount my children.
     */
    @Override
    public void tally() {
        passCount=failCount=skipCount=0;
        duration=0;
        for (CaseResultExt r : cases) {
            r.setClass(this);
            if (r.isSkipped()) {
                skipCount++;
            }
            else if(r.isPassed()) {
                passCount++;
            }
            else {
                failCount++;
            }
            duration += r.getDuration();
        }
    }


    void freeze() {
        passCount=failCount=skipCount=0;
        duration=0;
        for (CaseResultExt r : cases) {
            r.setClass(this);
            if (r.isSkipped()) {
                skipCount++;
            }
            else if(r.isPassed()) {
                passCount++;
            }
            else {
                failCount++;
            }
            duration += r.getDuration();
        }
        Collections.sort(cases);
    }

    public String getClassName() {
    	return className;
    }

    public int compareTo(ClassResultExt that) {
        return this.className.compareTo(that.className);
    }

    public String getDisplayName() {
        return getName();
    }
    
    public String getFullName() {
    	return getParent().getDisplayName() + "." + className;
    }

     
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Red Hat, Inc., Yahoo!, Inc.
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

import hudson.model.AbstractBuildExt;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.List;

/**
 * {@link AbstractTestResultAction} that aggregates all the test results
 * from the corresponding {@link AbstractBuildExt}s.
 *
 * <p>
 * (This has nothing to do with {@link AggregatedTestResultPublisher}, unfortunately)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class AggregatedTestResultAction extends AggregatedTestResultActionExt {
     
     
    public AggregatedTestResultAction(AbstractBuildExt owner) {
        super(owner);
    }


    /**
     * Data-binding bean for the remote API.
     */
    @ExportedBean(defaultVisibility=2)
    public static final class ChildReport extends AggregatedTestResultActionExt.ChildReportExt{
         

        public ChildReport(AbstractBuildExt<?, ?> child, AbstractTestResultActionExt result) {
            super(child, result);
        }
        
        @Exported
        public Object getResult(){
            return result;
        }
        
        @Exported
        public AbstractBuildExt<?,?> getChild(){
            return child;
        }
    }

    /**
     * Mainly for the remote API. Expose results from children.
     */
    @Exported(inline=true)
    @Override
    public List<ChildReportExt> getChildReports() {
         return super.getChildReports();
    }

}

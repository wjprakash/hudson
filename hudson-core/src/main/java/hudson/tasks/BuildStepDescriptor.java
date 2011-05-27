/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.tasks;

import hudson.model.Describable;
import hudson.model.DescriptorExt;
import hudson.model.AbstractProjectExt;
import hudson.model.HudsonExt;
import hudson.model.AbstractProjectExt.AbstractProjectDescriptorExt;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link DescriptorExt} for {@link Builder} and {@link Publisher}.
 *
 * <p>
 * For compatibility reasons, plugins developed before 1.150 may not extend from this descriptor type.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 */
public abstract class BuildStepDescriptor<T extends BuildStep & Describable<T>> extends DescriptorExt<T> {
    protected BuildStepDescriptor(Class<? extends T> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link BuildStep} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected BuildStepDescriptor() {
    }

    /**
     * Returns true if this task is applicable to the given project.
     *
     * @return
     *      true to allow user to configure this post-promotion task for the given project.
     * @see AbstractProjectDescriptorExt#isApplicable(DescriptorExt) 
     */
    public abstract boolean isApplicable(Class<? extends AbstractProjectExt> jobType);


    /**
     * Filters a descriptor for {@link BuildStep}s by using {@link BuildStepDescriptor#isApplicable(Class)}.
     */
    public static <T extends BuildStep&Describable<T>>
    List<DescriptorExt<T>> filter(List<DescriptorExt<T>> base, Class<? extends AbstractProjectExt> type) {
        // descriptor of the project
        DescriptorExt pd = HudsonExt.getInstance().getDescriptor((Class) type);

        List<DescriptorExt<T>> r = new ArrayList<DescriptorExt<T>>(base.size());
        for (DescriptorExt<T> d : base) {
            if (pd instanceof AbstractProjectDescriptorExt && !((AbstractProjectDescriptorExt)pd).isApplicable(d))
                continue;

            if (d instanceof BuildStepDescriptor) {
                BuildStepDescriptor<T> bd = (BuildStepDescriptor<T>) d;
                if(!bd.isApplicable(type))  continue;
                r.add(bd);
            } else {
                // old plugins built before 1.150 may not implement BuildStepDescriptor
                r.add(d);
            }
        }
        return r;
    }
}

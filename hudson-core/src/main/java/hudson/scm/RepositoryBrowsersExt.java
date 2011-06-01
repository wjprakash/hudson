/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Stephen Connolly
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
package hudson.scm;

import hudson.model.DescriptorExt;
import hudson.util.DescriptorList;
import hudson.Extension;

import java.util.ArrayList;
import java.util.List;


/**
 * List of all installed {@link RepositoryBrowsers}.
 *
 * @author Kohsuke Kawaguchi
 */
public class RepositoryBrowsersExt {
    /**
     * List of all installed {@link RepositoryBrowsers}.
     *
     * @deprecated as of 1.286.
     *      Use {@link RepositoryBrowser#all()} for read access and {@link Extension} for registration.
     */
    public static final List<DescriptorExt<RepositoryBrowserExt<?>>> LIST = new DescriptorList<RepositoryBrowserExt<?>>((Class)RepositoryBrowserExt.class);

    /**
     * Only returns those {@link RepositoryBrowser} descriptors that extend from the given type.
     */
    public static List<DescriptorExt<RepositoryBrowserExt<?>>> filter(Class<? extends RepositoryBrowserExt> t) {
        List<DescriptorExt<RepositoryBrowserExt<?>>> r = new ArrayList<DescriptorExt<RepositoryBrowserExt<?>>>();
        for (DescriptorExt<RepositoryBrowserExt<?>> d : RepositoryBrowserExt.all())
            if(d.isSubTypeOf(t))
                r.add(d);
        return r;
    }

}

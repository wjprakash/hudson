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
package hudson.scm;

import hudson.model.AbstractBuildExt;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Iterator;

/**
 * Represents SCM change list.
 *
 * <p>
 * Use the "index" view of this object to render the changeset detail page,
 * and use the "digest" view of this object to render the summary page.
 * For the change list at project level, see {@link SCM}.
 *
 * <p>
 * {@link Iterator} is expected to return recent changes first.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean(defaultVisibility=999)
public abstract class ChangeLogSet<T extends ChangeLogSet.Entry> extends ChangeLogSetExt<T> {

     
    protected ChangeLogSet(AbstractBuildExt<?, ?> build) {
        super(build);
    }

    /**
     * Optional identification of the kind of SCM being used.
     * @return a short token, such as the SCM's main CLI executable name
     * @since 1.284
     */
    @Exported
    @Override
    public String getKind() {
        return super.getKind();
    }
 

    @ExportedBean(defaultVisibility=999)
    public static abstract class Entry extends  ChangeLogSetExt.Entry{
         
    }
    
}

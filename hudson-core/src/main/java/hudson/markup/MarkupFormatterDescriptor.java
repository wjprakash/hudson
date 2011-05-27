/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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
package hudson.markup;

import hudson.DescriptorExtensionListExt;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;

/**
 * {@link DescriptorExt} for {@link MarkupFormatter}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.391
 */
public abstract class MarkupFormatterDescriptor extends DescriptorExt<MarkupFormatter> {
    /**
     * Returns all the registered {@link MarkupFormatterDescriptor}s.
     */
    public static DescriptorExtensionListExt<MarkupFormatter,MarkupFormatterDescriptor> all() {
        return HudsonExt.getInstance().getDescriptorList(MarkupFormatter.class);
    }
}

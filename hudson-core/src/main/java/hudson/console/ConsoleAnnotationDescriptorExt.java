/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.console;

import hudson.DescriptorExtensionListExt;
import hudson.ExtensionPoint;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;

import java.net.URL;

/**
 * DescriptorExt for {@link ConsoleNote}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public abstract class ConsoleAnnotationDescriptorExt extends DescriptorExt<ConsoleNote<?>> implements ExtensionPoint {
    public ConsoleAnnotationDescriptorExt(Class<? extends ConsoleNote<?>> clazz) {
        super(clazz);
    }

    public ConsoleAnnotationDescriptorExt() {
    }

    /**
     * {@inheritDoc}
     *
     * Users use this name to enable/disable annotations.
     */
    public abstract String getDisplayName();

    /**
     * Returns true if this descriptor has a JavaScript to be inserted on applicable console page.
     */
    public boolean hasScript() {
        return hasResource("/script.js") !=null;
    }

    /**
     * Returns true if this descriptor has a stylesheet to be inserted on applicable console page.
     */
    public boolean hasStylesheet() {
        return hasResource("/style.css") !=null;
    }

    protected URL hasResource(String name) {
        return clazz.getClassLoader().getResource(clazz.getName().replace('.','/').replace('$','/')+ name);
    }

    /**
     * Returns all the registered {@link ConsoleAnnotationDescriptor} descriptors.
     */
    public static DescriptorExtensionListExt<ConsoleNote<?>,ConsoleAnnotationDescriptorExt> all() {
        return (DescriptorExtensionListExt)HudsonExt.getInstance().getDescriptorList(ConsoleNote.class);
    }
}

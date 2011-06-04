/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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
package hudson.model;

import hudson.ExtensionPoint;
import hudson.PluginExt;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.DescriptorListExt;

import java.util.List;

/**
 * Participates in the rendering of HTML pages for all pages of HudsonExt.
 *
 * <p>
 * This class provides a few hooks to augument the HTML generation process of HudsonExt, across
 * all the HTML pages that HudsonExt delivers.
 *
 * <p>
 * For example, if you'd like to add a Google Analytics stat to HudsonExt, then you need to inject
 * a small script fragment to all HudsonExt pages. This extension point provides a means to do that.
 *
 * <h2>Life-cycle</h2>
 * <p>
 * {@link PluginExt}s that contribute this extension point
 * should implement a new decorator and put {@link Extension} on the class.
 *
 * <h2>Associated Views</h2>
 * <h4>global.jelly</h4>
 * <p>
 * If this extension point needs to expose a global configuration, write this jelly page.
 * See {@link DescriptorExt} for more about this. Optional.
 *
 * <h4>footer.jelly</h4>
 * <p>
 * This page is added right before the &lt;/body> tag. Convenient place for adding tracking beacons, etc.
 *
 * <h4>header.jelly</h4>
 * <p>
 * This page is added right before the &lt;/head> tag. Convenient place for additional stylesheet,
 * &lt;meta> tags, etc.
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.235
 */
public abstract class PageDecorator extends DescriptorExt<PageDecorator> implements ExtensionPoint, Describable<PageDecorator> {
    /**
     * @param yourClass
     *      pass-in "this.getClass()" (except that the constructor parameters cannot use 'this',
     *      so you'd have to hard-code the class name.
     */
    protected PageDecorator(Class<? extends PageDecorator> yourClass) {
        super(yourClass);
    }

// this will never work because DescriptorExt and Describable are the same thing.
//    protected PageDecorator() {
//    }

    public final DescriptorExt<PageDecorator> getDescriptor() {
        return this;
    }

    /**
     * Unless this object has additional web presence, display name is not used at all.
     * So default to "".
     */
    public String getDisplayName() {
        return "";
    }

    /**
     * Obtains the URL of this object, excluding the context path.
     *
     * <p>
     * Every {@link PageDecorator} is bound to URL via {@link HudsonExt#getDescriptor()}.
     * This method returns such an URL.
     */
    public final String getUrl() {
        return "descriptor/"+clazz.getName();
    }

    /**
     * All the registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    public static final List<PageDecorator> ALL = (List)new DescriptorListExt<PageDecorator>(PageDecorator.class);

    /**
     * Returns all the registered {@link PageDecorator} descriptors.
     */
    public static ExtensionList<PageDecorator> all() {
        return HudsonExt.getInstance().<PageDecorator,PageDecorator>getDescriptorList(PageDecorator.class);
    }
}

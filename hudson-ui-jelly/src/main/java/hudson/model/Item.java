/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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


import org.kohsuke.stapler.StaplerRequest;

/**
 * Basic configuration unit in Hudson.
 *
 * <p>
 * Every {@link ItemExt} is hosted in an {@link ItemGroup} called "parent",
 * and some {@link ItemExt}s are {@link ItemGroup}s. This form a tree
 * structure, which is rooted at {@link Hudson}.
 *
 * <p>
 * Unlike file systems, where a file can be moved from one directory
 * to another, {@link ItemExt} inherently belongs to a single {@link ItemGroup}
 * and that relationship will not change.
 * Think of
 * <a href="http://images.google.com/images?q=Windows%20device%20manager">Windows device manager</a>
 * &mdash; an HDD always show up under 'Disk drives' and it can never be moved to another parent.
 *
 * Similarly, {@link ItemGroup} is not a generic container. Each subclass
 * of {@link ItemGroup} can usually only host a certain limited kinds of
 * {@link ItemExt}s.
 *
 * <p>
 * {@link ItemExt}s have unique {@link #getName() name}s that distinguish themselves
 * among their siblings uniquely. The names can be combined by '/' to form an
 * item full name, which uniquely identifies an {@link ItemExt} inside the whole {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 * @see Items
 */
public interface Item extends ItemExt {

    /**
     * Returns the URL of this item relative to the context root of the application.
     *
     * @see AbstractItem#getUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getUrl();

    /**
     * Returns the URL of this item relative to the parent {@link ItemGroup}.
     * @see AbstractItem#getShortUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    String getShortUrl();

    /**
     * Returns the absolute URL of this item. This relies on the current
     * {@link StaplerRequest} to figure out what the host name is,
     * so can be used only during processing client requests.
     *
     * @return
     *      absolute URL.
     * @throws IllegalStateException
     *      if the method is invoked outside the HTTP request processing.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it won't work with
     *      network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references
     *      (even this won't work for the same reason, which should be fixed.)
     */
    String getAbsoluteUrl();
}

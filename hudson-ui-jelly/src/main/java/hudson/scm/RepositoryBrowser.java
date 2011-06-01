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

import hudson.Extension;
import hudson.model.DescriptorExt;

import org.kohsuke.stapler.export.ExportedBean;

/**
 * Connects HudsonExt to repository browsers like ViewCVS or FishEye,
 * so that HudsonExt can generate links to them. 
 *
 * <p>
 * {@link RepositoryBrowser} instance is normally created as
 * a result of job configuration, and  stores immutable
 * configuration information (such as the URL of the FishEye site).
 *
 * <p>
 * {@link RepositoryBrowser} is persisted with {@link SCM}.
 *
 * <p>
 * To have HudsonExt recognize {@link RepositoryBrowser}, put {@link Extension} on your {@link DescriptorExt}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.89
 * @see RepositoryBrowsers
 */
@ExportedBean
public abstract class RepositoryBrowser<E extends ChangeLogSetExt.Entry> extends RepositoryBrowserExt {
    
}

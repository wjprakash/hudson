/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, InfraDNA, Inc.
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
import hudson.model.Api;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Captures the configuration information in it.
 *
 * <p>
 * To register a custom {@link SCM} implementation from a plugin,
 * put {@link Extension} on your {@link SCMDescriptor}.
 *
 * <p>
 * Use the "project-changes" view to render change list to be displayed
 * at the project level. The default implementation simply aggregates
 * change lists from builds, but your SCM can provide different views.
 * The view gets the "builds" variable which is a list of builds that are
 * selected for the display.
 *
 * <p>
 * If you are interested in writing a subclass in a plugin,
 * also take a look at <a href="http://wiki.hudson-ci.org/display/HUDSON/Writing+an+SCM+plugin">
 * "Writing an SCM plugin"</a> wiki article.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class SCM extends SCMExt {
     
    /**
     * Expose {@link SCM} to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

     

    /**
     * Type of this SCM.
     *
     * Exposed so that the client of the remote API can tell what SCM this is.
     */
    @Exported
    @Override
    public String getType() {
        return super.getType();
    }

    /**
     * Returns the applicable {@link RepositoryBrowser} for files
     * controlled by this {@link SCM}.
     *
     * <p>
     * This method attempts to find applicable browser
     * from other job configurations.
     */
    @Exported(name="browser")
    @Override
    public final RepositoryBrowser<?> getEffectiveBrowser() {
         return super.getEffectiveBrowser();
    }

}

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
package hudson.model;

import hudson.PluginExt;
import hudson.DescriptorExtensionListExt;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extensible property of {@link User}.
 *
 * <p>
 * {@link PluginExt}s can extend this to define custom properties
 * for {@link User}s. {@link UserProperty}s show up in the user
 * configuration screen, and they are persisted with the user object.
 *
 * <p>
 * Configuration screen should be defined in <tt>config.jelly</tt>.
 * Within this page, the {@link UserProperty} instance is available
 * as <tt>instance</tt> variable (while <tt>it</tt> refers to {@link User}.
 * See {@link Mailer.UserProperty}'s <tt>config.jelly</tt> for an example.
 *
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class UserProperty extends UserPropertyExt {
    
    public UserProperty reconfigure(StaplerRequest req, JSONObject form) throws FormException {
    	return getDescriptor().newInstance(req, form);
    }
}

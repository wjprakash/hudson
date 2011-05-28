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
package hudson.slaves;

import hudson.Extension;
import hudson.model.ComputerSetExt;
import hudson.model.DescriptorExt;
import hudson.model.SlaveExt;
import hudson.model.NodeExt;
import hudson.model.HudsonExt;
import hudson.util.DescriptorList;
import hudson.util.FormValidation;
import hudson.DescriptorExtensionListExt;
import hudson.Util;
import hudson.model.FailureExt;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

/**
 * {@link DescriptorExt} for {@link Slave}.
 *
 * <h2>Views</h2>
 * <p>
 * This object needs to have <tt>newInstanceDetail.jelly</tt> view, which shows up in
 * <tt>http://server/hudson/computers/new</tt> page as an explanation of this job type.
 *
 * <h2>Other Implementation Notes</h2>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class NodeDescriptor extends NodeDescriptorExt {
    protected NodeDescriptor(Class<? extends NodeExt> clazz) {
        super(clazz);
    }

    

//    @Override
//    public String getConfigPage() {
//        return getViewPage(clazz, "configure-entries.jelly");
//    }

    public FormValidation doCheckName(@QueryParameter String value ) {
        String name = Util.fixEmptyAndTrim(value);
        if(name==null)
            return FormValidation.error(Messages.NodeDescripter_CheckName_Mandatory());
        try {
            HudsonExt.checkGoodName(name);
        } catch (FailureExt f) {
            return FormValidation.error(f.getMessage());
        }
        return FormValidation.ok();
    }

    /**
     * Returns all the registered {@link NodeDescriptorExt} descriptors.
     */
//    public static DescriptorExtensionListExt<Node,NodeDescriptor> all() {
//        return HudsonExt.getInstance().<Node,NodeDescriptor>getDescriptorList(NodeExt.class);
//    }

}

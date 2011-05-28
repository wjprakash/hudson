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
import hudson.model.DescriptorExt;
import hudson.model.SlaveExt;
import hudson.model.NodeExt;
import hudson.model.HudsonExt;
import hudson.util.DescriptorList;
import hudson.DescriptorExtensionListExt;

import java.util.List;
import java.util.ArrayList;


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
public abstract class NodeDescriptorExt extends DescriptorExt<NodeExt> {
    protected NodeDescriptorExt(Class<? extends NodeExt> clazz) {
        super(clazz);
    }

    protected NodeDescriptorExt() {
    }

    /**
     * Can the administrator create this type of nodes from UI?
     *
     * Return false if it only makes sense for programs to create it, not through the "new node" UI.
     */
    public boolean isInstantiable() {
        return true;
    }

    public final String newInstanceDetailPage() {
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/newInstanceDetail.jelly";
    }

    
    /**
     * Returns all the registered {@link NodeDescriptorExt} descriptors.
     */
    public static DescriptorExtensionListExt<NodeExt,NodeDescriptorExt> all() {
        return HudsonExt.getInstance().<NodeExt,NodeDescriptorExt>getDescriptorList(NodeExt.class);
    }

    /**
     * All the registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    public static final DescriptorList<NodeExt> ALL = new DescriptorList<NodeExt>(NodeExt.class);

    public static List<NodeDescriptorExt> allInstantiable() {
        List<NodeDescriptorExt> r = new ArrayList<NodeDescriptorExt>();
        for (NodeDescriptorExt d : all())
            if(d.isInstantiable())
                r.add(d);
        return r;
    }
}

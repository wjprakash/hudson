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
package hudson.util;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorExt;
import hudson.model.Descriptor.FormException;
import hudson.model.Saveable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Persisted list of {@link Describable}s with some operations specific
 * to {@link DescriptorExt}s.
 *
 * <p>
 * This class allows multiple instances of the same descriptor. Some clients
 * use this semantics, while other clients use it as "up to one instance per
 * one descriptor" model.
 *
 * Some of the methods defined in this class only makes sense in the latter model,
 * such as {@link #remove(DescriptorExt)}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DescribableList<T extends Describable<T>, D extends DescriptorExt<T>> extends DescribableListExt {
    protected DescribableList() {
    }

    /**
     * @deprecated since 2008-08-15.
     *      Use {@link #DescribableList(Saveable)} 
     */
    public DescribableList(Owner owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner, Collection<? extends T> initialList) {
        super(owner, initialList);
    }

  
    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This method is almost always used by the owner.
     * This method does not invoke the save method.
     *
     * @param json
     *      Structured form data that includes the data for nested descriptor list.
     */
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors) throws FormException, IOException {
        List<T> newList = new ArrayList<T>();

        for (Descriptor<T> d : descriptors) {
            String name = d.getJsonSafeClassName();
            if (json.has(name)) {
                T instance = d.newInstance(req, json.getJSONObject(name));
                newList.add(instance);
            }
        }

        replaceBy(newList);
    }

    /**
     * @deprecated as of 1.271
     *      Use {@link #rebuild(StaplerRequest, JSONObject, List)} instead.
     */
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException, IOException {
        rebuild(req,json,descriptors);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This version works with the the &lt;f:hetero-list> UI tag, where the user
     * is allowed to create multiple instances of the same descriptor. Order is also
     * significant.
     */
    public void rebuildHetero(StaplerRequest req, JSONObject formData, Collection<? extends Descriptor<T>> descriptors, String key) throws FormException, IOException {
        replaceBy(Descriptor.newInstancesFromHeteroList(req,formData,key,descriptors));
    }

}

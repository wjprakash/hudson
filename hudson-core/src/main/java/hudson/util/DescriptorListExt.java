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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * List of {@link DescriptorExt}s.
 *
 * <p>
 * Before HudsonExt 1.286, this class stored {@link DescriptorExt}s directly, but since 1.286,
 * this class works in two modes that are rather different.
 *
 * <p>
 * One is the compatibility mode, where it works just like pre 1.286 and store everything locally,
 * disconnected from any of the additions of 1.286. This is necessary for situations where
 * {@link DescriptorList} is owned by pre-1.286 plugins where this class doesn't know 'T'.
 * In this mode, {@link #legacy} is non-null but {@link #type} is null.
 *
 * <p>
 * The other mode is the new mode, where the {@link DescriptorExt}s are actually stored in {@link ExtensionList}
 * (see {@link HudsonExt#getDescriptorList(Class)}) and this class acts as a view to it. This enables
 * bi-directional interoperability &mdash; both descriptors registred automatically and descriptors registered
 * manually are visible from both {@link DescriptorList} and {@link ExtensionList}. In this mode,
 * {@link #legacy} is null but {@link #type} is non-null.
 *
 * <p>
 * The number of plugins that define extension points are limited, so we expect to be able to remove
 * this dual behavior first, then when everyone stops using {@link DescriptorList},  we can remove this class
 * altogether.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.161
 */
public class DescriptorListExt<T extends Describable<T>> extends AbstractList<DescriptorExt<T>> {

    private final Class<T> type;

    private final CopyOnWriteArrayList<DescriptorExt<T>> legacy;

    /**
     * This will create a legacy {@link DescriptorList} that is disconnected from
     * {@link ExtensionList}.
     *
     * @deprecated
     *      As of 1.286. Use {@link #DescriptorList(Class)} instead.
     */
    public DescriptorListExt(DescriptorExt<T>... descriptors) {
        this.type = null;
        this.legacy = new CopyOnWriteArrayList<DescriptorExt<T>>(descriptors);
    }

    /**
     * Creates a {@link DescriptorList} backed by {@link ExtensionList}.
     */
    public DescriptorListExt(Class<T> type) {
        this.type = type;
        this.legacy = null;
    }

    @Override
    public DescriptorExt<T> get(int index) {
        return store().get(index);
    }

    @Override
    public int size() {
        return store().size();
    }

    @Override
    public Iterator<DescriptorExt<T>> iterator() {
        return store().iterator();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     *      As of 1.286. Put {@link Extension} on your descriptor to have it auto-registered,
     *      instead of registering a descriptor manually.
     */
    @Override
    public boolean add(DescriptorExt<T> d) {
        return store().add(d);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     *      As of 1.286. Put {@link Extension} on your descriptor to have it auto-registered,
     *      instead of registering a descriptor manually.
     */
    @Override
    public void add(int index, DescriptorExt<T> element) {
        add(element); // order is ignored
    }

    @Override
    public boolean remove(Object o) {
        return store().remove(o);
    }

    /**
     * Gets the actual data store. This is the key to control the dual-mode nature of {@link DescriptorList}
     */
    private List<DescriptorExt<T>> store() {
        if(type==null)
            return legacy;
        else
            return HudsonExt.getInstance().<T,DescriptorExt<T>>getDescriptorList(type);
    }

    /**
     * Finds a descriptor by their {@link DescriptorExt#getId()}.
     *
     * If none is found, null is returned.
     */
    public DescriptorExt<T> findByName(String id) {
        for (DescriptorExt<T> d : this)
            if(d.getId().equals(id))
                return d;
        return null;
    }

    /**
     * No-op method used to force the class initialization of the given class.
     * The class initialization in turn is expected to put the descriptor
     * into the {@link DescriptorList}.
     *
     * <p>
     * This is necessary to resolve the class initialization order problem.
     * Often a {@link DescriptorList} is defined in the base class, and
     * when it tries to initialize itself by listing up descriptors of known
     * sub-classes, they might not be available in time.
     *
     * @since 1.162
     */
    public void load(Class<? extends Describable> c) {
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);  // Can't happen
        }
    }

    /**
     * Finds the descriptor that has the matching fully-qualified class name.
     */
    public DescriptorExt<T> find(String fqcn) {
        return DescriptorExt.find(this,fqcn);
    }
}

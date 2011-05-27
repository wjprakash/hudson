/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson;

import hudson.model.DescriptorExt;
import hudson.model.Describable;
import hudson.model.HudsonExt;
import hudson.model.ViewDescriptor;
import hudson.util.AdaptedIterator;
import hudson.util.Memoizer;
import hudson.util.Iterators.FlattenIterator;
import hudson.slaves.NodeDescriptorExt;
import hudson.tasks.Publisher;
import hudson.tasks.Publisher.DescriptorExtensionListImpl;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import org.jvnet.tiger_types.Types;

/**
 * {@link ExtensionList} for holding a set of {@link DescriptorExt}s, which is a group of descriptors for
 * the same extension point.
 *
 * Use {@link HudsonExt#getDescriptorList(Class)} to obtain instances.
 *
 * @param <D>
 *      Represents the descriptor type. This is {@code DescriptorExt<T>} normally but often there are subtypes
 *      of descriptors, like {@link ViewDescriptor}, {@link NodeDescriptorExt}, etc, and this parameter points
 *      to those for better type safety of users.
 *
 *      The actual value of 'D' is not necessary for the operation of this code, so it's purely for convenience
 *      of the users of this class.
 *
 * @since 1.286
 */
public class DescriptorExtensionListExt<T extends Describable<T>, D extends DescriptorExt<T>> extends ExtensionList<D> {
    /**
     * Creates a new instance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Describable<T>,D extends DescriptorExt<T>>
    DescriptorExtensionListExt<T,D> createDescriptorList(HudsonExt hudson, Class<T> describableType) {
        if (describableType == (Class) Publisher.class) {
            return (DescriptorExtensionListExt) new DescriptorExtensionListImpl(hudson);
        }
        return new DescriptorExtensionListExt<T,D>(hudson,describableType);
    }

    /**
     * Type of the {@link Describable} that this extension list retains.
     */
    private final Class<T> describableType;

    protected DescriptorExtensionListExt(HudsonExt hudson, Class<T> describableType) {
        super(hudson, (Class)DescriptorExt.class, (CopyOnWriteArrayList)getLegacyDescriptors(describableType));
        this.describableType = describableType;
    }

    /**
     * Finds the descriptor that has the matching fully-qualified class name.
     *
     * @param fqcn
     *      Fully qualified name of the descriptor, not the describable.
     */
    public D find(String fqcn) {
        return DescriptorExt.find(this,fqcn);
    }

    /**
     * Finds the descriptor that describes the given type.
     * That is, if this method returns d, {@code d.clazz==type}
     */
    public D find(Class<? extends T> type) {
        for (D d : this)
            if (d.clazz==type)
                return d;
        return null;
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
     * {@link #load()} in the descriptor is not a real load activity, so locking against "this" is enough.
     */
    @Override
    protected Object getLoadLock() {
        return this;
    }

    /**
     * Loading the descriptors in this case means filtering the descriptor from the master {@link ExtensionList}.
     */
    @Override
    protected List<ExtensionComponent<D>> load() {
        List<ExtensionComponent<D>> r = new ArrayList<ExtensionComponent<D>>();
        for( ExtensionComponent<DescriptorExt> c : hudson.getExtensionList(DescriptorExt.class).getComponents() ) {
            DescriptorExt d = c.getInstance();
            Type subTyping = Types.getBaseClass(d.getClass(), DescriptorExt.class);
            if (!(subTyping instanceof ParameterizedType)) {
                LOGGER.severe(d.getClass()+" doesn't extend Descriptor with a type parameter");
                continue;   // skip this one
            }
            if(Types.erasure(Types.getTypeArgument(subTyping,0))==(Class)describableType)
                r.add((ExtensionComponent)c);
        }
        return r;
    }

    /**
     * Stores manually registered DescriptorExt instances. Keyed by the {@link Describable} type.
     */
    private static final Memoizer<Class,CopyOnWriteArrayList<ExtensionComponent<DescriptorExt>>> legacyDescriptors = new Memoizer<Class,CopyOnWriteArrayList<ExtensionComponent<DescriptorExt>>>() {
        public CopyOnWriteArrayList compute(Class key) {
            return new CopyOnWriteArrayList();
        }
    };

    private static <T extends Describable<T>> CopyOnWriteArrayList<ExtensionComponent<DescriptorExt<T>>> getLegacyDescriptors(Class<T> type) {
        return (CopyOnWriteArrayList)legacyDescriptors.get(type);
    }

    /**
     * List up all the legacy instances currently in use.
     */
    public static Iterable<DescriptorExt> listLegacyInstances() {
        return new Iterable<DescriptorExt>() {
            public Iterator<DescriptorExt> iterator() {
                return new AdaptedIterator<ExtensionComponent<DescriptorExt>,DescriptorExt>(
                    new FlattenIterator<ExtensionComponent<DescriptorExt>,CopyOnWriteArrayList<ExtensionComponent<DescriptorExt>>>(legacyDescriptors.values()) {
                        protected Iterator<ExtensionComponent<DescriptorExt>> expand(CopyOnWriteArrayList<ExtensionComponent<DescriptorExt>> v) {
                            return v.iterator();
                        }
                    }) {

                    protected DescriptorExt adapt(ExtensionComponent<DescriptorExt> item) {
                        return item.getInstance();
                    }
                };
            }
        };
    }

    /**
     * Exposed just for the test harness. Clear legacy instances.
     */
    public static void clearLegacyInstances() {
        legacyDescriptors.clear();
    }

    private static final Logger LOGGER = Logger.getLogger(DescriptorExtensionListExt.class.getName());
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.XmlFile;
import hudson.BulkChange;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.listeners.SaveableListener;
import org.jvnet.tiger_types.Types;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.beans.Introspector;

/**
 * Metadata about a configurable instance.
 *
 * <p>
 * {@link DescriptorExt} is an object that has metadata about a {@link Describable}
 * object, and also serves as a factory (in a way this relationship is similar
 * to {@link Object}/{@link Class} relationship.
 *
 * A {@link DescriptorExt}/{@link Describable}
 * combination is used throughout in HudsonExt to implement a
 * configuration/extensibility mechanism.
 *
 * <p>
 * Take the list view support as an example, which is implemented
 * in {@link ListView} class. Whenever a new view is created, a new
 * {@link ListView} instance is created with the configuration
 * information. This instance gets serialized to XML, and this instance
 * will be called to render the view page. This is the job
 * of {@link Describable} &mdash; each instance represents a specific
 * configuration of a view (what projects are in it, regular expression, etc.)
 *
 * <p>
 * For HudsonExt to create such configured {@link ListView} instance, HudsonExt
 * needs another object that captures the metadata of {@link ListView},
 * and that is what a {@link DescriptorExt} is for. {@link ListView} class
 * has a singleton descriptor, and this descriptor helps render
 * the configuration form, remember system-wide configuration, and works as a factory.
 *
 * <p>
 * {@link DescriptorExt} also usually have its associated views.
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link DescriptorExt} can persist data just by storing them in fields.
 * However, it is the responsibility of the derived type to properly
 * invoke {@link #save()} and {@link #load()}.
 *
 * <h2>Reflection Enhancement</h2>
 * {@link DescriptorExt} defines addition to the standard Java reflection
 * and provides reflective information about its corresponding {@link Describable}.
 * These are primarily used by tag libraries to
 * keep the Jelly scripts concise. 
 *
 * @author Kohsuke Kawaguchi
 * @see Describable
 */
public abstract class DescriptorExt<T extends Describable<T>> implements Saveable {

    /**
     * Up to HudsonExt 1.61 this was used as the primary persistence mechanism.
     * Going forward HudsonExt simply persists all the non-transient fields
     * of {@link DescriptorExt}, just like others, so this is pointless.
     *
     * @deprecated since 2006-11-16
     */
    @Deprecated
    private transient Map<String, Object> properties;
    /**
     * The class being described by this descriptor.
     */
    public transient final Class<? extends T> clazz;
    protected transient final Map<String, String> checkMethods = new ConcurrentHashMap<String, String>();
    /**
     * Lazily computed list of properties on {@link #clazz} and on the descriptor itself.
     */
    private transient volatile Map<String, PropertyType> propertyTypes, globalPropertyTypes;

    /**
     * Represents a readable property on {@link Describable}.
     */
    public static final class PropertyType {

        public final Class clazz;
        public final Type type;
        private volatile Class itemType;

        PropertyType(Class clazz, Type type) {
            this.clazz = clazz;
            this.type = type;
        }

        PropertyType(Field f) {
            this(f.getType(), f.getGenericType());
        }

        PropertyType(Method getter) {
            this(getter.getReturnType(), getter.getGenericReturnType());
        }

        public Enum[] getEnumConstants() {
            return (Enum[]) clazz.getEnumConstants();
        }

        /**
         * If the property is a collection/array type, what is an item type?
         */
        public Class getItemType() {
            if (itemType == null) {
                itemType = computeItemType();
            }
            return itemType;
        }

        private Class computeItemType() {
            if (clazz.isArray()) {
                return clazz.getComponentType();
            }
            if (Collection.class.isAssignableFrom(clazz)) {
                Type col = Types.getBaseClass(type, Collection.class);

                if (col instanceof ParameterizedType) {
                    return Types.erasure(Types.getTypeArgument(col, 0));
                } else {
                    return Object.class;
                }
            }
            return null;
        }

        /**
         * Returns {@link DescriptorExt} whose 'clazz' is the same as {@link #getItemType() the item type}.
         */
        public DescriptorExt getItemTypeDescriptor() {
            return HudsonExt.getInstance().getDescriptor(getItemType());
        }

        public DescriptorExt getItemTypeDescriptorOrDie() {
            return HudsonExt.getInstance().getDescriptorOrDie(getItemType());
        }

        /**
         * Returns all the descriptors that produce types assignable to the item type.
         */
        public List<? extends DescriptorExt> getApplicableDescriptors() {
            return HudsonExt.getInstance().getDescriptorList(clazz);
        }
    }

    protected DescriptorExt(Class<? extends T> clazz) {
        this.clazz = clazz;
        // doing this turns out to be very error prone,
        // as field initializers in derived types will override values.
        // load();
    }

    /**
     * Infers the type of the corresponding {@link Describable} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     * 
     * @since 1.278
     */
    protected DescriptorExt() {
        this.clazz = (Class<T>) getClass().getEnclosingClass();
        if (clazz == null) {
            throw new AssertionError(getClass() + " doesn't have an outer class. Use the constructor that takes the Class object explicitly.");
        }

        // detect an type error
        Type bt = Types.getBaseClass(getClass(), DescriptorExt.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 't' is the closest approximation of T of DescriptorExt<T>.
            Class t = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!t.isAssignableFrom(clazz)) {
                throw new AssertionError("Outer class " + clazz + " of " + getClass() + " is not assignable to " + t + ". Perhaps wrong outer class?");
            }
        }

        // detect a type error. this DescriptorExt is supposed to be returned from getDescriptor(), so make sure its type match up.
        // this prevents a bug like http://www.nabble.com/Creating-a-new-parameter-Type-%3A-Masked-Parameter-td24786554.html
        try {
            Method getd = clazz.getMethod("getDescriptor");
            if (!getd.getReturnType().isAssignableFrom(getClass())) {
                throw new AssertionError(getClass() + " must be assignable to " + getd.getReturnType());
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(getClass() + " is missing getDescriptor method.");
        }

    }

    /**
     * Human readable name of this kind of configurable object.
     */
    public abstract String getDisplayName();

    /**
     * Uniquely identifies this {@link DescriptorExt} among all the other {@link DescriptorExt}s.
     *
     * <p>
     * Historically {@link #clazz} is assumed to be unique, so this method uses that as the default,
     * but if you are adding {@link DescriptorExt}s programmatically for the same type, you can change
     * this to disambiguate them.
     *
     * @return
     *      Stick to valid Java identifier character, plus '.', which had to be allowed for historical reasons.
     * 
     * @since 1.391
     */
    public String getId() {
        return clazz.getName();
    }

    /**
     * Gets the URL that this DescriptorExt is bound to, relative to the nearest {@link DescriptorByNameOwner}.
     * Since {@link HudsonExt} is a {@link DescriptorByNameOwner}, there's always one such ancestor to any request.
     */
    public String getDescriptorUrl() {
        return "descriptorByName/" + getId();
    }

    /**
     * Used by Jelly to abstract away the handlign of global.jelly vs config.jelly databinding difference.
     */
    public PropertyType getPropertyType(Object instance, String field) {
        // in global.jelly, instance==descriptor
        return instance == this ? getGlobalPropertyType(field) : getPropertyType(field);
    }

    /**
     * Obtains the property type of the given field of {@link #clazz}
     */
    public PropertyType getPropertyType(String field) {
        if (propertyTypes == null) {
            propertyTypes = buildPropertyTypes(clazz);
        }
        return propertyTypes.get(field);
    }

    /**
     * Obtains the property type of the given field of this descriptor.
     */
    public PropertyType getGlobalPropertyType(String field) {
        if (globalPropertyTypes == null) {
            globalPropertyTypes = buildPropertyTypes(getClass());
        }
        return globalPropertyTypes.get(field);
    }

    /**
     * Given the class, list up its {@link PropertyType}s from its public fields/getters.
     */
    private Map<String, PropertyType> buildPropertyTypes(Class<?> clazz) {
        Map<String, PropertyType> r = new HashMap<String, PropertyType>();
        for (Field f : clazz.getFields()) {
            r.put(f.getName(), new PropertyType(f));
        }

        for (Method m : clazz.getMethods()) {
            if (m.getName().startsWith("get")) {
                r.put(Introspector.decapitalize(m.getName().substring(3)), new PropertyType(m));
            }
        }

        return r;
    }

    /**
     * Gets the class name nicely escaped to be usable as a key in the structured form submission.
     */
    public final String getJsonSafeClassName() {
        return getId().replace('.', '-');
    }

    /**
     * Look out for a typical error a plugin developer makes.
     * See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html
     */
    protected T verifyNewInstance(T t) {
        if (t != null && t.getDescriptor() != this) {
            // TODO: should this be a fatal error?
            LOGGER.warning("Father of " + t + " and its getDescriptor() points to two different instances. Probably malplaced @Extension. See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html");
        }
        return t;
    }

    /**
     * Returns the resource path to the help screen HTML, if any.
     *
     * <p>
     * Starting 1.282, this method uses "convention over configuration" &mdash; you should
     * just put the "help.html" (and its localized versions, if any) in the same directory
     * you put your Jelly view files, and this method will automatically does the right thing.
     *
     * <p>
     * This value is relative to the context root of HudsonExt, so normally
     * the values are something like <tt>"/plugin/emma/help.html"</tt> to
     * refer to static resource files in a plugin, or <tt>"/publisher/EmmaPublisher/abc"</tt>
     * to refer to Jelly script <tt>abc.jelly</tt> or a method <tt>EmmaPublisher.doAbc()</tt>.
     *
     * @return
     *      null to indicate that there's no help.
     */
    public String getHelpFile() {
        return getHelpFile(null);
    }

    /**
     * Returns the path to the help screen HTML for the given field.
     *
     * <p>
     * The help files are assumed to be at "help/FIELDNAME.html" with possible
     * locale variations.
     */
    public String getHelpFile(final String fieldName) {
        for (Class c = clazz; c != null; c = c.getSuperclass()) {
            String page = "/descriptor/" + getId() + "/help";
            String suffix;
            if (fieldName == null) {
                suffix = "";
            } else {
                page += '/' + fieldName;
                suffix = '-' + fieldName;
            }

//            try {
//                if(Stapler.getCurrentRequest().getView(c,"help"+suffix)!=null)
//                    return page;
//            } catch (IOException e) {
//                throw new Error(e);
//            }

            InputStream in = getHelpStream(c, suffix);
            IOUtils.closeQuietly(in);
            if (in != null) {
                return page;
            }
        }
        return null;
    }

    /**
     * Checks if the given object is created from this {@link DescriptorExt}.
     */
    public final boolean isInstance(T instance) {
        return clazz.isInstance(instance);
    }

    /**
     * Checks if the type represented by this descriptor is a subtype of the given type.
     */
    public final boolean isSubTypeOf(Class type) {
        return type.isAssignableFrom(clazz);
    }

    /**
     * Saves the configuration info to the disk.
     */
    public synchronized void save() {
        if (BulkChange.contains(this)) {
            return;
        }
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     *
     * <p>
     * The constructor of the derived class must call this method.
     * (If we do that in the base class, the derived class won't
     * get a chance to set default values.)
     */
    public synchronized void load() {
        XmlFile file = getConfigFile();
        if (!file.exists()) {
            return;
        }

        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + file, e);
        }
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(HudsonExt.getInstance().getRootDir(), getId() + ".xml"));
    }

    protected InputStream getHelpStream(Class c, String suffix) {
        Locale locale = null;

        //Locale locale = Stapler.getCurrentRequest().getLocale();

        String base = c.getName().replace('.', '/').replace('$', '/') + "/help" + suffix;

        ClassLoader cl = c.getClassLoader();
        if (cl == null) {
            return null;
        }

        InputStream in;
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + '_' + locale.getVariant() + ".html");
        if (in != null) {
            return in;
        }
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + ".html");
        if (in != null) {
            return in;
        }
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + ".html");
        if (in != null) {
            return in;
        }

        // default
        return cl.getResourceAsStream(base + ".html");
    }

//
// static methods
//
    // to work around warning when creating a generic array type
    public static <T> T[] toArray(T... values) {
        return values;
    }

    public static <T> List<T> toList(T... values) {
        return new ArrayList<T>(Arrays.asList(values));
    }

    public static <T extends Describable<T>> Map<DescriptorExt<T>, T> toMap(Iterable<T> describables) {
        Map<DescriptorExt<T>, T> m = new LinkedHashMap<DescriptorExt<T>, T>();
        for (T d : describables) {
            m.put(d.getDescriptor(), d);
        }
        return m;
    }

    /**
     * Finds a descriptor from a collection by its class name.
     */
    public static <T extends DescriptorExt> T find(Collection<? extends T> list, String className) {
        for (T d : list) {
            if (d.getClass().getName().equals(className)) {
                return d;
            }
        }
        return null;
    }

    public static DescriptorExt find(String className) {
        return find(HudsonExt.getInstance().getExtensionList(DescriptorExt.class), className);
    }
    private static final Logger LOGGER = Logger.getLogger(DescriptorExt.class.getName());
    /**
     * Used in {@link #checkMethods} to indicate that there's no check method.
     */
    protected static final String NONE = "\u0000";

    private Object readResolve() {
        if (properties != null) {
            OldDataMonitorExt.report(this, "1.62");
        }
        return this;
    }
}

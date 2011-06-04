/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.labels;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.BulkChange;
import hudson.CopyOnWrite;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.FailureExt;
import hudson.model.HudsonExt;
import hudson.model.LabelExt;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.DescribableListExt;
import hudson.util.QuotedStringTokenizer;
import hudson.util.VariableResolver;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atomic single token label, like "foo" or "bar".
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public class LabelAtomExt extends LabelExt implements Saveable {

    protected DescribableListExt<LabelAtomPropertyExt, LabelAtomPropertyDescriptor> properties =
            new DescribableListExt<LabelAtomPropertyExt, LabelAtomPropertyDescriptor>(this);
    @CopyOnWrite
    protected transient volatile List<Action> transientActions = new Vector<Action>();

    public LabelAtomExt(String name) {
        super(name);
    }

    /**
     * If the label contains 'unsafe' chars, escape them.
     */
    @Override
    public String getExpression() {
        return escape(name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Note that this method returns a read-only view of {@link Action}s.
     * {@link LabelAtomProperty}s who want to add a project action
     * should do so by implementing {@link LabelAtomProperty#getActions(LabelAtom)}.
     */
    @Override
    public synchronized List<Action> getActions() {
        // add all the transient actions, too
        List<Action> actions = new Vector<Action>(super.getActions());
        actions.addAll(transientActions);
        // return the read only list to cause a failure on plugins who try to add an action here
        return Collections.unmodifiableList(actions);
    }

    protected void updateTransientActions() {
        Vector<Action> ta = new Vector<Action>();

        // add the config link
        if (!getApplicablePropertyDescriptors().isEmpty()) {
            // if there's no property descriptor, there's nothing interesting to configure.
            ta.add(new Action() {

                public String getIconFileName() {
                    if (HudsonExt.getInstance().hasPermission(HudsonExt.ADMINISTER)) {
                        return "setting.gif";
                    } else {
                        return null;
                    }
                }

                public String getDisplayName() {
                    return "Configure";
                }

                public String getUrlName() {
                    return "configure";
                }
            });
        }

        for (LabelAtomPropertyExt p : properties) {
            ta.addAll(p.getActions(this));
        }

        transientActions = ta;
    }

    /**
     * Properties associated with this label.
     */
    public DescribableListExt<LabelAtomPropertyExt, LabelAtomPropertyDescriptor> getProperties() {
        return properties;
    }

    public List<LabelAtomPropertyExt> getPropertiesList() {
        return properties.toList();
    }

    @Override
    public boolean matches(VariableResolver<Boolean> resolver) {
        return resolver.resolve(name);
    }

    @Override
    public LabelOperatorPrecedence precedence() {
        return LabelOperatorPrecedence.ATOM;
    }

    /*package*/ XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(HudsonExt.getInstance().root, "labels/" + name + ".xml"));
    }

    public void save() throws IOException {
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

    public void load() {
        XmlFile file = getConfigFile();
        if (file.exists()) {
            try {
                file.unmarshal(this);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
            }
        }
        properties.setOwner(this);
        updateTransientActions();
    }

    /**
     * Returns all the {@link LabelAtomPropertyDescriptor}s that can be potentially configured
     * on this label.
     */
    public List<LabelAtomPropertyDescriptor> getApplicablePropertyDescriptors() {
        return LabelAtomPropertyExt.all();
    }

    /**
     * Obtains an atom by its {@linkplain #getName() name}.
     */
    public static LabelAtomExt get(String l) {
        return HudsonExt.getInstance().getLabelAtom(l);
    }

    public static boolean needsEscape(String name) {
        try {
            HudsonExt.checkGoodName(name);
            // additional restricted chars
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (" ()\t\n".indexOf(ch) != -1) {
                    return true;
                }
            }
            return false;
        } catch (FailureExt failure) {
            return true;
        }
    }

    public static String escape(String name) {
        if (needsEscape(name)) {
            return QuotedStringTokenizer.quote(name);
        }
        return name;
    }
    private static final Logger LOGGER = Logger.getLogger(LabelAtomExt.class.getName());
    private static final XStream2 XSTREAM = new XStream2();

    static {
        // Don't want LabelExt.ConverterImpl to be used:
        XSTREAM.registerConverter(new LabelAtomConverter(), 100);
    }

    // class name is not ConverterImpl, to avoid getting picked up by AssociatedConverterImpl
    private static class LabelAtomConverter extends XStream2.PassthruConverter<LabelAtomExt> {

        private LabelExt.ConverterImpl leafLabelConverter = new LabelExt.ConverterImpl();

        private LabelAtomConverter() {
            super(XSTREAM);
        }

        public boolean canConvert(Class type) {
            return LabelAtomExt.class.isAssignableFrom(type);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            if (context.get(IN_NESTED) == null) {
                context.put(IN_NESTED, true);
                try {
                    super.marshal(source, writer, context);
                } finally {
                    context.put(IN_NESTED, false);
                }
            } else {
                leafLabelConverter.marshal(source, writer, context);
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (context.get(IN_NESTED) == null) {
                context.put(IN_NESTED, true);
                try {
                    return super.unmarshal(reader, context);
                } finally {
                    context.put(IN_NESTED, false);
                }
            } else {
                return leafLabelConverter.unmarshal(reader, context);
            }
        }

        @Override
        protected void callback(LabelAtomExt obj, UnmarshallingContext context) {
            // noop
        }
        private static final Object IN_NESTED = "VisitingInnerLabelAtom";
    }
}

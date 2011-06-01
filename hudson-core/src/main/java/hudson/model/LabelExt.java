/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import antlr.ANTLRException;
import hudson.UtilExt;
import static hudson.UtilExt.fixNull;

import hudson.model.labels.LabelAtomExt;
import hudson.model.labels.LabelExpression;
import hudson.model.labels.LabelExpressionLexer;
import hudson.model.labels.LabelExpressionParser;
import hudson.model.labels.LabelOperatorPrecedence;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.Cloud;
import hudson.util.QuotedStringTokenizer;
import hudson.util.VariableResolver;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.TreeSet;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Group of {@link NodeExt}s.
 * 
 * @author Kohsuke Kawaguchi
 * @see HudsonExt#getLabels()
 * @see HudsonExt#getLabel(String) 
 */
public abstract class LabelExt extends ActionableExt implements Comparable<LabelExt>, ModelObject {
    /**
     * Display name of this label.
     */
    protected transient final String name;
    private transient volatile Set<NodeExt> nodes;
    private transient volatile Set<Cloud> clouds;

    public transient final LoadStatisticsExt loadStatistics;
    public transient final NodeProvisioner nodeProvisioner;

    public LabelExt(String name) {
        this.name = name;
         // passing these causes an infinite loop - getTotalExecutors(),getBusyExecutors());
        this.loadStatistics = new LoadStatisticsExt(0,0) {
            @Override
            public int computeIdleExecutors() {
                return LabelExt.this.getIdleExecutors();
            }

            @Override
            public int computeTotalExecutors() {
                return LabelExt.this.getTotalExecutors();
            }

            @Override
            public int computeQueueLength() {
                return HudsonExt.getInstance().getQueue().countBuildableItemsFor(LabelExt.this);
            }
        };
        this.nodeProvisioner = new NodeProvisioner(this, loadStatistics);
    }

    /**
     * Alias for {@link #getDisplayName()}.
     */
    public String getName() {
        return getDisplayName();
    }

    /**
     * Returns a human-readable text that represents this label.
     */
    public String getDisplayName() {
        return name;
    }

    /**
     * Returns a label expression that represents this label.
     */
    public abstract String getExpression();

    /**
     * Relative URL from the context path, that ends with '/'.
     */
    public String getUrl() {
        return "label/"+name+'/';
    }

    public String getSearchUrl() {
        return getUrl();
    }

    /**
     * Evaluates whether the label expression is true given the specified value assignment.
     * IOW, returns true if the assignment provided by the resolver matches this label expression.
     */
    public abstract boolean matches(VariableResolver<Boolean> resolver);

    /**
     * Evaluates whether the label expression is true when an entity owns the given set of
     * {@link LabelAtom}s.
     */
    public final boolean matches(final Collection<LabelAtomExt> labels) {
        return matches(new VariableResolver<Boolean>() {
            public Boolean resolve(String name) {
                for (LabelAtomExt a : labels)
                    if (a.getName().equals(name))
                        return true;
                return false;
            }
        });
    }

    public final boolean matches(NodeExt n) {
        return matches(n.getAssignedLabels());
    }

    /**
     * Returns true if this label is a "self label",
     * which means the label is the name of a {@link NodeExt}.
     */
    public boolean isSelfLabel() {
        Set<NodeExt> nodes = getNodes();
        return nodes.size() == 1 && nodes.iterator().next().getSelfLabel() == this;
    }

    /**
     * Gets all {@link NodeExt}s that belong to this label.
     */
    public Set<NodeExt> getNodes() {
        Set<NodeExt> nodes = this.nodes;
        if(nodes!=null) return nodes;

        Set<NodeExt> r = new HashSet<NodeExt>();
        HudsonExt h = HudsonExt.getInstance();
        if(this.matches(h))
            r.add(h);
        for (NodeExt n : h.getNodes()) {
            if(this.matches(n))
                r.add(n);
        }
        return this.nodes = Collections.unmodifiableSet(r);
    }

    /**
     * Gets all {@link Cloud}s that can launch for this label.
     */
    public Set<Cloud> getClouds() {
        if(clouds==null) {
            Set<Cloud> r = new HashSet<Cloud>();
            HudsonExt h = HudsonExt.getInstance();
            for (Cloud c : h.clouds) {
                if(c.canProvision(this))
                    r.add(c);
            }
            clouds = Collections.unmodifiableSet(r);
        }
        return clouds;
    }
    
    /**
     * Can jobs be assigned to this label?
     * <p>
     * The answer is yes if there is a reasonable basis to believe that HudsonExt can have
     * an executor under this label, given the current configuration. This includes
     * situations such as (1) there are offline slaves that have this label (2) clouds exist
     * that can provision slaves that have this label.
     */
    public boolean isAssignable() {
        for (NodeExt n : getNodes())
            if(n.getNumExecutors()>0)
                return true;
        return !getClouds().isEmpty();
    }

    /**
     * Number of total {@link Executor}s that belong to this label.
     * <p>
     * This includes executors that belong to offline nodes, so the result
     * can be thought of as a potential capacity, whereas {@link #getTotalExecutors()}
     * is the currently functioning total number of executors.
     * <p>
     * This method doesn't take the dynamically allocatable nodes (via {@link Cloud})
     * into account. If you just want to test if there's some executors, use {@link #isAssignable()}.
     */
    public int getTotalConfiguredExecutors() {
        int r=0;
        for (NodeExt n : getNodes())
            r += n.getNumExecutors();
        return r;
    }

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    public int getTotalExecutors() {
        int r=0;
        for (NodeExt n : getNodes()) {
            ComputerExt c = n.toComputer();
            if(c!=null && c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    public int getBusyExecutors() {
        int r=0;
        for (NodeExt n : getNodes()) {
            ComputerExt c = n.toComputer();
            if(c!=null && c.isOnline())
                r += c.countBusy();
        }
        return r;
    }

    /**
     * Number of idle {@link Executor}s that can start working immediately.
     */
    public int getIdleExecutors() {
        int r=0;
        for (NodeExt n : getNodes()) {
            ComputerExt c = n.toComputer();
            if(c!=null && (c.isOnline() || c.isConnecting()))
                r += c.countIdle();
        }
        return r;
    }

    /**
     * Returns true if all the nodes of this label is offline.
     */
    public boolean isOffline() {
        for (NodeExt n : getNodes()) {
            if(n.toComputer() != null && !n.toComputer().isOffline())
                return false;
        }
        return true;
    }

    /**
     * Returns a human readable text that explains this label.
     */
    public String getDescription() {
        Set<NodeExt> nodes = getNodes();
        if(nodes.isEmpty()) {
            Set<Cloud> clouds = getClouds();
            if(clouds.isEmpty())
                return Messages.Label_InvalidLabel();

            return Messages.Label_ProvisionedFrom(toString(clouds));
        }

        if(nodes.size()==1)
            return nodes.iterator().next().getNodeDescription();

        return Messages.Label_GroupOf(toString(nodes));
    }

    private String toString(Collection<? extends ModelObject> model) {
        boolean first=true;
        StringBuilder buf = new StringBuilder();
        for (ModelObject c : model) {
            if(buf.length()>80) {
                buf.append(",...");
                break;
            }
            if(!first)  buf.append(',');
            else        first=false;
            buf.append(c.getDisplayName());
        }
        return buf.toString();
    }

    /**
     * Returns projects that are tied on this node.
     */
    public List<AbstractProjectExt> getTiedJobs() {
        List<AbstractProjectExt> r = new ArrayList<AbstractProjectExt>();
        for( AbstractProjectExt p : UtilExt.filter(HudsonExt.getInstance().getItems(),AbstractProjectExt.class) ) {
            if(this.equals(p.getAssignedLabel()))
                r.add(p);
        }
        return r;
    }

    public boolean contains(NodeExt node) {
        return getNodes().contains(node);
    }

    /**
     * If there's no such label defined in {@link NodeExt} or {@link Cloud}.
     * This is usually used as a signal that this label is invalid.
     */
    public boolean isEmpty() {
        return getNodes().isEmpty() && getClouds().isEmpty();
    }
    
    /*package*/ void reset() {
        nodes = null;
        clouds = null;
    }

    /**
     * Returns the label that represents "this&amp;rhs"
     */
    public LabelExt and(LabelExt rhs) {
        return new LabelExpression.And(this,rhs);
    }

    /**
     * Returns the label that represents "this|rhs"
     */
    public LabelExt or(LabelExt rhs) {
        return new LabelExpression.Or(this,rhs);
    }

    /**
     * Returns the label that represents "this&lt;->rhs"
     */
    public LabelExt iff(LabelExt rhs) {
        return new LabelExpression.Iff(this,rhs);
    }

    /**
     * Returns the label that represents "this->rhs"
     */
    public LabelExt implies(LabelExt rhs) {
        return new LabelExpression.Implies(this,rhs);
    }

    /**
     * Returns the label that represents "!this"
     */
    public LabelExt not() {
        return new LabelExpression.Not(this);
    }

    /**
     * Returns the label that represents "(this)"
     * This is a pointless operation for machines, but useful
     * for humans who find the additional parenthesis often useful
     */
    public LabelExt paren() {
        return new LabelExpression.Paren(this);
    }

    /**
     * Precedence of the top most operator.
     */
    public abstract LabelOperatorPrecedence precedence();


    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return name.equals(((LabelExt)that).name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public int compareTo(LabelExt that) {
        return this.name.compareTo(that.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public static final class ConverterImpl implements Converter {
        public ConverterImpl() {
        }

        public boolean canConvert(Class type) {
            return LabelExt.class.isAssignableFrom(type);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            LabelExt src = (LabelExt) source;
            writer.setValue(src.getExpression());
        }

        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            return HudsonExt.getInstance().getLabel(reader.getValue());
        }
    }

    /**
     * Convers a whitespace-separate list of tokens into a set of {@link LabelExt}s.
     *
     * @param labels
     *      Strings like "abc def ghi". Can be empty or null.
     * @return
     *      Can be empty but never null. A new writable set is always returned,
     *      so that the caller can add more to the set.
     * @since 1.308
     */
    public static Set<LabelAtomExt> parse(String labels) {
        Set<LabelAtomExt> r = new TreeSet<LabelAtomExt>();
        labels = fixNull(labels);
        if(labels.length()>0)
            for( String l : new QuotedStringTokenizer(labels).toArray())
                r.add(HudsonExt.getInstance().getLabelAtom(l));
        return r;
    }

    /**
     * Obtains a label by its {@linkplain #getName() name}.
     */
    public static LabelExt get(String l) {
        return HudsonExt.getInstance().getLabel(l);
    }

    /**
     * Parses the expression into a label expression tree.
     *
     * TODO: replace this with a real parser later
     */
    public static LabelExt parseExpression(String labelExpression) throws ANTLRException {
        LabelExpressionLexer lexer = new LabelExpressionLexer(new StringReader(labelExpression));
        return new LabelExpressionParser(lexer).expr();
    }
}

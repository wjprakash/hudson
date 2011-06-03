/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
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

import hudson.UtilExt;
import hudson.EnvVars;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.QueueExt.QueueAction;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Records the parameter values used for a build.
 *
 * <P>
 * This object is associated with the build record so that we remember what parameters
 * were used for what build. It is also attached to the queue item to remember parameter
 * that were specified when scheduling.
 */
public class ParametersActionExt implements Action, Iterable<ParameterValueExt>, QueueAction, EnvironmentContributingAction {

    private final List<ParameterValueExt> parameters;

    /**
     * @deprecated since 1.283; kept to avoid warnings loading old build data, but now transient.
     */
    private transient AbstractBuildExt<?, ?> build;

    public ParametersActionExt(List<ParameterValueExt> parameters) {
        this.parameters = parameters;
    }
    
    public ParametersActionExt(ParameterValueExt... parameters) {
        this(Arrays.asList(parameters));
    }

    public void createBuildWrappers(AbstractBuildExt<?,?> build, Collection<? super BuildWrapper> result) {
        for (ParameterValueExt p : parameters) {
            BuildWrapper w = p.createBuildWrapper(build);
            if(w!=null) result.add(w);
        }
    }

    public void buildEnvVars(AbstractBuildExt<?,?> build, EnvVars env) {
        for (ParameterValueExt p : parameters)
            p.buildEnvVars(build,env);
    }

    /**
     * Performs a variable subsitution to the given text and return it.
     */
    public String substitute(AbstractBuildExt<?,?> build, String text) {
        return UtilExt.replaceMacro(text,createVariableResolver(build));
    }

    /**
     * Creates an {@link VariableResolver} that aggregates all the parameters.
     *
     * <p>
     * If you are a {@link BuildStep}, most likely you should call {@link AbstractBuildExt#getBuildVariableResolver()}. 
     */
    public VariableResolver<String> createVariableResolver(AbstractBuildExt<?,?> build) {
        VariableResolver[] resolvers = new VariableResolver[parameters.size()+1];
        int i=0;
        for (ParameterValueExt p : parameters)
            resolvers[i++] = p.createVariableResolver(build);

        resolvers[i] = build.getBuildVariableResolver();

        return new VariableResolver.Union<String>(resolvers);
    }

    public Iterator<ParameterValueExt> iterator() {
        return parameters.iterator();
    }

    public List<ParameterValueExt> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public ParameterValueExt getParameter(String name) {
        for (ParameterValueExt p : parameters)
            if (p.getName().equals(name))
                return p;
        return null;
    }

    public String getDisplayName() {
        return Messages.ParameterAction_DisplayName();
    }

    public String getIconFileName() {
        return "document-properties.gif";
    }

    public String getUrlName() {
        return "parameters";
    }

    /**
     * Allow an other build of the same project to be scheduled, if it has other parameters.
     */
    public boolean shouldSchedule(List<Action> actions) {
        List<ParametersActionExt> others = UtilExt.filter(actions, ParametersActionExt.class);
        if (others.isEmpty()) {
            return !parameters.isEmpty();
        } else {
            // I don't think we need multiple ParametersActions, but let's be defensive
            Set<ParameterValueExt> params = new HashSet<ParameterValueExt>();
            for (ParametersActionExt other: others) {
                params.addAll(other.parameters);
            }
            return !params.equals(new HashSet<ParameterValueExt>(this.parameters));
        }
    }

    private Object readResolve() {
        if (build != null)
            OldDataMonitorExt.report(build, "1.283");
        return this;
    }
}

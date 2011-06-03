/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.AbstractList;

import hudson.Extension;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that <tt>index.jelly</tt> provides
 * a form to enter build parameters. 
 */
public class ParametersDefinitionPropertyExt extends JobPropertyExt<AbstractProjectExt<?, ?>>
        implements Action {

    private final List<ParameterDefinitionExt> parameterDefinitions;

    public ParametersDefinitionPropertyExt(List<ParameterDefinitionExt> parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions;
    }

    public ParametersDefinitionPropertyExt(ParameterDefinitionExt... parameterDefinitions) {
        this.parameterDefinitions = Arrays.asList(parameterDefinitions);
    }
    
    public AbstractProjectExt<?,?> getOwner() {
        return owner;
    }

    public List<ParameterDefinitionExt> getParameterDefinitions() {
        return parameterDefinitions;
    }

    /**
     * Gets the names of all the parameter definitions.
     */
    public List<String> getParameterDefinitionNames() {
        return new AbstractList<String>() {
            public String get(int index) {
                return parameterDefinitions.get(index).getName();
            }

            public int size() {
                return parameterDefinitions.size();
            }
        };
    }

    @Override
    public Collection<Action> getJobActions(AbstractProjectExt<?, ?> job) {
        return Collections.<Action>singleton(this);
    }

    public AbstractProjectExt<?, ?> getProject() {
        return (AbstractProjectExt<?, ?>) owner;
    }

    

    /**
     * Gets the {@link ParameterDefinitionExt} of the given name, if any.
     */
    public ParameterDefinitionExt getParameterDefinition(String name) {
        for (ParameterDefinitionExt pd : parameterDefinitions)
            if (pd.getName().equals(name))
                return pd;
        return null;
    }

    @Extension
    public static class DescriptorImplExt extends JobPropertyDescriptorExt {
        @Override
        public boolean isApplicable(Class<? extends JobExt> jobType) {
            return AbstractProjectExt.class.isAssignableFrom(jobType);
        }


        @Override
        public String getDisplayName() {
            return Messages.ParametersDefinitionProperty_DisplayName();
        }
    }

    public String getDisplayName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}

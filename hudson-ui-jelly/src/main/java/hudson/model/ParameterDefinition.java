/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Tom Huybrechts
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

import hudson.Extension;
import java.beans.ParameterDescriptor;

import java.io.Serializable;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Defines a parameter for a build.
 *
 * <p>
 * In HudsonExt, a user can configure a job to require parameters for a build.
 * For example, imagine a test job that takes the bits to be tested as a parameter.
 *
 * <p>
 * The actual meaning and the purpose of parameters are entirely up to users, so
 * what the concrete parameter implmentation is pluggable. Write subclasses
 * in a plugin and put {@link Extension} on the descriptor to register them.
 *
 * <p>
 * Three classes are used to model build parameters. First is the
 * {@link ParameterDescriptor}, which tells HudsonExt what kind of implementations are
 * available. From {@link ParameterDescriptor#newInstance(StaplerRequest, JSONObject)},
 * HudsonExt creates {@link ParameterDefinitionExt}s based on the job configuration.
 * For example, if the user defines two string parameters "database-type" and
 * "appserver-type", we'll get two {@link StringParameterDefinition} instances
 * with their respective names.
 *
 * <p>
 * When a job is configured with {@link ParameterDefinitionExt} (or more precisely,
 * {@link ParametersDefinitionProperty}, which in turns retains {@link ParameterDefinitionExt}s),
 * user would have to enter the values for the defined build parameters.
 * The {@link #createValue(StaplerRequest, JSONObject)} method is used to convert this
 * form submission into {@link ParameterValueExt} objects, which are then accessible
 * during a build.
 *
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * Instances of {@link ParameterDefinitionExt}s are persisted into job <tt>config.xml</tt>
 * through XStream.
 *
 *
 * <h2>Assocaited Views</h2>
 * <h4>config.jelly</h4>
 * <p>
 * {@link ParameterDefinitionExt} class uses <tt>config.jelly</tt> to provide contribute a form
 * fragment in the job configuration screen. Values entered there is fed back to
 * {@link ParameterDescriptor#newInstance(StaplerRequest, JSONObject)} to create {@link ParameterDefinitionExt}s.
 *
 * <h4>index.jelly</h4>
 * The <tt>index.jelly</tt> view contributes a form fragment in the page where the user
 * enters actual values of parameters for a build. The result of this form submission
 * is then fed to {@link ParameterDefinitionExt#createValue(StaplerRequest, JSONObject)} to
 * create {@link ParameterValueExt}s.
 *
 * TODO: what Jelly pages does this object need for rendering UI?
 * TODO: {@link ParameterValueExt} needs to have some mechanism to expose values to the build
 * @see StringParameterDefinition
 */
@ExportedBean(defaultVisibility=3)
public abstract class ParameterDefinition extends ParameterDefinitionExt implements Serializable {

    public ParameterDefinition(String name) {
        this(name, null);
    }

    public ParameterDefinition(String name, String description) {
        super(name, description);
    }

    @Exported
    @Override
    public String getType() {
    	return super.getType();
    }
    
    @Exported
    @Override
    public String getName() {
        return super.getName();
    }

    @Exported
    @Override
    public String getDescription() {
        return super.getDescription();
    }

    
    /**
     * Create a parameter value from a form submission.
     *
     * <p>
     * This method is invoked when the user fills in the parameter values in the HTML form
     * and submits it to the server.
     */
    public abstract ParameterValueExt createValue(StaplerRequest req, JSONObject jo);
    
    /**
     * Create a parameter value from a GET with query string.
     * If no value is available in the request, it returns a default value if possible, or null.
     *
     * <p>
     * Unlike {@link #createValue(StaplerRequest, JSONObject)}, this method is intended to support
     * the programmatic POST-ing of the build URL. This form is less expressive (as it doesn't support
     * the tree form), but it's more scriptable.
     *
     * <p>
     * If a {@link ParameterDefinitionExt} can't really support this mode of creating a value,
     * you may just always return null.
     */
    public abstract ParameterValueExt createValue(StaplerRequest req);

}

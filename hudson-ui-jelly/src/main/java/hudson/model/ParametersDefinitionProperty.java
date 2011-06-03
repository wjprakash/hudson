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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Extension;
import hudson.model.Descriptor.FormException;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that <tt>index.jelly</tt> provides
 * a form to enter build parameters. 
 */
@ExportedBean(defaultVisibility=2)
public class ParametersDefinitionProperty extends ParametersDefinitionPropertyExt{

  
    public ParametersDefinitionProperty(List<ParameterDefinitionExt> parameterDefinitions) {
        super(parameterDefinitions);
    }

    public ParametersDefinitionProperty(ParameterDefinitionExt... parameterDefinitions) {
       this(Arrays.asList(parameterDefinitions));
    }
    
    
    @Exported
    public List<ParameterDefinitionExt> getParameterDefinitions() {
        return super.getParameterDefinitions();
    }

     
    /**
     * Interprets the form submission and schedules a build for a parameterized job.
     *
     * <p>
     * This method is supposed to be invoked from {@link AbstractProjectExt#doBuild(StaplerRequest, StaplerResponse)}.
     */
    public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(this,"index.jelly").forward(req,rsp);
            return;
        }

        List<ParameterValueExt> values = new ArrayList<ParameterValueExt>();
        
        JSONObject formData = req.getSubmittedForm();
        JSONArray a = JSONArray.fromObject(formData.get("parameter"));

        for (Object o : a) {
            JSONObject jo = (JSONObject) o;
            String name = jo.getString("name");

            ParameterDefinitionExt d = getParameterDefinition(name);
            if(d==null)
                throw new IllegalArgumentException("No such parameter definition: " + name);
            ParameterValueExt parameterValue = d.createValue(req, jo);
            values.add(parameterValue);
        }

    	HudsonExt.getInstance().getQueue().schedule(
                owner, owner.getDelay(req), new ParametersActionExt(values), new CauseActionExt(new CauseExt.UserCause()));

        // send the user back to the job top page.
        rsp.sendRedirect(".");
    }

    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        List<ParameterValueExt> values = new ArrayList<ParameterValueExt>();
        for (ParameterDefinitionExt d: parameterDefinitions) {
        	ParameterValueExt value = d.createValue(req);
        	if (value != null) {
        		values.add(value);
        	} else {
        		throw new IllegalArgumentException("Parameter " + d.getName() + " was missing.");
        	}
        }

    	HudsonExt.getInstance().getQueue().schedule(
                owner, owner.getDelay(req), new ParametersActionExt(values), owner.getBuildCause(req));

        // send the user back to the job top page.
        rsp.sendRedirect(".");
    }

    
    @Extension
    public static class DescriptorImpl extends DescriptorImplExt {
        
        public JobPropertyExt<?> newInstance(StaplerRequest req,
                                          JSONObject formData) throws FormException {
            if (formData.isNullObject()) {
                return null;
            }

            JSONObject parameterized = formData.getJSONObject("parameterized");

            if (parameterized.isNullObject()) {
            	return null;
            }
            
            List<ParameterDefinitionExt> parameterDefinitions = Descriptor.newInstancesFromHeteroList(
                    req, parameterized, "parameter", ParameterDefinitionExt.all());
            if(parameterDefinitions.isEmpty())
                return null;

            return new ParametersDefinitionProperty(parameterDefinitions);
        }


    }
}

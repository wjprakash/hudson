/**
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@Oracle.com
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
package hudson.matrix;

import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import java.io.IOException;
import java.util.HashSet;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Winston Prakash
 */
public class MatrixProject extends MatrixProjectExt{
    
    public MatrixProject(String name) {
        this(Hudson.getInstance(), name);
    }

    public MatrixProject(ItemGroup parent, String name) {
        super(parent, name);
    }
    
    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            MatrixConfiguration item = getItem(token);
            if(item!=null)
            return item;
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }
    
    /**
     * Also delete all the workspaces of the configuration, too.
     */
    @Override
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, FormException{
        HttpResponse rsp = super.doDoWipeOutWorkspace();
        for (MatrixConfiguration c : configurations.values())
            c.doDoWipeOutWorkspace();
        return rsp;
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        JSONObject json = req.getSubmittedForm();

        if(req.getParameter("hasCombinationFilter")!=null) {
            this.combinationFilter = Util.nullify(req.getParameter("combinationFilter"));
        } else {
            this.combinationFilter = null;
        }
        
        if (req.getParameter("hasTouchStoneCombinationFilter")!=null) {
            this.touchStoneCombinationFilter = Util.nullify(req.getParameter("touchStoneCombinationFilter"));
            String touchStoneResultCondition = req.getParameter("touchStoneResultCondition");
            this.touchStoneResultCondition = Result.fromString(touchStoneResultCondition);
        } else {
            this.touchStoneCombinationFilter = null;
        }

        if(req.hasParameter("customWorkspace")) {
            customWorkspace = req.getParameter("customWorkspace.directory");
        } else {
            customWorkspace = null;        
        }
        
        // parse system axes
        DescribableList<AxisExt,AxisDescriptor> newAxes = new DescribableList<AxisExt,AxisDescriptor>(this);
        newAxes.rebuildHetero(req, json, AxisExt.all(),"axis");
        checkAxisNames(newAxes);
        this.axes = new AxisList(newAxes.toList());

        runSequentially = json.has("runSequentially");

        buildWrappers.rebuild(req, json, BuildWrappers.getFor(this));
        builders.rebuildHetero(req, json, Builder.all(), "builder");
        publishers.rebuild(req, json, BuildStepDescriptor.filter(Publisher.all(),this.getClass()));

        rebuildConfigurations();
    }
    
    /**
     * Verifies that AxisExt names are valid and unique.
     */
    private void checkAxisNames(Iterable<AxisExt> newAxes) throws FormException {
        HashSet<String> axisNames = new HashSet<String>();
        for (AxisExt a : newAxes) {
            FormValidation fv = a.getDescriptor().doCheckName(a.getName());
            if (fv.kind!=Kind.OK)
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),fv,"axis.name");

            if (axisNames.contains(a.getName()))
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),"axis.name");
            axisNames.add(a.getName());
        }
    }
}

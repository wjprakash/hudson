/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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


public class RunParameterDefinitionExt extends SimpleParameterDefinitionExt {

    private final String projectName;

    public RunParameterDefinitionExt(String name, String projectName, String description) {
        super(name, description);
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }

    public JobExt getProject() {
        return (JobExt) HudsonExt.getInstance().getItem(projectName);
    }

    @Extension
    public static class DescriptorImplExt extends ParameterDescriptorExt {
        @Override
        public String getDisplayName() {
            return Messages.RunParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/run.html";
        }

    }

    @Override
    public ParameterValueExt getDefaultParameterValue() {
        RunExt<?,?> lastBuild = getProject().getLastBuild();
        if (lastBuild != null) {
        	return createValue(lastBuild.getExternalizableId());
        } else {
        	return null;
        }
    }

    public RunParameterValueExt createValue(String value) {
        return new RunParameterValueExt(getName(), value, getDescription());
    }

}

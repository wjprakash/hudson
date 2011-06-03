/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.cli;

import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.CauseExt;
import hudson.model.ParametersActionExt;
import hudson.model.ParameterValueExt;
import hudson.model.ParametersDefinitionPropertyExt;
import hudson.model.ParameterDefinitionExt;
import hudson.Extension;
import hudson.AbortException;
import hudson.model.ItemExt;
import hudson.util.EditDistance;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.concurrent.Future;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.io.PrintStream;

/**
 * Builds a job, and optionally waits until its completion.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class BuildCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Builds a job, and optionally waits until its completion.";
    }

    @Argument(metaVar="JOB",usage="Name of the job to build",required=true)
    public AbstractProjectExt<?,?> job;

    @Option(name="-s",usage="Wait until the completion/abortion of the command")
    public boolean sync = false;

    @Option(name="-p",usage="Specify the build parameters in the key=value format.")
    public Map<String,String> parameters = new HashMap<String, String>();

    protected int run() throws Exception {
        job.checkPermission(ItemExt.BUILD);

        ParametersActionExt a = null;
        if (!parameters.isEmpty()) {
            ParametersDefinitionPropertyExt pdp = job.getProperty(ParametersDefinitionPropertyExt.class);
            if (pdp==null)
                throw new AbortException(job.getFullDisplayName()+" is not parameterized but the -p option was specified");

            List<ParameterValueExt> values = new ArrayList<ParameterValueExt>(); 

            for (Entry<String, String> e : parameters.entrySet()) {
                String name = e.getKey();
                ParameterDefinitionExt pd = pdp.getParameterDefinition(name);
                if (pd==null)
                    throw new AbortException(String.format("\'%s\' is not a valid parameter. Did you mean %s?",
                            name, EditDistance.findNearest(name, pdp.getParameterDefinitionNames())));
                values.add(pd.createValue(this,e.getValue()));
            }
            
            a = new ParametersActionExt(values);
        }

        Future<? extends AbstractBuildExt> f = job.scheduleBuild2(0, new CLICause(), a);
        if (!sync)  return 0;

        AbstractBuildExt b = f.get();    // wait for the completion
        stdout.println("Completed "+b.getFullDisplayName()+" : "+b.getResult());
        return b.getResult().ordinal;
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(
            "Starts a build, and optionally waits for a completion.\n" +
            "Aside from general scripting use, this command can be\n" +
            "used to invoke another job from within a build of one job.\n" +
            "With the -s option, this command changes the exit code based on\n" +
            "the outcome of the build (exit code 0 indicates a success.)\n"
        );
    }

    // TODO: CLI can authenticate as different users, so should record which user here..
    public static class CLICause extends CauseExt {
        public String getShortDescription() {
            return "Started by command line";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CLICause;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }
}


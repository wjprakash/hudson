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
package hudson.model;

import hudson.Functions;
import hudson.console.AnnotatedLargeText;
import hudson.console.ExpandableDetailsNote;
import hudson.scm.ChangeLogSetExt;
import hudson.scm.ChangeLogSetExt.Entry;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;
import javax.servlet.ServletException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 *
 * @author Winston Prakash
 */
public abstract class AbstractBuild extends AbstractBuildExt {

    @Exported(name = "builtOn")
    @Override
    public String getBuiltOnStr() {
        return super.getBuiltOnStr();
    }

    /**
     * Used to render the side panel "Back to project" link.
     *
     * <p>
     * In a rare situation where a build can be reached from multiple paths,
     * returning different URLs from this method based on situations might
     * be desirable.
     *
     * <p>
     * If you override this method, you'll most likely also want to override
     * {@link #getDisplayName()}.
     */
    public String getUpUrl() {
        return Functions.getNearestAncestorUrl(Stapler.getCurrentRequest(), getParent()) + '/';
    }

    /**
     * List of users who committed a change since the last non-broken build till now.
     *
     * <p>
     * This list at least always include people who made changes in this build, but
     * if the previous build was a failure it also includes the culprit list from there.
     *
     * @return
     *      can be empty but never null.
     */
    @Exported
    @Override
    public Set<UserExt> getCulprits() {
        return super.getCulprits();
    }

    /**
     * Gets the changes incorporated into this build.
     *
     * @return never null.
     */
    @Exported
    @Override
    public ChangeLogSetExt<? extends Entry> getChangeSet() {
        return super.getChangeSet();
    }

    protected abstract class AbstractRunner extends AbstractRunnerExt {

        @Override
        public ResultExt run(BuildListener listener) throws Exception {
            ResultExt result = super.run(listener);
            NodeExt node = getCurrentNode();
            ComputerExt c = node.toComputer();
            if (c == null || c.isOffline()) {
                // As can be seen in HUDSON-5073, when a build fails because of the slave connectivity problem,
                // error message doesn't point users to the slave. So let's do it here.
                listener.hyperlink("/computer/" + builtOn + "/log", "Looks like the node went offline during the build. Check the slave log for the details.");

                // grab the end of the log file. This might not work very well if the slave already
                // starts reconnecting. Fixing this requires a ring buffer in slave logs.
                AnnotatedLargeText<ComputerExt> log = c.getLogText();
                StringWriter w = new StringWriter();
                log.writeHtmlTo(Math.max(0, c.getLogFile().length() - 10240), w);

                listener.getLogger().print(ExpandableDetailsNote.encodeTo("details", w.toString()));
                listener.getLogger().println();
            }
            return super.run(listener);
        }
    }

    //
    // web methods
    //
    /**
     * Stops this build if it's still going.
     *
     * If we use this/executor/stop URL, it causes 404 if the build is already killed,
     * as {@link #getExecutor()} returns null.
     */
    public synchronized void doStop(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ExecutorExt e = getExecutor();
        if (e != null) {
            Executor.doStop(e, req, rsp);
        } else // nothing is building
        {
            rsp.forwardToPreviousPage(req);
        }
    }
}

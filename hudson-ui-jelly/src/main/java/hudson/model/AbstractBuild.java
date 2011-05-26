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
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.io.IOException;
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
public abstract class AbstractBuild extends AbstractBuildExt{
    
    @Exported(name="builtOn")
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
        return Functions.getNearestAncestorUrl(Stapler.getCurrentRequest(),getParent())+'/';
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
    public Set<User> getCulprits() {
        return super.getCulprits();
    }
    
    /**
     * Gets the changes incorporated into this build.
     *
     * @return never null.
     */
    @Exported
    @Override
    public ChangeLogSet<? extends Entry> getChangeSet() {
        return super.getChangeSet();
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
        Executor e = getExecutor();
        if (e!=null)
            e.doStop(req,rsp);
        else
            // nothing is building
            rsp.forwardToPreviousPage(req);
    }
    
}

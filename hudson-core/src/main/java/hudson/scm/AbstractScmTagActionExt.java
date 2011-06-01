/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.scm;

import hudson.model.AbstractBuildExt;
import hudson.model.TaskActionExt;
import hudson.model.BuildBadgeAction;
import hudson.security.Permission;
import hudson.security.ACL;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Common part of {@link CVSSCM.TagAction} and {@link SubversionTagAction}.
 *
 * <p>
 * This class implements the action that tags the modules. Derived classes
 * need to provide <tt>tagForm.jelly</tt> view that displays a form for
 * letting user start tagging.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractScmTagActionExt extends TaskActionExt implements BuildBadgeAction {
    protected final AbstractBuildExt build;

    protected AbstractScmTagActionExt(AbstractBuildExt build) {
        this.build = build;
    }

    public final String getUrlName() {
        // to make this consistent with CVSSCM, even though the name is bit off
        return "tagBuild";
    }

    /**
     * Defaults to {@link SCM#TAG}.
     */
    protected Permission getPermission() {
        return SCMExt.TAG;
    }

    public AbstractBuildExt getBuild() {
        return build;
    }

    /**
     * This message is shown as the tool tip of the build badge icon.
     */
    public String getTooltip() {
        return null;
    }

    /**
     * Returns true if the build is tagged already.
     */
    public abstract boolean isTagged();

    protected ACL getACL() {
        return build.getACL();
    }

    protected synchronized String chooseAction() {
        if(workerThread!=null)
            return "inProgress.jelly";
        return "tagForm.jelly";
    }

}

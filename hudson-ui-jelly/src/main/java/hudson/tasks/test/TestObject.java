/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Tom Huybrechts, Yahoo!, Inc.
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
package hudson.tasks.test;

import hudson.model.*;
import java.util.logging.Level;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.Functions;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Base class for all test result objects.
 * For compatibility with code that expects this class to be in hudson.tasks.junit,
 * we've created a pure-abstract class, hudson.tasks.junit.TestObject. That
 * stub class is deprecated; instead, people should use this class.  
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class TestObject extends TestObjectExt {

    private static final Logger LOGGER = Logger.getLogger(TestObject.class.getName());

    /**
     * Computes the relative path to get to this test object from <code>it</code>. If
     * <code>it</code> does not appear in the parent chain for this object, a
     * relative path from the server root will be returned.
     *
     * @return A relative path to this object, potentially from the top of the
     * HudsonExt object model
     */
    public String getRelativePathFrom(TestObject it) {


        // if (it is one of my ancestors) {
        //    return a relative path from it
        // } else {
        //    return a complete path starting with "/"
        // }
        if (it == this) {
            return ".";
        }

        StringBuilder buf = new StringBuilder();
        TestObjectExt next = this;
        TestObjectExt cur = this;
        // Walk up my ancesotors from leaf to root, looking for "it"
        // and accumulating a relative url as I go
        while (next != null && it != next) {
            cur = next;
            buf.insert(0, '/');
            buf.insert(0, cur.getSafeName());
            next = cur.getParent();
        }
        if (it == next) {
            return buf.toString();
        } else {
            // Keep adding on to the string we've built so far

            // Start with the test result action
            AbstractTestResultActionExt action = getTestResultAction();
            if (action == null) {
                LOGGER.warning("trying to get relative path, but we can't determine the action that owns this result.");
                return ""; // this won't take us to the right place, but it also won't 404.
            }
            buf.insert(0, '/');
            buf.insert(0, action.getUrlName());

            // Now the build
            AbstractBuildExt<?, ?> myBuild = cur.getOwner();
            if (myBuild == null) {
                LOGGER.warning("trying to get relative path, but we can't determine the build that owns this result.");
                return ""; // this won't take us to the right place, but it also won't 404. 
            }
            buf.insert(0, '/');
            buf.insert(0, myBuild.getUrl());

            // If we're inside a stapler request, just delegate to HudsonExt.FunctionsExt to get the relative path!
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req != null && myBuild instanceof ItemExt) {
                buf.insert(0, '/');
                // Ugly but I don't see how else to convince the compiler that myBuild is an ItemExt
                ItemExt myBuildAsItem = (ItemExt) myBuild;
                buf.insert(0, Functions.getRelativeLinkTo(myBuildAsItem));
            } else {
                // We're not in a stapler request. Okay, give up.
                LOGGER.info("trying to get relative path, but it is not my ancestor, and we're not in a stapler request. Trying absolute hudson url...");
                String hudsonRootUrl = Hudson.getInstance().getRootUrl();
                if (hudsonRootUrl == null || hudsonRootUrl.length() == 0) {
                    LOGGER.warning("Can't find anything like a decent hudson url. Punting, returning empty string.");
                    return "";

                }
                buf.insert(0, '/');
                buf.insert(0, hudsonRootUrl);
            }

            LOGGER.log(Level.INFO, "Here''s our relative path: {0}", buf.toString());
            return buf.toString();
        }

    }

    /**
     * Exposes this object through the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    public Object getDynamic(String token, StaplerRequest req,
            StaplerResponse rsp) {
        for (Action a : getTestActions()) {
            if (a == null) {
                continue; // be defensive
            }
            String urlName = a.getUrlName();
            if (urlName == null) {
                continue;
            }
            if (urlName.equals(token)) {
                return a;
            }
        }
        return null;
    }

    public synchronized HttpResponse doSubmitDescription(
            @QueryParameter String description) throws IOException,
            ServletException {
        if (getOwner() == null) {
            LOGGER.severe("getOwner() is null, can't save description.");
        } else {
            getOwner().checkPermission(RunExt.UPDATE);
            setDescription(description);
            getOwner().save();
        }

        return new HttpRedirect(".");
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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

import hudson.UtilExt;
import hudson.security.AccessControlled;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Defines a bunch of static methods to be used as a "mix-in" for {@link ItemGroup}
 * implementations. Not meant for a consumption from outside {@link ItemGroup}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ItemGroupMixIn extends ItemGroupMixInExt{
    
    protected ItemGroupMixIn(ItemGroup parent, AccessControlled acl) {
        super(parent, acl);
    }


    /**
     * Creates a {@link TopLevelItem} from the submission of the '/lib/hudson/newFromList/formList'
     * or throws an exception if it fails.
     */
    public synchronized TopLevelItem createTopLevelItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        acl.checkPermission(JobExt.CREATE);

        TopLevelItem result;

        String requestContentType = req.getContentType();
        if(requestContentType==null)
            throw new FailureExt("No Content-Type header set");

        boolean isXmlSubmission = requestContentType.startsWith("application/xml") || requestContentType.startsWith("text/xml");

        String name = req.getParameter("name");
        if(name==null)
            throw new FailureExt("Query parameter 'name' is required");

        {// check if the name looks good
            HudsonExt.checkGoodName(name);
            name = name.trim();
            if(parent.getItem(name)!=null)
                throw new FailureExt(Messages.Hudson_JobAlreadyExists(name));
        }

        String mode = req.getParameter("mode");
        if(mode!=null && mode.equals("copy")) {
            String from = req.getParameter("from");

            // resolve a name to ItemExt
            ItemExt src = parent.getItem(from);
            if (src==null)
                src = HudsonExt.getInstance().getItemByFullName(from);

            if(src==null) {
                if(UtilExt.fixEmpty(from)==null)
                    throw new FailureExt("Specify which job to copy");
                else
                    throw new FailureExt("No such job: "+from);
            }
            if (!(src instanceof TopLevelItem))
                throw new FailureExt(from+" cannot be copied");

            result = copy((TopLevelItem) src,name);
        } else {
            if(isXmlSubmission) {
                result = createProjectFromXML(name, req.getInputStream());
                rsp.setStatus(HttpServletResponse.SC_OK);
                return result;
            } else {
                if(mode==null)
                    throw new FailureExt("No mode given");

                // create empty job and redirect to the project config screen
                result = createProject(Items.getDescriptor(mode), name, true);
            }
        }

        rsp.sendRedirect2(redirectAfterCreateItem(req, result));
        return result;
    }

    /**
     * Computes the redirection target URL for the newly created {@link TopLevelItem}.
     */
    protected String redirectAfterCreateItem(StaplerRequest req, TopLevelItem result) throws IOException {
        return req.getContextPath()+'/' + result.getUrl() + "configure";
    }

}

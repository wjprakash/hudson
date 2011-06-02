/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Tom Huybrechts
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

import hudson.FeedAdapter;
import hudson.model.Descriptor.FormException;
import hudson.util.RunListExt;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user.
 *
 * <p>
 * In HudsonExt, {@link User} objects are created in on-demand basis;
 * for example, when a build is performed, its change log is computed
 * and as a result commits from users who HudsonExt has never seen may be discovered.
 * When this happens, new {@link User} object is created.
 *
 * <p>
 * If the persisted record for an user exists, the information is loaded at
 * that point, but if there's no such record, a fresh instance is created from
 * thin air (this is where {@link UserPropertyDescriptor#newInstance(User)} is
 * called to provide initial {@link UserProperty} objects.
 *
 * <p>
 * Such newly created {@link User} objects will be simply GC-ed without
 * ever leaving the persisted record, unless {@link User#save()} method
 * is explicitly invoked (perhaps as a result of a browser submitting a
 * configuration.)
 *
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class User extends UserExt {

    private User(String id, String fullName) {
        super(id, fullName);
    }

     

    @Exported
    public String getId() {
         return super.getId();
    }

    /**
     * The URL of the user page.
     */
    @Exported(visibility=999)
    public String getAbsoluteUrl() {
        return Hudson.getInstance().getRootUrl() + getUrl();
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     *
     * @return
     *      never null.
     */
    @Exported(visibility=999)
    @Override
    public String getFullName() {
        return super.getFullName();
    }

    @Exported
    @Override
    public String getDescription() {
        return super.getDescription();
    }

    /**
     * List of all {@link UserProperty}s exposed primarily for the remoting API.
     */
    @Exported(name="property",inline=true)
    public List<UserPropertyExt> getAllProperties() {
        return super.getAllProperties();
    }
    
    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(HudsonExt.ADMINISTER);

        description = req.getParameter("description");
        save();
        
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Exposed remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Accepts submission from the configuration page.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(HudsonExt.ADMINISTER);

        fullName = req.getParameter("fullName");
        description = req.getParameter("description");

        JSONObject json = req.getSubmittedForm();

        List<UserPropertyExt> props = new ArrayList<UserPropertyExt>();
        int i = 0;
        for (UserPropertyDescriptor d : UserPropertyExt.all()) {
            UserPropertyExt p = getProperty(d.clazz);

            JSONObject o = json.optJSONObject("userProperty" + (i++));
            if (o!=null) {
                if (p != null) {
                    p = p.reconfigure(req, o);
                } else {
                    p = d.newInstance(req, o);
                }
                p.setUser(this);
            }

            props.add(p);
        }
        this.properties = props;

        save();

        rsp.sendRedirect(".");
    }

    /**
     * Deletes this user from HudsonExt.
     */
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        requirePOST();
        checkPermission(HudsonExt.ADMINISTER);
        if (id.equals(HudsonExt.getAuthentication().getName())) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Cannot delete self");
            return;
        }

        delete();

        rsp.sendRedirect2("../..");
    }

    public void doRssAll(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " all builds", RunListExt.fromRuns(getBuilds()), Run.FEED_ADAPTER);
    }

    public void doRssFailed(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rss(req, rsp, " regression builds", RunListExt.fromRuns(getBuilds()).regressionOnly(), Run.FEED_ADAPTER);
    }

    public void doRssLatest(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final List<RunExt> lastBuilds = new ArrayList<RunExt>();
        for (final TopLevelItem item : HudsonExt.getInstance().getItems()) {
            if (!(item instanceof JobExt)) continue;
            for (RunExt r = ((JobExt) item).getLastBuild(); r != null; r = r.getPreviousBuild()) {
                if (!(r instanceof AbstractBuildExt)) continue;
                final AbstractBuildExt b = (AbstractBuildExt) r;
                if (b.hasParticipant(this)) {
                    lastBuilds.add(b);
                    break;
                }
            }
        }
        rss(req, rsp, " latest build", RunListExt.fromRuns(lastBuilds), Run.FEED_ADAPTER_LATEST);
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunListExt runs, FeedAdapter adapter)
            throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(), runs.newBuilds(), adapter, req, rsp);
    }
}

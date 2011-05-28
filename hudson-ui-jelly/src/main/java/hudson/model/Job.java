/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Martin Eigenbrodt, 
 * Matthew R. Harrah, Red Hat, Inc., Stephen Connolly, Tom Huybrechts, Winston Prakash
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.search.QuickSilver;
import hudson.tasks.LogRotator;
import hudson.util.RunList;
import hudson.widgets.Widget;

import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;
import net.sf.json.JSONException;

import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 * A job is an runnable entity under the monitoring of HudsonExt.
 * 
 * <p>
 * Every time it "runs", it will be recorded as a {@link RunExt} object.
 *
 * <p>
 * To create a custom job type, extend {@link TopLevelItemDescriptor} and put {@link Extension} on it.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Job<JobT extends Job<JobT, RunT>, RunT extends RunExt<JobT, RunT>> extends JobExt<JobT , RunT > implements StaplerOverridable {


    protected Job(ItemGroup parent, String name) {
        super(parent, name);
    }


    @Exported
    @Override
    public boolean isInQueue() {
        return super.isInQueue();
    }

    /**
     * If this job is in the build queue, return its item.
     */
    @Exported
    @Override
    public QueueExt.Item getQueueItem() {
        return super.getQueueItem();
    }

    /**
     * If true, it will keep all the build logs of dependency components.
     */
    @Exported
    @Override
    public boolean isKeepDependencies() {
        return super.isKeepDependencies();
    }

    /**
     * Peeks the next build number.
     */
    @Exported
    @Override
    public int getNextBuildNumber() {
        return super.getNextBuildNumber();
    }

    
    /**
     * List of all {@link JobPropertyExt} exposed primarily for the remoting API.
     * @since 1.282
     */
    @Exported(name = "property", inline = true)
    @Override
    public List<JobPropertyExt<? super JobT>> getAllProperties() {
        return super.getAllProperties();
    }
 
    /**
     * Returns true if we should display "build now" icon
     */
    @Exported
    @Override
    public abstract boolean isBuildable();

    /**
     * Gets the read-only view of all the builds.
     * 
     * @return never null. The first entry is the latest build.
     */
    @Exported
    @WithBridgeMethods(List.class)
    @Override
    public RunList<RunT> getBuilds() {
        return super.getBuilds();
    }

     

    @Override
    public Object getDynamic(String token, StaplerRequest req,
            StaplerResponse rsp) {
        try {
            // try to interpret the token as build number
            return _getRuns().get(Integer.valueOf(token));
        } catch (NumberFormatException e) {
            // try to map that to widgets
            for (Widget w : getWidgets()) {
                if (w.getUrlName().equals(token)) {
                    return w;
                }
            }

            // is this a permalink?
            for (Permalink p : getPermalinks()) {
                if (p.getId().equals(token)) {
                    return p.resolve(this);
                }
            }

            return super.getDynamic(token, req, rsp);
        }
    }

    

    /**
     * Returns the last build.
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastBuild() {
        return super.getLastBuild();
    }

    /**
     * Returns the oldest build in the record.
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getFirstBuild() {
        return super.getLastBuild();
    }

    /**
     * Returns the last successful build, if any. Otherwise null. A successful build
     * would include either {@link Result#SUCCESS} or {@link Result#UNSTABLE}.
     * 
     * @see #getLastStableBuild()
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastSuccessfulBuild() {
        return super.getLastSuccessfulBuild();
    }

    /**
     * Returns the last build that was anything but stable, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastUnsuccessfulBuild() {
        return super.getLastUnsuccessfulBuild();
    }

    /**
     * Returns the last unstable build, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastUnstableBuild() {
        return super.getLastUnstableBuild();
    }

    /**
     * Returns the last stable build, if any. Otherwise null.
     * @see #getLastSuccessfulBuild
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastStableBuild() {
        return super.getLastStableBuild();
    }

    /**
     * Returns the last failed build, if any. Otherwise null.
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastFailedBuild() {
        return super.getLastFailedBuild();
    }

    /**
     * Returns the last completed build, if any. Otherwise null.
     */
    @Exported
    @QuickSilver
    @Override
    public RunT getLastCompletedBuild() {
        return super.getLastCompletedBuild();
    }

    /**
     * Used as the color of the status ball for the project.
     */
    @Exported(visibility = 2, name = "color")
    @Override
    public BallColorExt getIconColor() {
        return super.getIconColor();
    }

    

    @Exported(name = "healthReport")
    @Override
    public List<HealthReportExt> getBuildHealthReports() {
         return super.getBuildHealthReports();
    }

    

    //
    //
    // actions
    //
    //
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");

        keepDependencies = req.getParameter("keepDependencies") != null;

        try {
            properties.clear();

            JSONObject json = req.getSubmittedForm();

            if (req.getParameter("logrotate") != null) {
                logRotator = LogRotator.DESCRIPTOR.newInstance(req, json.getJSONObject("logrotate"));
            } else {
                logRotator = null;
            }

            int i = 0;
            for (JobPropertyDescriptorExt d : JobPropertyDescriptorExt.getPropertyDescriptors(Job.this.getClass())) {
                String name = "jobProperty" + (i++);
                JSONObject config = json.getJSONObject(name);
                JobPropertyExt prop = d.newInstance(req, config);
                if (prop != null) {
                    prop.setOwner(this);
                    properties.add(prop);
                }
            }

            submit(req, rsp);

            save();

            String newName = req.getParameter("name");
            if (newName != null && !newName.equals(name)) {
                // check this error early to avoid HTTP response splitting.
                HudsonExt.checkGoodName(newName);
                rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            } else {
                rsp.sendRedirect(".");
            }
        } catch (JSONException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("Failed to parse form data. Please report this problem as a bug");
            pw.println("JSON=" + req.getSubmittedForm());
            pw.println();
            e.printStackTrace(pw);

            rsp.setStatus(SC_BAD_REQUEST);
            sendError(sw.toString(), req, rsp, true);
        }
    }

    /**
     * Derived class can override this to perform additional config submission
     * work.
     */
    protected void submit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, FormException {
    }

    /**
     * Accepts and serves the job description
     */
    public void doDescription(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if (req.getMethod().equals("GET")) {
            //read
            rsp.setContentType("text/plain;charset=UTF-8");
            rsp.getWriter().write(this.getDescription());
            return;
        }
        if (req.getMethod().equals("POST")) {
            checkPermission(CONFIGURE);

            // submission
            if (req.getParameter("description") != null) {
                this.setDescription(req.getParameter("description"));
                rsp.sendError(SC_NO_CONTENT);
                return;
            }
        }

        // huh?
        rsp.sendError(SC_BAD_REQUEST);
    }

    /**
     * Returns the image that shows the current buildCommand status.
     */
    public void doBuildStatus(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        rsp.sendRedirect2(req.getContextPath() + "/images/48x48/" + getBuildStatusUrl());
    }


    /**
     * Renames this job.
     */
    public/* not synchronized. see renameTo() */ void doDoRename(
            StaplerRequest req, StaplerResponse rsp) throws IOException,
            ServletException {
        requirePOST();
        // rename is essentially delete followed by a create
        checkPermission(CREATE);
        checkPermission(DELETE);

        String newName = req.getParameter("newName");
        HudsonExt.checkGoodName(newName);

        if (isBuilding()) {
            // redirect to page explaining that we can't rename now
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            return;
        }

        renameTo(newName);
        // send to the new job page
        // note we can't use getUrl() because that would pick up old name in the
        // Ancestor.getUrl()
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl()
                + getShortUrl());
    }

    public void doRssAll(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        rss(req, rsp, " all builds", getBuilds());
    }

    public void doRssFailed(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        rss(req, rsp, " failed builds", getBuilds().failureOnly());
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix,
            RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName() + suffix, getUrl(), runs.newBuilds(),
                RunExt.FEED_ADAPTER, req, rsp);
    }

}

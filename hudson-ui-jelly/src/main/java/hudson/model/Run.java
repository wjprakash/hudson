/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Red Hat, Inc., Tom Huybrechts, Romain Seguy, Yahoo! Inc.,
 * Darek Ostolski
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

import hudson.console.AnnotatedLargeText;
import hudson.FunctionsExt;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.FeedAdapter;
import hudson.FilePathExt;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.RunListener;
import hudson.tasks.LogRotatorExt;
import hudson.tasks.MailerExt;
import hudson.util.FlushProofOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A particular execution of {@link JobExt}.
 *
 * <p>
 * Custom {@link RunExt} type is always used in conjunction with
 * a custom {@link JobExt} type, so there's no separate registration
 * mechanism for custom {@link RunExt} types.
 *
 * @author Kohsuke Kawaguchi
 * @see RunListener
 */
@ExportedBean
public abstract class Run<JobT extends JobExt<JobT, RunT>, RunT extends Run<JobT, RunT>>
        extends RunExt<JobT, RunT> {

    /**
     * Creates a new {@link RunExt}.
     */
    protected Run(JobT job) throws IOException {
        super(job);

    }

    /**
     * Constructor for creating a {@link RunExt} object in
     * an arbitrary state.
     */
    protected Run(JobT job, Calendar timestamp) {
        super(job, timestamp);
    }

    protected Run(JobT job, long timestamp) {
        super(job, timestamp);
    }

    /**
     * Loads a run from a log file.
     */
    protected Run(JobT project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Returns the build result.
     *
     * <p>
     * When a build is {@link #isBuilding() in progress}, this method
     * returns an intermediate result.
     */
    @Exported
    public ResultExt getResult() {
        return super.getResult();
    }

    /**
     * Returns true if the build is not completed yet.
     * This includes "not started yet" state.
     */
    @Exported
    public boolean isBuilding() {
        return super.isBuilding();
    }

    /**
     * Returns true if this log file should be kept and not deleted.
     *
     * This is used as a signal to the {@link LogRotator}.
     */
    @Exported
    public final boolean isKeepLog() {
        return super.isKeepLog();
    }

    /**
     * When the build is scheduled.
     */
    @Exported
    public Calendar getTimestamp() {
        return super.getTimestamp();
    }

    @Exported
    public String getDescription() {
        return super.getDescription();
    }

    /**
     * Gets the millisecond it took to build.
     */
    @Exported
    public long getDuration() {
        return super.getDuration();
    }

    @Exported
    public String getFullDisplayName() {
        return super.getFullDisplayName();
    }

    @Exported(visibility = 2)
    public int getNumber() {
        return super.getNumber();
    }

    /**
     * Returns the URL of this {@link RunExt}, relative to the context root of HudsonExt.
     *
     * @return
     *      String like "job/foo/32/" with trailing slash but no leading slash. 
     */
    // I really messed this up. I'm hoping to fix this some time
    // it shouldn't have trailing '/', and instead it should have leading '/'
    public String getUrl() {
        return project.getUrl() + getNumber() + '/';
    }

    /**
     * Obtains the absolute URL to this build.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it won't work with
     *      network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references
     *      (even this won't work for the same reason, which should be fixed.)
     */
    @Exported(visibility = 2, name = "url")
    public final String getAbsoluteUrl() {
        return project.getAbsoluteUrl() + getNumber() + '/';
    }

    public final String getSearchUrl() {
        return getNumber() + "/";
    }

    /**
     * Unique ID of this build.
     */
    @Exported
    public String getId() {
        return super.getId();
    }

    /**
     * Gets the artifacts (relative to {@link #getArtifactsDir()}.
     */
    @Exported
    public List<Artifact> getArtifacts() {
        return super.getArtifacts();
    }

    /**
     * Used from <tt>console.jelly</tt> to write annotated log to the given output.
     *
     * @since 1.349
     */
    public void writeLogTo(long offset, XMLOutput out) throws IOException {
        try {
            getLogText().writeHtmlTo(offset, out.asWriter());
        } catch (IOException e) {
            // try to fall back to the old getLogInputStream()
            // mainly to support .gz compressed files
            // In this case, console annotation handling will be turned off.
            InputStream input = getLogInputStream();
            try {
                IOUtils.copy(input, out.asWriter());
            } finally {
                IOUtils.closeQuietly(input);
            }
        }
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(), getCharset(), !isLogUpdated(), this);
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Serves the artifacts.
     */
    public DirectoryBrowserSupportExt doArtifact() {
        if (FunctionsExt.isArtifactsPermissionEnabled()) {
            checkPermission(ARTIFACTS);
        }
        return new DirectoryBrowserSupportExt(this, new FilePathExt(getArtifactsDir()), project.getDisplayName() + ' ' + getDisplayName(), "package.gif", true);
    }

    /**
     * Returns the build number in the body.
     */
    public void doBuildNumber(StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.getWriter().print(number);
    }

    /**
     * Returns the build time stamp in the body.
     */
    public void doBuildTimestamp(StaplerRequest req, StaplerResponse rsp, @QueryParameter String format) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(HttpServletResponse.SC_OK);
        DateFormat df = format == null
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH)
                : new SimpleDateFormat(format, req.getLocale());
        rsp.getWriter().print(df.format(getTime()));
    }

    /**
     * Sends out the raw console output.
     */
    public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        // Prevent jelly from flushing stream so Content-Length header can be added afterwards
        FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
        getLogText().writeLogTo(0, out);
        out.close();
    }

    /**
     * Handles incremental log output.
     * @deprecated as of 1.352
     *      Use {@code getLogText().doProgressiveText(req,rsp)}
     */
    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req, rsp);
    }

    public void doToggleLogKeep(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        keepLog(!keepLog);
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Deletes the build when the button is pressed.
     */
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        requirePOST();
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why != null) {
            sendError(Messages.Run_UnableToDelete(toString(), why), req, rsp);
            return;
        }

        delete();
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl());
    }

    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars env = super.getEnvironment(log);
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {
            env.put("HUDSON_URL", rootUrl);
            env.put("BUILD_URL", rootUrl + getUrl());
            env.put("JOB_URL", rootUrl + getParent().getUrl());
        }
        return env;
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    public HttpResponse doConfigSubmit(StaplerRequest req) throws IOException, ServletException, FormException {
        checkPermission(UPDATE);
        BulkChange bc = new BulkChange(this);
        try {
            JSONObject json = req.getSubmittedForm();
            submit(json);
            bc.commit();
        } finally {
            bc.abort();
        }
        return HttpResponses.redirectToDot();
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Object result = super.getDynamic(token, req, rsp);
        if (result == null) // Next/Previous Build links on an action page (like /job/Abc/123/testReport)
        // will also point to same action (/job/Abc/124/testReport), but other builds
        // may not have the action.. tell browsers to redirect up to the build page.
        {
            result = new RedirectUp();
        }
        return result;
    }
    
    /**
     * {@link FeedAdapter} to produce feed from the summary of this build.
     */
    public static final FeedAdapter<Run> FEED_ADAPTER = new DefaultFeedAdapter();

    /**
     * {@link FeedAdapter} to produce feeds to show one build per project.
     */
    public static final FeedAdapter<Run> FEED_ADAPTER_LATEST = new DefaultFeedAdapter() {
        /**
         * The entry unique ID needs to be tied to a project, so that
         * new builds will replace the old result.
         */
        @Override
        public String getEntryID(Run e) {
            // can't use a meaningful year field unless we remember when the job was created.
            return "tag:hudson.java.net,2008:"+ e.getParent().getAbsoluteUrl();
        }
    };
    
    private static class DefaultFeedAdapter implements FeedAdapter<Run> {
        public String getEntryTitle(Run entry) {
            return entry+" ("+entry.getBuildStatusSummary().message+")";
        }

        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        public String getEntryID(Run entry) {
            return "tag:" + "hudson.java.net,"
                + entry.getTimestamp().get(Calendar.YEAR) + ":"
                + entry.getParent().getName()+':'+entry.getId();
        }

        public String getEntryDescription(Run entry) {
            // TODO: this could provide some useful details
            return null;
        }

        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }

        public String getEntryAuthor(Run entry) {
            return MailerExt.descriptor().getAdminAddress();
        }
    }

    public static class RedirectUp {

        public void doDynamic(StaplerResponse rsp) throws IOException {
            // Compromise to handle both browsers (auto-redirect) and programmatic access
            // (want accurate 404 response).. send 404 with javscript to redirect browsers.
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            rsp.setContentType("text/html;charset=UTF-8");
            PrintWriter out = rsp.getWriter();
            out.println("<html><head>"
                    + "<meta http-equiv='refresh' content='1;url=..'/>"
                    + "<script>window.location.replace('..');</script>"
                    + "</head>"
                    + "<body style='background-color:white; color:white;'>"
                    + "Not found</body></html>");
            out.flush();
        }
    }
}

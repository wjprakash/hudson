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
package hudson;

import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.net.URL;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Winston Prakash
 */
public class Plugin extends PluginExt {

    /**
     * @since 1.233
     * @deprecated as of 1.305 override {@link #configure(StaplerRequest,JSONObject)} instead
     */
    public void configure(JSONObject formData) throws IOException, ServletException, FormException {
    }

    /**
     * Handles the submission for the system configuration.
     *
     * <p>
     * If this class defines <tt>config.jelly</tt> view, be sure to
     * override this method and persists the submitted values accordingly.
     *
     * <p>
     * The following is a sample <tt>config.jelly</tt> that you can start yours with:
     * <pre><xmp>
     * <j:jelly xmlns:j="jelly:core" xmlns:st="jelly:stapler" xmlns:d="jelly:define" xmlns:l="/lib/layout" xmlns:t="/lib/hudson" xmlns:f="/lib/form">
     *   <f:section title="Locale">
     *     <f:entry title="${%Default Language}" help="/plugin/locale/help/default-language.html">
     *       <f:textbox name="systemLocale" value="${it.systemLocale}" />
     *     </f:entry>
     *   </f:section>
     * </j:jelly>
     * </xmp></pre>
     *
     * <p>
     * This allows you to access data as {@code formData.getString("systemLocale")}
     *
     * <p>
     * If you are using this method, you'll likely be interested in
     * using {@link #save()} and {@link #load()}.
     * @since 1.305
     */
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, ServletException, FormException {
        configure(formData);
    }

    /**
     * This method serves static resources in the plugin under <tt>hudson/plugin/SHORTNAME</tt>.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();

        if (path.length() == 0) {
            path = "/";
        }

        if (path.indexOf("..") != -1 || path.length() < 1) {
            // don't serve anything other than files in the sub directory.
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // use serveLocalizedFile to support automatic locale selection
        rsp.serveLocalizedFile(req, new URL(wrapper.baseResourceURL, '.' + path));
    }
}

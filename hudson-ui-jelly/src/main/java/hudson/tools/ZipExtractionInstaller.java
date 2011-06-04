/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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

package hudson.tools;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Installs a tool into the Hudson working area by downloading and unpacking a ZIP file.
 * @since 1.305
 */
public class ZipExtractionInstaller extends ZipExtractionInstallerExt {

     
    @DataBoundConstructor
    public ZipExtractionInstaller(String label, String url, String subdir) {
        super(label, url, subdir);
    }

    @Extension
    public static class DescriptorImpl extends DescriptorImplExt {

        public FormValidation doCheckUrl(@QueryParameter String value) {
            try {
                URLConnection conn = ProxyConfiguration.open(new URL(value));
                conn.connect();
                if (conn instanceof HttpURLConnection) {
                    if (((HttpURLConnection) conn).getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return FormValidation.error(Messages.ZipExtractionInstaller_bad_connection());
                    }
                }
                return FormValidation.ok();
            } catch (MalformedURLException x) {
                return FormValidation.error(Messages.ZipExtractionInstaller_malformed_url());
            } catch (IOException x) {
                return FormValidation.error(x,Messages.ZipExtractionInstaller_could_not_connect());
            }
        }

    }

}

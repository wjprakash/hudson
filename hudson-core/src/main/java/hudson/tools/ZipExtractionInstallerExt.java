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
import hudson.FilePathExt;
import hudson.FilePathExt.FileCallable;
import hudson.UtilExt;
import hudson.FunctionsExt;
import hudson.model.NodeExt;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

/**
 * Installs a tool into the Hudson working area by downloading and unpacking a ZIP file.
 * @since 1.305
 */
public class ZipExtractionInstallerExt extends ToolInstaller {

    /**
     * URL of a ZIP file which should be downloaded in case the tool is missing.
     */
    private final String url;
    /**
     * Optional subdir to extract.
     */
    private final String subdir;

    public ZipExtractionInstallerExt(String label, String url, String subdir) {
        super(label);
        this.url = url;
        this.subdir = UtilExt.fixEmptyAndTrim(subdir);
    }

    public String getUrl() {
        return url;
    }

    public String getSubdir() {
        return subdir;
    }

    public FilePathExt performInstallation(ToolInstallation tool, NodeExt node, TaskListener log) throws IOException, InterruptedException {
        FilePathExt dir = preferredLocation(tool, node);
        if (dir.installIfNecessaryFrom(new URL(url), log, "Unpacking " + url + " to " + dir + " on " + node.getDisplayName())) {
            dir.act(new ChmodRecAPlusX());
        }
        if (subdir == null) {
            return dir;
        } else {
            return dir.child(subdir);
        }
    }

    @Extension
    public static class DescriptorImplExt extends ToolInstallerDescriptor<ZipExtractionInstallerExt> {

        public String getDisplayName() {
            return Messages.ZipExtractionInstaller_DescriptorImpl_displayName();
        }
    }

    /**
     * Sets execute permission on all files, since unzip etc. might not do this.
     * Hackish, is there a better way?
     */
    static class ChmodRecAPlusX implements FileCallable<Void> {

        private static final long serialVersionUID = 1L;

        public Void invoke(File d, VirtualChannel channel) throws IOException {
            if (!FunctionsExt.isWindows()) {
                process(d);
            }
            return null;
        }

        @IgnoreJRERequirement
        private void process(File f) {
            if (f.isFile()) {
                if (FunctionsExt.isMustangOrAbove()) {
                    f.setExecutable(true, false);
                } else {
                    UtilExt.chmod(f, 0755);
                }
            } else {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File kid : kids) {
                        process(kid);
                    }
                }
            }
        }
    }
}

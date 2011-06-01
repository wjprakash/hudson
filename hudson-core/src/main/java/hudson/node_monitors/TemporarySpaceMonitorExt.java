/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.node_monitors;

import hudson.Extension;
import hudson.FilePathExt;
import hudson.FilePathExt.FileCallable;
import hudson.FunctionsExt;
import hudson.model.ComputerExt;
import hudson.model.HudsonExt;
import hudson.node_monitors.DiskSpaceMonitorDescriptorExt.DiskSpace;
import hudson.remoting.VirtualChannel;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * Monitors the disk space of "/tmp".
 *
 * @author Kohsuke Kawaguchi
 */
public class TemporarySpaceMonitorExt extends AbstractDiskSpaceMonitorExt {

    public TemporarySpaceMonitorExt(String freeSpaceThreshold) throws ParseException {
        super(freeSpaceThreshold);
    }

    public TemporarySpaceMonitorExt() {
    }

    public DiskSpace getFreeSpace(ComputerExt c) {
        return DESCRIPTOR.get(c);
    }

    @Override
    public String getColumnCaption() {
        // Hide this column from non-admins
        return HudsonExt.getInstance().hasPermission(HudsonExt.ADMINISTER) ? super.getColumnCaption() : null;
    }
    public static final DiskSpaceMonitorDescriptorExt DESCRIPTOR = new DiskSpaceMonitorDescriptorExt() {

        public String getDisplayName() {
            return Messages.TemporarySpaceMonitor_DisplayName();
        }

        protected DiskSpace getFreeSpace(ComputerExt c) throws IOException, InterruptedException {
            FilePathExt p = c.getNode().getRootPath();
            if (p == null) {
                return null;
            }

            return p.act(new GetTempSpace());
        }
    };

    @Extension
    public static DiskSpaceMonitorDescriptorExt install() {
        if (FunctionsExt.isMustangOrAbove()) {
            return DESCRIPTOR;
        }
        return null;
    }

    protected static final class GetTempSpace implements FileCallable<DiskSpace> {

        @IgnoreJRERequirement
        public DiskSpace invoke(File f, VirtualChannel channel) throws IOException {
            try {
                // if the disk is really filled up we can't even create a single file,
                // so calling File.createTempFile and figuring out the directory won't reliably work.
                f = new File(System.getProperty("java.io.tmpdir"));
                long s = f.getUsableSpace();
                if (s <= 0) {
                    return null;
                }
                return new DiskSpace(s);
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }
        private static final long serialVersionUID = 1L;
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Winston Prakash
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
import hudson.StaplerUtils;
import hudson.model.ComputerExt;
import hudson.util.jna.NativeSystemMemory;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * Checks the swap space availability.
 *
 * @author Kohsuke Kawaguchi
 * @sine 1.233
 */
public class SwapSpaceMonitor extends SwapSpaceMonitorExt {

    /**
     * Returns the HTML representation of the space.
     */
    public String toHtml(NativeSystemMemory usage) {
        if (usage.getAvailableSwapSpace() == -1) {
            return "N/A";
        }

        long free = usage.getAvailableSwapSpace();
        free /= 1024L;   // convert to KB
        free /= 1024L;   // convert to MB
        if (free > 256 || usage.getTotalSwapSpace() < usage.getAvailableSwapSpace() * 5) {
            return free + "MB"; // if we have more than 256MB free or less than 80% filled up, it's OK
        }
        // Otherwise considered dangerously low.
        return StaplerUtils.wrapToErrorSpan(free + "MB");
    }
    @Extension
    public static final AbstractNodeMonitorDescriptor<NativeSystemMemory> DESCRIPTOR = new AbstractNodeMonitorDescriptor<NativeSystemMemory>() {

        @Override
        protected NativeSystemMemory monitor(ComputerExt c) throws IOException, InterruptedException {
            return c.getChannel().call(new MonitorTask());
        }

        @Override
        public String getDisplayName() {
            return Messages.SwapSpaceMonitor_DisplayName();
        }

        @Override
        public NodeMonitorExt newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new SwapSpaceMonitor();
        }
    };

    /**
     * Memory Usage.
     *
     * <p>
     * {@link MemoryUsage} + stapler annotations.
     */
    @ExportedBean
    public static class MemoryUsage extends SwapSpaceMonitorExt.MemoryUsage {

        public MemoryUsage(NativeSystemMemory mem) {
            super(mem);
        }

        /**
         * Total physical memory of the system, in bytes.
         */
        @Exported
        @Override
        public long getTotalPhysicalMemory() {
            return super.getTotalPhysicalMemory();
        }

        /**
         * Of the total physical memory of the system, available bytes.
         */
        @Exported
        @Override
        public long getAvailablePhysicalMemory() {
            return super.getAvailablePhysicalMemory();
        }

        /**
         * Total number of swap space in bytes.
         */
        @Exported
        @Override
        public long getTotalSwapSpace() {
            return super.getTotalSwapSpace();
        }

        /**
         * Available swap space in bytes.
         */
        @Exported
        @Override
        public long getAvailableSwapSpace() {
            return super.getAvailableSwapSpace();
        }
    }
}

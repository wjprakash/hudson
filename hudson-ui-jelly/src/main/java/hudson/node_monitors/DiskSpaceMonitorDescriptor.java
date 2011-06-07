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

import hudson.StaplerUtils;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;

import java.math.BigDecimal;

import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link AbstractNodeMonitorDescriptor} for {@link NodeMonitor} that checks a free disk space of some directory.
 *
 * @author Kohsuke Kawaguchi
 */
/*package*/ abstract class DiskSpaceMonitorDescriptor extends AbstractNodeMonitorDescriptor<DiskSpace> {

    /**
     * Value object that represents the disk space.
     */
    @ExportedBean
    public static final class DiskSpace extends DiskSpaceMonitorDescriptorExt.DiskSpace {


        public DiskSpace(long size) {
            super(size);
        }

        
        @Exported
        public long getSize(){
            return size;
        }

         

        /**
         * Returns the HTML representation of the space.
         */
        @Override
        public String toHtml() {
            long space = size;
            space /= 1024L;   // convert to KB
            space /= 1024L;   // convert to MB
            if (triggered) {
                // less than a GB
                return StaplerUtils.wrapToErrorSpan(new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString() + "GB");
            }

            return space / 1024 + "GB";
        }
    }
}

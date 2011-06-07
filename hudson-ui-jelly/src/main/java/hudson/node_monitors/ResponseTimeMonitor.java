/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc.
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
import hudson.model.Descriptor.FormException;
import hudson.model.ComputerExt;
import hudson.remoting.Future;
import hudson.util.TimeUnit2;
import hudson.util.IOException2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Monitors the round-trip response time to this slave.
 *
 * @author Kohsuke Kawaguchi
 */
public class ResponseTimeMonitor extends ResponseTimeMonitorExt {
    
    private static final Logger LOGGER = Logger.getLogger(ResponseTimeMonitor.class.getName());
    
    @Extension
    public static final AbstractNodeMonitorDescriptor<Data> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Data>() {
        @Override
        protected Data monitor(ComputerExt c) throws IOException, InterruptedException {
            Data old = get(c);
            Data d;

            long start = System.nanoTime();
            Future<String> f = c.getChannel().callAsync(new NoopTask());
            try {
                f.get(TIMEOUT, TimeUnit.MILLISECONDS);
                long end = System.nanoTime();
                d = new Data(old,TimeUnit2.NANOSECONDS.toMillis(end-start));
            } catch (ExecutionException e) {
                throw new IOException2(e.getCause());    // I don't think this is possible
            } catch (TimeoutException e) {
                // special constant to indicate that the processing timed out.
                d = new Data(old,-1L);
            }

            if(d.hasTooManyTimeouts() && !isIgnored()) {
                // unlike other monitors whose failure still allow us to communicate with the slave,
                // the failure in this monitor indicates that we are just unable to make any requests
                // to this slave. So we should severe the connection, as opposed to marking it temporarily
                // off line, which still keeps the underlying channel open.
                c.disconnect(d);
                LOGGER.warning(Messages.ResponseTimeMonitor_MarkedOffline(c.getName()));
            }
            return d;
        }

        @Override
        public String getDisplayName() {
            return Messages.ResponseTimeMonitor_DisplayName();
        }

        @Override
        public NodeMonitorExt newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ResponseTimeMonitor();
        }
    };

    /**
     * Immutable representation of the monitoring data.
     */
    @ExportedBean
    public static final class Data extends ResponseTimeMonitorExt.Data {
        
        private Data(Data old, long newDataPoint) {
             super(old, newDataPoint);
        }

        

        /**
         * Computes the average response time, by taking the time out into account.
         */
        @Exported
        @Override
        public long getAverage() {
           return super.getAverage();
        }

        

        /**
         * HTML rendering of the data
         */
        @Override
        public String toString() {
//            StringBuilder buf = new StringBuilder();
//            for (long l : past5) {
//                if(buf.length()>0)  buf.append(',');
//                buf.append(l);
//            }
//            return buf.toString();
            int fc = failureCount();
            if(fc>0)
                return StaplerUtils.wrapToErrorSpan(Messages.ResponseTimeMonitor_TimeOut(fc));
            return getAverage()+"ms";
        }
    }
}

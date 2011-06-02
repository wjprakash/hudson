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
import hudson.slaves.OfflineCauseExt;
import hudson.model.ComputerExt;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.util.TimeUnit2;
import hudson.util.IOException2;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Monitors the round-trip response time to this slave.
 *
 * @author Kohsuke Kawaguchi
 */
public class ResponseTimeMonitorExt extends NodeMonitorExt {
    @Extension
    public static final AbstractNodeMonitorDescriptorExt<Data> DESCRIPTOR = new AbstractNodeMonitorDescriptorExt<Data>() {
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

        public String getDisplayName() {
            return Messages.ResponseTimeMonitor_DisplayName();
        }
    };

    /**
     * Immutable representation of the monitoring data.
     */
    public static  class Data extends OfflineCauseExt {
        /**
         * Record of the past 5 times. -1 if time out. Otherwise in milliseconds.
         * Old ones first.
         */
        private final long[] past5;

        protected Data(Data old, long newDataPoint) {
            if(old==null)
                past5 = new long[] {newDataPoint};
            else {
                past5 = new long[Math.min(5,old.past5.length+1)];
                int copyLen = past5.length - 1;
                System.arraycopy(old.past5, old.past5.length-copyLen, this.past5, 0, copyLen);
                past5[past5.length-1] = newDataPoint;
            }
        }

        /**
         * Computes the recurrence of the time out
         */
        protected int failureCount() {
            int cnt=0;
            for(int i=past5.length-1; i>=0 && past5[i]<0; i--, cnt++)
                ;
            return cnt;
        }

        /**
         * Computes the average response time, by taking the time out into account.
         */
        public long getAverage() {
            long total=0;
            for (long l : past5) {
                if(l<0)     total += TIMEOUT;
                else        total += l;
            }
            return total/past5.length;
        }

        public boolean hasTooManyTimeouts() {
            return failureCount()>=5;
        }

    }

    protected static class NoopTask implements Callable<String,RuntimeException> {
        public String call() {
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Time out interval in milliseconds.
     */
    protected static final long TIMEOUT = 5000;

    private static final Logger LOGGER = Logger.getLogger(ResponseTimeMonitorExt.class.getName());
}

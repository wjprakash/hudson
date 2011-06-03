/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * Scalar value that changes over the time (such as load average, Q length, # of executors, etc.)
 *
 * <p>
 * This class computes <a href="http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average">
 * the exponential moving average</a> from the raw data (to be supplied by {@link #update(float)}).
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class TimeSeries extends TimeSeriesExt{

    public TimeSeries(float initialValue, float decay, int historySize) {
        super(initialValue, decay, historySize);
    }

     
    /**
     * Gets the history data of the exponential moving average. The returned array should be treated
     * as read-only and immutable.
     *
     * @return
     *      Always non-null, contains at least one entry.
     */
    @Exported
    @Override
    public float[] getHistory() {
        return super.getHistory();
    }

    /**
     * Gets the most up-to-date data point value. {@code getHistory[0]}.
     */
    @Exported
    @Override
    public float getLatest() {
        return super.getLatest();
    }

}

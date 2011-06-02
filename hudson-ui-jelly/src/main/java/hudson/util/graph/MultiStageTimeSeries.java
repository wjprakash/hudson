/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Orcale Corporation, Kohsuke Kawaguchi, Winston Prakash
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
package hudson.util.graph;

import hudson.model.Api;
import hudson.model.Messages;
import hudson.model.TimeSeries;
import java.awt.Color;
import java.io.IOException;

import javax.servlet.ServletException;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Maintains several {@link TimeSeries} with different update frequencies to satisfy three goals;
 * (1) retain data over long timespan, (2) save memory, and (3) retain accurate data for the recent past.
 *
 * All in all, one instance uses about 8KB space.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class MultiStageTimeSeries extends MultiStageTimeSeriesExt{

     
    public MultiStageTimeSeries(Localizable title, Color color, float initialValue, float decay) {
        super(title, color, initialValue, decay);
    }

    @Exported
    public TimeSeries getHour() {
        return hour;
    }

    @Exported
    public TimeSeries getSec10() {
        return sec10;
    }

    @Exported
    public TimeSeries getMin() {
        return min;
    }

    /**
     * @deprecated since 2009-04-05.
     *      Use {@link #MultiStageTimeSeries(Localizable, Color, float, float)}
     */
    public MultiStageTimeSeries(float initialValue, float decay) {
        this(Messages._MultiStageTimeSeries_EMPTY_STRING(), Color.WHITE, initialValue, decay);
    }
    

    public Api getApi() {
        return new Api(this);
    }

    

    /**
     * Represents the trend chart that consists of several {@link MultiStageTimeSeries}.
     *
     * <p>
     * This object is renderable as HTTP response.
     */
    public static final class TrendChart extends TrendChartExt implements HttpResponse {

        public TrendChart(TimeScale timeScale, MultiStageTimeSeries... series) {
            super(timeScale, series);
        }

        /**
         * Renders this object as an image.
         */
        @Override
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            createGraph().doPng(req, rsp); 
        }
    }

    public static TrendChart createTrendChart(TimeScale scale, MultiStageTimeSeries... data) {
        return new TrendChart(scale,data);
    }
}

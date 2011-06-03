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

import hudson.model.Messages;
import hudson.model.TimeSeriesExt;
import hudson.util.TimeUnit2;
import java.awt.Color;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jvnet.localizer.Localizable;

/**
 * Maintains several {@link TimeSeries} with different update frequencies to satisfy three goals;
 * (1) retain data over long timespan, (2) save memory, and (3) retain accurate data for the recent past.
 *
 * All in all, one instance uses about 8KB space.
 *
 * @author Kohsuke Kawaguchi
 */
public class MultiStageTimeSeriesExt {

    /**
     * Name of this data series.
     */
    public final Localizable title;
    /**
     * Used to render a line in the trend chart.
     */
    public final Color color;
    /**
     * Updated every 10 seconds. Keep data up to 1 hour.
     */
    public final TimeSeriesExt sec10;
    /**
     * Updated every 1 min. Keep data up to 1 day.
     */
    public final TimeSeriesExt min;
    /**
     * Updated every 1 hour. Keep data up to 4 weeks.
     */
    public final TimeSeriesExt hour;
    private int counter;

    public MultiStageTimeSeriesExt(Localizable title, Color color, float initialValue, float decay) {
        this.title = title;
        this.color = color;
        this.sec10 = new TimeSeriesExt(initialValue, decay, 6 * 60);
        this.min = new TimeSeriesExt(initialValue, decay, 60 * 24);
        this.hour = new TimeSeriesExt(initialValue, decay, 28 * 24);
    }

    /**
     * @deprecated since 2009-04-05.
     *      Use {@link #MultiStageTimeSeries(Localizable, Color, float, float)}
     */
    public MultiStageTimeSeriesExt(float initialValue, float decay) {
        this(Messages._MultiStageTimeSeries_EMPTY_STRING(), Color.WHITE, initialValue, decay);
    }

    /**
     * Call this method every 10 sec and supply a new data point.
     */
    public void update(float f) {
        counter = (counter + 1) % 360;   // 1hour/10sec = 60mins/10sec=3600secs/10sec = 360
        sec10.update(f);
        if (counter % 6 == 0) {
            min.update(f);
        }
        if (counter == 0) {
            hour.update(f);
        }
    }

    /**
     * Selects a {@link TimeSeries}.
     */
    public TimeSeriesExt pick(TimeScale timeScale) {
        switch (timeScale) {
            case HOUR:
                return hour;
            case MIN:
                return min;
            case SEC10:
                return sec10;
            default:
                throw new AssertionError();
        }
    }

    /**
     * Gets the most up-to-date data point value.
     */
    public float getLatest(TimeScale timeScale) {
        return pick(timeScale).getLatest();
    }

    /**
     * Choose which datapoint to use.
     */
    public enum TimeScale {

        SEC10(TimeUnit2.SECONDS.toMillis(10)),
        MIN(TimeUnit2.MINUTES.toMillis(1)),
        HOUR(TimeUnit2.HOURS.toMillis(1));
        /**
         * Number of milliseconds (10 secs, 1 min, and 1 hour)
         * that this constant represents.
         */
        public final long tick;

        TimeScale(long tick) {
            this.tick = tick;
        }

        /**
         * Creates a new {@link DateFormat} suitable for processing
         * this {@link TimeScale}.
         */
        public DateFormat createDateFormat() {
            switch (this) {
                case HOUR:
                    return new SimpleDateFormat("MMM/dd HH");
                case MIN:
                    return new SimpleDateFormat("HH:mm");
                case SEC10:
                    return new SimpleDateFormat("HH:mm:ss");
                default:
                    throw new AssertionError();
            }
        }

        /**
         * Parses the {@link TimeScale} from the query parameter.
         */
        public static TimeScale parse(String type) {
            if (type == null) {
                return TimeScale.MIN;
            }
            return Enum.valueOf(TimeScale.class, type.toUpperCase(Locale.ENGLISH));
        }
    }

    /**
     * Represents the trend chart that consists of several {@link MultiStageTimeSeries}.
     *
     * <p>
     * This object is renderable as HTTP response.
     */
    public static class TrendChartExt {

        public final TimeScale timeScale;
        public final List<MultiStageTimeSeriesExt> series;
        public final DataSet dataset;

        public TrendChartExt(TimeScale timeScale, MultiStageTimeSeriesExt... series) {
            this.timeScale = timeScale;
            this.series = new ArrayList<MultiStageTimeSeriesExt>(Arrays.asList(series));
            this.dataset = createDataset();
        }

        /**
         * Creates a {@link DefaultCategoryDataset} for rendering a graph from a set of {@link MultiStageTimeSeries}.
         */
        private DataSet createDataset() {
            float[][] dataPoints = new float[series.size()][];
            for (int i = 0; i < series.size(); i++) {
                dataPoints[i] = series.get(i).pick(timeScale).getHistory();
            }

            int dataLength = dataPoints[0].length;
            for (float[] dataPoint : dataPoints) {
                assert dataLength == dataPoint.length;
            }

            DataSet<String, String> ds = new DataSet<String, String>();

            DateFormat format = timeScale.createDateFormat();

            Date date = new Date(System.currentTimeMillis() - timeScale.tick * dataLength);
            for (int i = dataLength - 1; i >= 0; i--) {
                date = new Date(date.getTime() + timeScale.tick);
                String timeStr = format.format(date);
                for (int j = 0; j < dataPoints.length; j++) {
                    ds.add(dataPoints[j][i], series.get(j).title.toString(), timeStr);
                }
            }
            return ds;
        }

        public Graph createGraph() {
            Graph graph = new Graph(-1, 500, 400);
            graph.setXAxisLabel("");
            graph.setData(createDataset());
            graph.setChartType(Graph.TYPE_LINE);
            graph.setMultiStageTimeSeries(series);
            return graph;
        }
    }

    public static TrendChartExt createTrendChart(TimeScale scale, MultiStageTimeSeriesExt... data) {
        return new TrendChartExt(scale, data);
    }
}

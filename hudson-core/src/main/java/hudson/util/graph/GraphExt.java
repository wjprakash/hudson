/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc. Kohsuke Kawaguchi,  Winston.Prakash@Oracle.com
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

import hudson.util.ColorPalette;
import java.awt.Font;
import java.awt.Graphics2D;

import java.util.Calendar;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A JFreeChart-generated graph that's bound to UI.
 *
 * <p>
 * This object exposes two URLs:
 * <dl>
 * <dt>/png
 * <dd>PNG image of a graph
 *
 * <dt>/map
 * <dd>Clickable map
 * </dl>
 * 
 * @since 1.320
 */
public class GraphExt {
    
    protected static int TYPE_STACKED_AREA = 1;
    protected static int TYPE_LINE = 2;
    protected final long timestamp;
    protected int width;
    protected int height;
    protected volatile GraphSupport graphSupport;


    /**
     * @param timestamp
     *      Timestamp of this graph. Used for HTTP cache related headers.
     *      If the graph doesn't have any timestamp to tie it to, pass -1.
     */
    public GraphExt(long timestamp, int defaultW, int defaultH) {
        this.timestamp = timestamp;
        this.width = defaultW;
        this.height = defaultH;
    }
    
     public GraphSupport getGraphSupport() {
        return graphSupport;
    }

    public void setGraphSupport(GraphSupport graphSupport) {
        this.graphSupport = graphSupport;
    }

    public GraphExt(Calendar timestamp, int defaultW, int defaultH) {
        this(timestamp.getTimeInMillis(), defaultW, defaultH);
    }
    
    public void setChartType(int chartType) {
        if (graphSupport != null) {
            graphSupport.setChartType(chartType);
        }
    }
    
    public void setMultiStageTimeSeries(List<MultiStageTimeSeriesExt> multiStageTimeSeries) {
        if (graphSupport != null) {
            graphSupport.setMultiStageTimeSeries(multiStageTimeSeries);
        }
    }

    public void setData(DataSet data) {
        if (graphSupport != null) {
            graphSupport.setData(data);
        }
    }

    public void setTitle(String title) {
        if (graphSupport != null) {
            graphSupport.setTitle(title);
        }
    }

    public void setXAxisLabel(String xLabel) {
        if (graphSupport != null) {
            graphSupport.setXAxisLabel(xLabel);
        }
    }

    public void setYAxisLabel(String yLabel) {
        if (graphSupport != null) {
            graphSupport.setYAxisLabel(yLabel);
        }
    }

    public BufferedImage createImage(int width, int height) {
        BufferedImage image = null;
        if (graphSupport != null) {
            image = graphSupport.render(width, height);
        } else {
            image = createErrorImage(width, height);
        }
        return image;
    }

     
    private BufferedImage createErrorImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = img.createGraphics();
        graphics.setColor(ColorPalette.RED);
        Font font = new Font("Serif", Font.BOLD, 14);
        graphics.drawRect(2, 2, width - 4, height - 4);
        graphics.drawString("Graph Support missing. \n Install Graph Support Plugin", 10, height / 2);
        return img;
    }
}

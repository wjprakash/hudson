/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.util.RunListExt;

import java.util.Date;

/**
 * UI widget for showing the SMILE timeline control.
 *
 * <p>
 * Return this from your "getTimeline" method.
 *
 * @author Kohsuke Kawaguchi, Winston Prakash
 * @since 1.372
 */
public class BuildTimelineWidgetExt {

     
    protected final RunListExt<?> builds;

    public BuildTimelineWidgetExt(RunListExt<?> builds) {
        this.builds = builds;
    }

    public RunExt<?, ?> getFirstBuild() {
        return builds.getFirstBuild();
    }

    public RunExt<?, ?> getLastBuild() {
        return builds.getLastBuild();
    }

    /**
     * Event data to be rendered on timeline.
     * See http://code.google.com/p/simile-widgets/wiki/Timeline_EventSources
    
     * <p>
     * This is bound to JSON and sent to the client-side JavaScript.
     */
    private static class Event {

        public Date start;
        public Date end;
        public String title, description;
        /**
         * If true, the event occurs over a time duration. No icon. The event will be
         * drawn as a dark blue tape. The tape color is set with the color attribute.
         * Default color is #58A0DC
         *
         * If false (default), the event is focused on a specific "instant" (shown with the icon).
         * The event will be drawn as a blue dot icon (default) with a pale blue tape.
         * The tape is the default color (or color attribute color), with opacity
         * set to 20. To change the opacity, change the theme's instant: {impreciseOpacity: 20}
         * value. Maximum 100.
         */
        public Boolean durationEvent;
        /**
         * Url. The bubble's title text be a hyper-link to this address.
         */
        public String link;
        /**
         * Color of the text and tape (duration events) to display in the timeline.
         * If the event has durationEvent = false, then the bar's opacity will
         * be applied (default 20%). See durationEvent, above.
         */
        public String color;
        /**
         * CSS class name.
         */
        public String classname;
    }
}

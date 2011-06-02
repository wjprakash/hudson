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
package hudson.slaves;

import hudson.model.ComputerExt;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * Represents a cause that puts a {@linkplain ComputerExt#isOffline() computer offline}.
 *
 * <h2>Views</h2>
 * <p>
 * {@link OfflineCause} must have <tt>cause.jelly</tt> that renders a cause
 * into HTML. This is used to tell users why the node is put offline.
 * This view should render a block element like DIV. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.320
 */
@ExportedBean
public abstract class OfflineCause extends OfflineCauseExt {

    /**
     * {@link OfflineCause} that renders a static text,
     * but without any further UI.
     */
    public static class SimpleOfflineCause extends OfflineCauseExt.SimpleOfflineCause {

        private SimpleOfflineCause(Localizable description) {
            super(description);
        }

        @Exported(name = "description")
        @Override
        public String toString() {
            return super.toString();
        }
    }

    public static OfflineCauseExt create(Localizable d) {
        if (d == null) {
            return null;
        }
        return new SimpleOfflineCause(d);
    }

    /**
     * Caused by unexpected channel termination.
     */
    public static class ChannelTermination extends OfflineCauseExt.ChannelTermination {

        public ChannelTermination(Exception cause) {
            super(cause);
        }

        @Exported
        public Exception getCause() {
            return cause;
        }
    }

    public static class ByCLI extends OfflineCauseExt.ByCLI {

        public ByCLI(String message) {
            super(message);
        }

        @Exported
        public String getMessage() {
            return message;
        }
    }
}

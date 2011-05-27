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

import hudson.util.ByteBuffer;
import hudson.util.CharSpool;
import hudson.util.LineEndNormalizingWriter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;

/**
 * Represents a large text data.
 *
 * <p>
 * This class defines methods for handling progressive text update.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated moved to stapler, as of Hudson 1.220
 */
public class LargeText extends LargeTextExt {

    public LargeText(final File file, boolean completed) {
        super(file, completed);
    }

    public LargeText(final ByteBuffer memory, boolean completed) {
        super(memory, completed);
    }

    /**
     * Implements the progressive text handling.
     * This method is used as a "web method" with progressiveText.jelly.
     */
    public void doProgressText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setStatus(HttpServletResponse.SC_OK);

        if (!source.exists()) {
            // file doesn't exist yet
            rsp.addHeader("X-Text-Size", "0");
            rsp.addHeader("X-More-Data", "true");
            return;
        }

        long start = 0;
        String s = req.getParameter("start");
        if (s != null) {
            start = Long.parseLong(s);
        }

        if (source.length() < start) {
            start = 0;  // text rolled over
        }
        CharSpool spool = new CharSpool();
        long r = writeLogTo(start, spool);

        rsp.addHeader("X-Text-Size", String.valueOf(r));
        if (!completed) {
            rsp.addHeader("X-More-Data", "true");
        }

        // when sending big text, try compression. don't bother if it's small
        Writer w;
        if (r - start > 4096) {
            w = rsp.getCompressedWriter(req);
        } else {
            w = rsp.getWriter();
        }
        spool.writeTo(new LineEndNormalizingWriter(w));
        w.close();

    }
}

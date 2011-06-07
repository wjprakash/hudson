/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Winston Prakash
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
package hudson;

import hudson.model.Hudson;
import org.kohsuke.stapler.Stapler;

/**
 * Various utility methods that don't have more proper home.
 *
 * @author Kohsuke Kawaguchi
 */
public class Util extends UtilExt {

    /**
     * Wraps with the error icon and the CSS class to render error message.
     * @since 1.173
     */
    public static String wrapToErrorSpan(String s) {
        s = "<span class=error><img src='"
                + Stapler.getCurrentRequest().getContextPath() + Hudson.RESOURCE_PATH
                + "/images/none.gif' height=16 width=1>" + s + "</span>";
        return s;
    }
}

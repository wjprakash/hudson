/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.console;

import hudson.Extension;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Entry point to the {@link ConsoleAnnotator} extension point. This class creates a new instance
 * of {@link ConsoleAnnotator} that starts a new console annotation session.
 *
 * <p>
 * {@link ConsoleAnnotatorFactory}s are used whenever a browser requests console output (as opposed to when
 * the console output is being produced &mdash; for that see {@link ConsoleNote}.)
 *
 * <p>
 * {@link ConsoleAnnotator}s returned by {@link ConsoleAnnotatorFactory} are asked to start from
 * an arbitrary line of the output, because typically browsers do not request the entire console output.
 * Because of this, {@link ConsoleAnnotatorFactory} is generally suitable for peep-hole local annotation
 * that only requires a small contextual information, such as keyword coloring, URL hyperlinking, and so on.
 *
 * <p>
 * To register, put @{@link Extension} on your {@link ConsoleAnnotatorFactory} subtype.
 *
 * <h2>Behaviour, JavaScript, and CSS</h2>
 * <p>
 * {@link ConsoleNote} can have associated <tt>script.js</tt> and <tt>style.css</tt> (put them
 * in the same resource directory that you normally put Jelly scripts), which will be loaded into
 * the HTML page whenever the console notes are used. This allows you to use minimal markup in
 * code generation, and do the styling in CSS and perform the rest of the interesting work as a CSS behaviour/JavaScript.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public abstract class ConsoleAnnotatorFactory  extends  ConsoleAnnotatorFactoryExt{
    

    /**
     * Serves the JavaScript file associated with this console annotator factory.
     */
    @WebMethod(name="script.js")
    public void doScriptJs(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveFile(req, getResource("/script.js"), TimeUnit2.DAYS.toMillis(1));
    }

    @WebMethod(name="style.css")
    public void doStyleCss(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.serveFile(req, getResource("/style.css"), TimeUnit2.DAYS.toMillis(1));
    }
}

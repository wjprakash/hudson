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
package hudson.tasks._ant;

import hudson.MarkupText;
import hudson.console.ConsoleNote;
import hudson.console.ConsoleAnnotator;

import java.util.regex.Pattern;

/**
 * Marks the log line "TARGET:" that Ant uses to mark the beginning of the new target.
 * @sine 1.349
 */
public class AntTargetNoteExt extends ConsoleNote {
    public AntTargetNoteExt() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        // still under development. too early to put into production
        if (!ENABLED)   return null;

        MarkupText.SubText t = text.findToken(Pattern.compile(".*(?=:)"));
        if (t!=null)
            t.addMarkup(0,t.length(),"<b class=ant-target>","</b>");
        return null;
    }

    public static boolean ENABLED = !Boolean.getBoolean(AntTargetNoteExt.class.getName()+".disabled");
}

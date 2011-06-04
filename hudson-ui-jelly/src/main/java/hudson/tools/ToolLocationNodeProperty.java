/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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
package hudson.tools;

import hudson.slaves.NodeProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * {@link NodeProperty} that allows users to specify different locations for {@link ToolInstallation}s.
 *
 * @since 1.286
 */
public class ToolLocationNodeProperty extends ToolLocationNodePropertyExt {

    @DataBoundConstructor
    public ToolLocationNodeProperty(List<ToolLocationExt> locations) {
        super(locations);
    }

    public ToolLocationNodeProperty(ToolLocationExt... locations) {
        this(Arrays.asList(locations));
    }

    public static final class ToolLocation extends ToolLocationExt {

        public ToolLocation(ToolDescriptorExt type, String name, String home) {
            super(type, name, home);
        }

        @DataBoundConstructor
        public ToolLocation(String key, String home) {
            super(key, home);
        }
    }
}

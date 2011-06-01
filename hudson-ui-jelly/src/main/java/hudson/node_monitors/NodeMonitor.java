/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Thomas J. Black
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
package hudson.node_monitors;

import hudson.model.ComputerSetExt;
import hudson.model.NodeExt;


import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extension point for managing and monitoring {@link NodeExt}s.
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>column.jelly</dt>
 * <dd>
 * Invoked from {@link ComputerSetExt} <tt>index.jelly</tt> to render a column.
 * The {@link NodeMonitor} instance is accessible through the "from" variable.
 * Also see {@link #getColumnCaption()}.
 *
 * <dt>config.jelly (optional)</dt>
 * <dd>
 * Configuration fragment to be displayed in {@code http://server/hudson/computer/configure}.
 * Used for configuring the threshold for taking nodes offline. 
 * </dl>
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link NodeMonitor}s are persisted via XStream.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
@ExportedBean
public abstract class NodeMonitor extends NodeMonitorExt {
     

    /**
     * Returns the name of the column to be added to {@link ComputerSetExt} index.jelly.
     *
     * @return
     *      null to not render a column. The convention is to use capitalization like "Foo Bar Zot".
     */
    @Exported
    @Override
    public String getColumnCaption() {
        return super.getColumnCaption();
    }
}

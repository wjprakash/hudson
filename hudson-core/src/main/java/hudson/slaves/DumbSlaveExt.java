/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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

import hudson.model.SlaveExt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Default {@link Slave} implementation for computers that do not belong to a higher level structure,
 * like grid or cloud.
 *
 * @author Kohsuke Kawaguchi
 */
public final class DumbSlaveExt extends SlaveExt {
    /**
     * @deprecated as of 1.286.
     *      Use {@link #DumbSlave(String, String, String, String, Mode, String, ComputerLauncher, RetentionStrategy, List)}
     */
    public DumbSlaveExt(String name, String nodeDescription, String remoteFS, String numExecutors, ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategyExt retentionStrategy) throws IOException {
        this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }
    
    public DumbSlaveExt(String name, String nodeDescription, String remoteFS, String numExecutors, ModeExt mode, String labelString, ComputerLauncher launcher, RetentionStrategyExt retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException {
    	super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    @Override
    public NodeDescriptorExt getDescriptor() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}

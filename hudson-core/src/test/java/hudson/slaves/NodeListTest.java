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
package hudson.slaves;


import junit.framework.TestCase;
import hudson.model.HudsonExt;
import hudson.model.NodeExt;
import hudson.model.TaskListener;
import hudson.model.ComputerExt;
import hudson.model.TopLevelItem;
import hudson.XmlFile;
import hudson.Launcher;
import hudson.FilePathExt;
import hudson.model.labels.LabelAtomExt;
import hudson.util.ClockDifferenceExt;
import hudson.util.DescribableListExt;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeListTest extends TestCase {
    static class DummyNode extends NodeExt {
        public String getNodeName() {
            throw new UnsupportedOperationException();
        }

        public void setNodeName(String name) {
            throw new UnsupportedOperationException();
        }

        public String getNodeDescription() {
            throw new UnsupportedOperationException();
        }

        public Launcher createLauncher(TaskListener listener) {
            throw new UnsupportedOperationException();
        }

        public int getNumExecutors() {
            throw new UnsupportedOperationException();
        }

        public ModeExt getMode() {
            throw new UnsupportedOperationException();
        }

        public ComputerExt createComputer() {
            throw new UnsupportedOperationException();
        }

        public Set<LabelAtomExt> getAssignedLabels() {
            throw new UnsupportedOperationException();
        }

        public String getLabelString() {
            throw new UnsupportedOperationException();
        }

        public FilePathExt getWorkspaceFor(TopLevelItem item) {
            throw new UnsupportedOperationException();
        }

        public FilePathExt getRootPath() {
            throw new UnsupportedOperationException();
        }

        public ClockDifferenceExt getClockDifference() throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        public NodeDescriptorExt getDescriptor() {
            throw new UnsupportedOperationException();
        }

		@Override
		public DescribableListExt<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
            throw new UnsupportedOperationException();
		}
    }
    static class EphemeralNode extends DummyNode implements hudson.slaves.EphemeralNode {
        public NodeExt asNode() {
            return this;
        }
    }

    public void testSerialization() throws Exception {
        NodeList nl = new NodeList();
        nl.add(new DummyNode());
        nl.add(new EphemeralNode());

        File tmp = File.createTempFile("test","test");
        try {
            XmlFile x = new XmlFile(HudsonExt.XSTREAM, tmp);
            x.write(nl);

            String xml = FileUtils.readFileToString(tmp);
            System.out.println(xml);
            assertEquals(4,xml.split("\n").length);

            NodeList back = (NodeList)x.read();

            assertEquals(1,back.size());
            assertEquals(DummyNode.class,back.get(0).getClass());
        } finally {
            tmp.delete();
        }
    }
}

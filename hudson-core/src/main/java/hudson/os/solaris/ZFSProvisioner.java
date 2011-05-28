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
package hudson.os.solaris;

import hudson.FileSystemProvisioner;
import hudson.FilePathExt;
import hudson.WorkspaceSnapshot;
import hudson.FileSystemProvisionerDescriptor;
import hudson.Extension;
import hudson.remoting.VirtualChannel;
import hudson.FilePathExt.FileCallable;
import hudson.model.AbstractBuildExt;
import hudson.model.TaskListener;
import hudson.model.AbstractProjectExt;
import hudson.model.NodeExt;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeUtils;
import hudson.util.jna.NativeZfsFileSystem;

import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FileSystemProvisioner} for ZFS.
 *
 * @author Kohsuke Kawaguchi
 */
public class ZFSProvisioner extends FileSystemProvisioner implements Serializable {
    
    private NativeUtils nativeUtils = NativeUtils.getInstance();
    private final NodeExt node;
    private final String rootDataset;

    public ZFSProvisioner(NodeExt node) throws IOException, InterruptedException {
        this.node = node;
        rootDataset = node.getRootPath().act(new FileCallable<String>() {
            public String invoke(File f, VirtualChannel channel) throws IOException {
                try {
                    NativeZfsFileSystem fs = nativeUtils.getZfsByMountPoint(f);
                    if(fs != null)    return fs.getName();
                } catch (NativeAccessException ex) {
                    Logger.getLogger(ZFSProvisioner.class.getName()).log(Level.SEVERE, null, ex);
                }
                 
                // TODO: for now, only support slaves that are already on ZFS.
                throw new IOException("Not on ZFS");
            }
        });
    }

    public void prepareWorkspace(AbstractBuildExt<?,?> build, FilePathExt ws, final TaskListener listener) throws IOException, InterruptedException {
        final String name = build.getProject().getFullName();
        
        ws.act(new FileCallable<Void>() {

            public Void invoke(File f, VirtualChannel channel) throws IOException {
                try {
                    NativeZfsFileSystem fs = nativeUtils.getZfsByMountPoint(f);
                    if (fs != null) {
                        return null;    // already on ZFS
                    }
                    // nope. create a file system
                    String fullName = rootDataset + '/' + name;
                    listener.getLogger().println("Creating a ZFS file system " + fullName + " at " + f);
                    fs = nativeUtils.createZfs(fullName);
                    fs.setMountPoint(f);
                    fs.mount();
                } catch (NativeAccessException ex) {
                    Logger.getLogger(ZFSProvisioner.class.getName()).log(Level.SEVERE, null, ex);
                }

                return null;
            }
        });
    }

    public void discardWorkspace(AbstractProjectExt<?, ?> project, FilePathExt ws) throws IOException, InterruptedException {
        ws.act(new FileCallable<Void>() {
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                try {
                    NativeZfsFileSystem fs = nativeUtils.getZfsByMountPoint(f);
                    if(fs != null){
                       fs.destory(true);
                    }
                } catch (NativeAccessException ex) {
                    Logger.getLogger(ZFSProvisioner.class.getName()).log(Level.SEVERE, null, ex);
                }
                 
                return null;
            }
        });
    }

    /**
     * @deprecated as of 1.350
     */
    public WorkspaceSnapshot snapshot(AbstractBuildExt<?, ?> build, FilePathExt ws, TaskListener listener) throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    public WorkspaceSnapshot snapshot(AbstractBuildExt<?, ?> build, FilePathExt ws, String glob, TaskListener listener) throws IOException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Extension
    public static final class DescriptorImpl extends FileSystemProvisionerDescriptor {
        public boolean discard(FilePathExt ws, TaskListener listener) throws IOException, InterruptedException {
            // TODO
            return false;
        }

        public String getDisplayName() {
            return "ZFS";
        }
    }

    private static final long serialVersionUID = 1L;
}

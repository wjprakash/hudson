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

import hudson.model.ComputerExt;
import hudson.model.HudsonExt;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePathExt;
import hudson.remoting.Channel;

import java.io.IOException;

/**
 * Receives notifications about status changes of {@link ComputerExt}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.246
 */
public abstract class ComputerListener implements ExtensionPoint {
    /**
     * Called before a {@link ComputerExt} is marked online.
     *
     * <p>
     * This enables you to do some work on all the slaves
     * as they get connected. Unlike {@link #onOnline(ComputerExt, TaskListener)},
     * a failure to carry out this function normally will prevent
     * a computer from marked as online.
     *
     * @param channel
     *      This is the channel object to talk to the slave.
     *      (This is the same object returned by {@link ComputerExt#getChannel()} once
     *      it's connected.
     * @param root
     *      The directory where this slave stores files.
     *      The same as {@link Node#getRootPath()}, except that method returns
     *      null until the slave is connected. So this parameter is passed explicitly instead.
     * @param listener
     *      This is connected to the launch log of the computer.
     *      Since this method is called synchronously from the thread
     *      that launches a computer, if this method performs a time-consuming
     *      operation, this listener should be notified of the progress.
     *      This is also a good listener for reporting problems.
     *
     * @throws IOException
     *      Exceptions will be recorded to the listener, and
     *      the computer will not become online.
     * @throws InterruptedException
     *      Exceptions will be recorded to the listener, and
     *      the computer will not become online.
     *
     * @since 1.295
     * @see #onOnline(ComputerExt, TaskListener)
     */
    public void preOnline(ComputerExt c, Channel channel, FilePathExt root, TaskListener listener) throws IOException, InterruptedException {
    }

    /**
     * Called right after a {@link ComputerExt} comes online.
     *
     * @deprecated as of 1.292
     *      Use {@link #onOnline(ComputerExt, TaskListener)}
     */
    public void onOnline(ComputerExt c) {}

    /**
     * Called right after a {@link ComputerExt} comes online.
     *
     * <p>
     * This enables you to do some work on all the slaves
     * as they get connected.
     *
     * <p>
     * Starting HudsonExt 1.312, this method is also invoked for the master, not just for slaves.
     *
     * @param listener
     *      This is connected to the launch log of the computer.
     *      Since this method is called synchronously from the thread
     *      that launches a computer, if this method performs a time-consuming
     *      operation, this listener should be notified of the progress.
     *      This is also a good listener for reporting problems.
     *
     * @throws IOException
     *      Exceptions will be recorded to the listener. Note that
     *      throwing an exception doesn't put the computer offline.
     * @throws InterruptedException
     *      Exceptions will be recorded to the listener. Note that
     *      throwing an exception doesn't put the computer offline.
     *
     * @see #preOnline(ComputerExt, Channel, FilePathExt, TaskListener)
     */
    public void onOnline(ComputerExt c, TaskListener listener) throws IOException, InterruptedException {
        // compatibility
        onOnline(c);
    }

    /**
     * Called right after a {@link ComputerExt} went offline.
     */
    public void onOffline(ComputerExt c) {}

    /**
     * Called when configuration of the node was changed, a node is added/removed, etc.
     * @since 1.377
     */
    public void onConfigurationChange() {}

    /**
     * Registers this {@link ComputerListener} so that it will start receiving events.
     *
     * @deprecated as of 1.286
     *      put {@link Extension} on your class to have it auto-registered.
     */
    public final void register() {
        all().add(this);
    }

    /**
     * Unregisters this {@link ComputerListener} so that it will never receive further events.
     *
     * <p>
     * Unless {@link ComputerListener} is unregistered, it will never be a subject of GC.
     */
    public final boolean unregister() {
        return all().remove(this);
    }

    /**
     * All the registered {@link ComputerListener}s.
     */
    public static ExtensionList<ComputerListener> all() {
        return HudsonExt.getInstance().getExtensionList(ComputerListener.class);
    }
}

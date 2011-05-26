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
package hudson.slaves;

import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.FilePathExt;
import hudson.model.ComputerExt;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import hudson.util.ClasspathBuilder;
import hudson.util.JVMBuilder;
import hudson.util.StreamCopyThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Various convenient subtype of {@link Channel}s.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Channels {
    /**
     * @deprecated since 2009-04-13.
     *      Use {@link #forProcess(String, ExecutorService, InputStream, OutputStream, OutputStream, Proc)}
     */
    public static Channel forProcess(String name, ExecutorService execService, InputStream in, OutputStream out, Proc proc) throws IOException {
        return forProcess(name,execService,in,out,null,proc);
    }

    /**
     * Creates a channel that wraps a remote process, so that when we shut down the connection
     * we kill the process.
     */
    public static Channel forProcess(String name, ExecutorService execService, InputStream in, OutputStream out, OutputStream header, final Proc proc) throws IOException {
        return new Channel(name, execService, in, out, header) {
            /**
             * Kill the process when the channel is severed.
             */
            @Override
            protected synchronized void terminate(IOException e) {
                super.terminate(e);
                try {
                    proc.kill();
                } catch (IOException x) {
                    // we are already in the error recovery mode, so just record it and move on
                    LOGGER.log(Level.INFO, "Failed to terminate the severed connection",x);
                } catch (InterruptedException x) {
                    // process the interrupt later
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public synchronized void close() throws IOException {
                super.close();
                // wait for the child process to complete
                try {
                    proc.join();
                } catch (InterruptedException e) {
                    // process the interrupt later
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    public static Channel forProcess(String name, ExecutorService execService, final Process proc, OutputStream header) throws IOException {
        final Thread thread = new StreamCopyThread(name + " stderr", proc.getErrorStream(), header);
        thread.start();

        return new Channel(name, execService, proc.getInputStream(), proc.getOutputStream(), header) {
            /**
             * Kill the process when the channel is severed.
             */
            @Override
            protected synchronized void terminate(IOException e) {
                super.terminate(e);
                proc.destroy();
                // the stderr copier should exit by itself
            }

            @Override
            public synchronized void close() throws IOException {
                super.close();
                // wait for Maven to complete
                try {
                    proc.waitFor();
                    thread.join();
                } catch (InterruptedException e) {
                    // process the interrupt later
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    /**
     * Launches a new JVM with the given classpath and system properties, establish a communication channel,
     * and return a {@link Channel} to it.
     *
     * @param displayName
     *      Human readable name of what this JVM represents. For example "Selenium grid" or "Hadoop".
     *      This token is used for messages to {@code listener}.
     * @param listener
     *      The progress of the launcher and the failure information will be sent here. Must not be null.
     * @param workDir
     *      If non-null, the new JVM will have this directory as the working directory. This must be a local path.
     * @param classpath
     *      The classpath of the new JVM. Can be null if you just need {@code slave.jar} (and everything else
     *      can be sent over the channel.) But if you have jars that are known to be necessary by the new JVM,
     *      setting it here will improve the classloading performance (by avoiding remote class file transfer.)
     *      Classes in this classpath will also take precedence over any other classes that's sent via the channel
     *      later, so it's also useful for making sure you get the version of the classes you want.
     * @param systemProperties
     *      If the new JVM should have a certain system properties set. Can be null.
     *
     * @return
     *      never null
     * @since 1.300
     */
    public static Channel newJVM(String displayName, TaskListener listener, FilePathExt workDir, ClasspathBuilder classpath, Map<String,String> systemProperties) throws IOException {
        JVMBuilder vmb = new JVMBuilder();
        vmb.systemProperties(systemProperties);

        return newJVM(displayName,listener,vmb,workDir,classpath);
    }

    /**
     * Launches a new JVM with the given classpath, establish a communication channel,
     * and return a {@link Channel} to it.
     *
     * @param displayName
     *      Human readable name of what this JVM represents. For example "Selenium grid" or "Hadoop".
     *      This token is used for messages to {@code listener}.
     * @param listener
     *      The progress of the launcher and the failure information will be sent here. Must not be null.
     * @param workDir
     *      If non-null, the new JVM will have this directory as the working directory. This must be a local path.
     * @param classpath
     *      The classpath of the new JVM. Can be null if you just need {@code slave.jar} (and everything else
     *      can be sent over the channel.) But if you have jars that are known to be necessary by the new JVM,
     *      setting it here will improve the classloading performance (by avoiding remote class file transfer.)
     *      Classes in this classpath will also take precedence over any other classes that's sent via the channel
     *      later, so it's also useful for making sure you get the version of the classes you want.
     * @param vmb
     *      A partially configured {@link JVMBuilder} that allows the caller to fine-tune the launch parameter.
     *
     * @return
     *      never null
     * @since 1.361
     */
    public static Channel newJVM(String displayName, TaskListener listener, JVMBuilder vmb, FilePathExt workDir, ClasspathBuilder classpath) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("localhost",0));
        serverSocket.setSoTimeout(10*1000);

        // use -cp + FQCN instead of -jar since remoting.jar can be rebundled (like in the case of the swarm plugin.)
        vmb.classpath().addJarOf(Channel.class);
        vmb.mainClass(Launcher.class);

        if(classpath!=null)
            vmb.args().add("-cp").add(classpath);
        vmb.args().add("-connectTo","localhost:"+serverSocket.getLocalPort());

        listener.getLogger().println("Starting "+displayName);
        Proc p = vmb.launch(new LocalLauncher(listener)).stdout(listener).pwd(workDir).start();

        Socket s = serverSocket.accept();
        serverSocket.close();

        return forProcess("Channel to "+displayName, ComputerExt.threadPoolForRemoting,
                new BufferedInputStream(new SocketInputStream(s)),
                new BufferedOutputStream(new SocketOutputStream(s)),null,p);
    }


    private static final Logger LOGGER = Logger.getLogger(Channels.class.getName());
}

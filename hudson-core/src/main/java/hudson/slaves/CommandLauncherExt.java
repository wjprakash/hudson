/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import hudson.EnvVars;
import hudson.UtilExt;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.util.StreamCopyThread;
import hudson.util.ProcessTree;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * {@link ComputerLauncher} through a remote login mechanism like ssh/rsh.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class CommandLauncherExt extends ComputerLauncher {

    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private final String agentCommand;
    
    /**
     * Optional environment variables to add to the current environment. Can be null.
     */
    protected final EnvVars env;

    public CommandLauncherExt(String command) {
        this(command, null);
    }
    
    public CommandLauncherExt(String command, EnvVars env) {
    	this.agentCommand = command;
    	this.env = env;
    }

    public String getCommand() {
        return agentCommand;
    }

    /**
     * Gets the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }
    
    @Override
    public void launch(SlaveComputerExt computer, final TaskListener listener){
        performLaunch(computer, listener, null);
    }

    public void performLaunch(SlaveComputerExt computer, final TaskListener listener, EnvVars envLoc) {
        EnvVars _cookie = null;
        Process _proc = null;
        try {
            listener.getLogger().println(hudson.model.Messages.Slave_Launching(getTimestamp()));
            if(getCommand().trim().length()==0) {
                listener.getLogger().println(Messages.CommandLauncher_NoLaunchCommand());
                return;
            }
            listener.getLogger().println("$ " + getCommand());

            ProcessBuilder pb = new ProcessBuilder(UtilExt.tokenize(getCommand()));
            final EnvVars cookie = _cookie = EnvVars.createCookie();
            pb.environment().putAll(cookie);

            if (envLoc != null) {
            	pb.environment().putAll(envLoc);
            }
            
            if (env != null) {
            	pb.environment().putAll(env);
            }
            
            final Process proc = _proc = pb.start();

            // capture error information from stderr. this will terminate itself
            // when the process is killed.
            new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                    proc.getErrorStream(), listener.getLogger()).start();

            computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    try {
                        int exitCode = proc.exitValue();
                        if (exitCode!=0) {
                            listener.error("Process terminated with exit code "+exitCode);
                        }
                    } catch (IllegalThreadStateException e) {
                        // hasn't terminated yet
                    }

                    try {
                        ProcessTree.get().killAll(proc, cookie);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.INFO, "interrupted", e);
                    }
                }
            });

            LOGGER.info("slave agent launched for " + computer.getDisplayName());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_abortedLaunch()));
        } catch (RuntimeException e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
        } catch (Error e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
        } catch (IOException e) {
            UtilExt.displayIOException(e, listener);

            String msg = UtilExt.getWin32ErrorMessage(e);
            if (msg == null) {
                msg = "";
            } else {
                msg = " : " + msg;
            }
            msg = hudson.model.Messages.Slave_UnableToLaunch(computer.getDisplayName(), msg);
            LOGGER.log(Level.SEVERE, msg, e);
            e.printStackTrace(listener.error(msg));

            if(_proc!=null)
                try {
                    ProcessTree.get().killAll(_proc, _cookie);
                } catch (InterruptedException x) {
                    x.printStackTrace(listener.error(Messages.ComputerLauncher_abortedLaunch()));
                }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CommandLauncherExt.class.getName());

}

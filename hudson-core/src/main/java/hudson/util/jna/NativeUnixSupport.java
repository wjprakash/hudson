/*
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@oracle.com
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
package hudson.util.jna;

import hudson.DescriptorExtensionListExt;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;
import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Extension point for adding Native Unix Support to HudsonExt
 *
 * <p>
 * This object can have an optional <tt>config.jelly</tt> to configure the Native Access Support
 * <p>
 * A default constructor is needed to create NativeAccessSupport in the default configuration.
 *
 * @author Winston Prakash
 * @since 2.0.1
 * @see NativeAccessSupportDescriptor
 */
public abstract class NativeUnixSupport extends AbstractDescribableImpl<NativeUnixSupport> implements ExtensionPoint {

    /**
     * Returns all the registered {@link NativeAccessSupport} descriptors.
     */
    public static DescriptorExtensionListExt<NativeUnixSupport, DescriptorExt<NativeUnixSupport>> all() {
        return HudsonExt.getInstance().<NativeUnixSupport, DescriptorExt<NativeUnixSupport>>getDescriptorList(NativeUnixSupport.class);
    }

    @Override
    public NativeUnixSupportDescriptor getDescriptor() {
        return (NativeUnixSupportDescriptor) super.getDescriptor();
    }

    /**
     * Check if this Extension has Support for specific native Operation
     * @param nativeFunc Native Operation
     * @return true if supported
     */
    abstract public boolean hasSupportFor(NativeFunction nativeFunc);

    /**
     * Get the error associated with the last Operation
     * @return String error message
     */
    abstract public String getLastError();

    /**
     * Do the Unix style chmod (change file permission) on a File
     * @param file
     * @param mask
     * @return true if the operation is successful
     */
    abstract public boolean chmod(File file, int mask) throws NativeAccessException;

    /**
     * Make the file writable with native operation 
     * @param file
     * @return true if the operation is successful
     */
    abstract public boolean makeFileWritable(File file) throws NativeAccessException;

    /**
     * Create unix style symlink 
     * @param targetPath
     * @param file
     * @return true if the operation is successful
     */
    abstract public boolean createSymlink(String targetPath, File file) throws NativeAccessException;

    /**
     * Get the unix style mode (file permission) of a file
     * @param file
     * @return true if the operation is successful
     */
    abstract public int mode(File file);

    /**
     * Resolves symlink, if the given file is a symlink on a Unix System. Otherwise return null.
     * @param targetPath
     * @param file
     * @return
     */
    abstract public String resolveSymlink(File file) throws NativeAccessException;

    /**
     * Get the information about the System Memory
     * @return SystemMemory
     * @throws hudson.util.jna.Native.ExecutionError 
     */
    abstract public NativeSystemMemory getSystemMemory() throws NativeAccessException;

    /**
     * Get the effective User ID on a Unix System
     * @return int
     * @throws hudson.util.jna.Native.ExecutionError 
     */
    abstract public int getEuid() throws NativeAccessException;

    /**
     * Get the effective Group ID on a Unix System
     * @return int 
     * @throws hudson.util.jna.Native.ExecutionError 
     */
    abstract public int getEgid() throws NativeAccessException;


    /**
     * Restart current Java process (JVM in which this application is running)
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public void restartJavaProcess(Map<String,String> properties, boolean asDaemon) throws NativeAccessException;

    /**
     * Check if this Java process can be restarted
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public boolean canRestartJavaProcess() throws NativeAccessException;

    /**
     * Check if the Unix user exists on the machine where this program runs
     * @param userName
     * @return
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public boolean checkUnixUser(String userName) throws NativeAccessException;
    
    /**
     * Check if the Unix group exists on the machine where this program runs
     * @param groupName
     * @return
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public boolean checkUnixGroup(String groupName) throws NativeAccessException;

    /**
     * Authenticate using Using Unix Pluggable Authentication Modules (PAM)
     * @param serviceName, sshd is the default
     * @param userName
     * @param password
     * @return Set<String> list of groups to which this user belongs
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public Set<String> pamAuthenticate(String serviceName, String userName, String password) throws NativeAccessException;

    /**
     * Check if PAM Authentication available in the machine where this program runs
     * @return Message corresponding to the availability of PAM
     * @throws hudson.util.jna.Native.NativeExecutionException 
     */
    abstract public String checkPamAuthentication() throws NativeAccessException;

    /**
     * Do the Unix style chown (change Owner permission) on a File
     * @param file
     * @param mask
     * @return true if the function executed successfully
     * @throws hudson.util.jna.Native.ExecutionError 
     */
    abstract public boolean chown(File file, int uid, int gid) throws NativeAccessException;;

    abstract public String getProcessUser() throws NativeAccessException;;
}

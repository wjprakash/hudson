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

import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.Extension;
import hudson.os.SU;
import hudson.model.AdministrativeMonitorExt;
import hudson.model.HudsonExt;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.util.ForkOutputStream;
import hudson.util.StreamTaskListener;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeUtils;
import hudson.util.jna.NativeZfsFileSystem;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encourages the user to migrate HUDSON_HOME on a ZFS file system. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.283
 */
public class ZFSInstallerExt extends AdministrativeMonitorExt implements Serializable {
    /**
     * True if $HUDSON_HOME is a ZFS file system by itself.
     */
    private final boolean active = shouldBeActive();

    /**
     * This will be the file system name that we'll create.
     */
    private String prospectiveZfsFileSystemName;
    
    protected NativeUtils nativeUtils = NativeUtils.getInstance();

    public boolean isActivated() {
        return active;
    }

    public boolean isRoot() {
        try {
            return NativeUtils.getInstance().getEuid() == 0;
        } catch (NativeAccessException exc) {
            LOGGER.log(Level.INFO, "Native Support to find EUID failed - {0}", exc.getLocalizedMessage());
            return false;
        }
    }

    public String getProspectiveZfsFileSystemName() {
        return prospectiveZfsFileSystemName;
    }

    private boolean shouldBeActive() {
        if(!System.getProperty("os.name").equals("SunOS") || disabled)
            // on systems that don't have ZFS, we don't need this monitor
            return false;

        try {
            List<NativeZfsFileSystem> roots = nativeUtils.getZfsRoots();
            
            if(roots.isEmpty())
                return false;       // no active ZFS pool

            // if we don't run on a ZFS file system, activate
            NativeZfsFileSystem hudsonZfs = nativeUtils.getZfsByMountPoint(HudsonExt.getInstance().getRootDir());
            if(hudsonZfs!=null)
                return false;       // already on ZFS

            // decide what file system we'll create
            NativeZfsFileSystem pool = roots.get(0);
            
            prospectiveZfsFileSystemName = computeHudsonFileSystemName(pool);

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to detect whether Hudson is on ZFS",e);
            return false;
        } catch (LinkageError e) {
            LOGGER.info("No ZFS available. If you believe this is an error, increase the logging level to get the stack trace");
            LOGGER.log(Level.FINE,"Stack trace of failed ZFS load",e);
            return false;
        }
    }

    /**
     * Creates a ZFS file system to migrate the data to.
     *
     * <p>
     * This has to be done while we still have an interactive access with the user, since it involves the password.
     *
     * <p>
     * An exception will be thrown if the operation fails. A normal completion means a success.
     *
     * @return
     *      The ZFS dataset name to migrate the data to.
     */
    protected String createZfsFileSystem(final TaskListener listener, String rootUsername, String rootPassword) throws IOException, InterruptedException {
        // capture the UID that HudsonExt runs under
        // so that we can allow this user to do everything on this new partition
        
        final File home = HudsonExt.getInstance().getRootDir();

        // this is the actual creation of the file system.
        // return true indicating a success
        return SU.execute(listener, rootUsername, rootPassword, new Callable<String, IOException>() {

            public String call() throws IOException {
                NativeZfsFileSystem hudson = null;
                try {
                    PrintStream out = listener.getLogger();

                    int uid = nativeUtils.getEuid();
                    int gid = nativeUtils.getEgid();
                    final String userName = nativeUtils.getProcessUser();


                    NativeZfsFileSystem existing = nativeUtils.getZfsByMountPoint(home);

                    if (existing != null) {
                        // no need for migration
                        out.println(home + " is already on ZFS. Doing nothing");
                        return existing.getName();
                    }
                    List<NativeZfsFileSystem> roots = nativeUtils.getZfsRoots();
                    String name = computeHudsonFileSystemName(roots.get(0));
                    out.println("Creating " + name);

                    hudson = nativeUtils.createZfs(name);

                    // mount temporarily to set the owner right
                    File dir = Util.createTempDir();
                    hudson.setMountPoint(dir);
                    hudson.mount();
                    if (nativeUtils.chown(dir, uid, gid)) {
                        throw new IOException("Failed to chown " + dir);
                    }
                    hudson.unmount();

                    hudson.setProperty("hudson:managed-by", "hudson"); // mark this file system as "managed by HudsonExt"

                    hudson.allow(userName);
                    return hudson.getName();
                } catch (NativeAccessException ex) {
                    Logger.getLogger(ZFSInstallerExt.class.getName()).log(Level.SEVERE, null, ex);
                    if (hudson != null){
                        hudson.destory();
                    }
                    throw new IOException();
                }
            }
        });
    }

    @Extension
    public static AdministrativeMonitorExt init() {
        String migrationTarget = System.getProperty(ZFSInstallerExt.class.getName() + ".migrate");
        if(migrationTarget!=null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamTaskListener listener = new StreamTaskListener(new ForkOutputStream(System.out, out));
            try {
                if(migrate(listener,migrationTarget)) {
                    // completed successfully
                    return new MigrationCompleteNotice();
                }
            } catch (Exception e) {
                // if we let any exception from here, it will prevent HudsonExt from starting.
                e.printStackTrace(listener.error("Migration failed"));
            }
            // migration failed
            return new MigrationFailedNotice(out);
        }

        // install the monitor if applicable
        ZFSInstallerExt zi = new ZFSInstallerExt();
        if(zi.isActivated())
            return zi;

        return null;
    }

    /**
     * Migrates $HUDSON_HOME to a new ZFS file system.
     *
     * TODO: do this in a separate JVM to elevate the privilege.
     *
     * @param listener
     *      Log of migration goes here.
     * @param target
     *      Dataset to move the data to.
     * @return
     *      false if a migration failed.
     */
    private static boolean migrate(TaskListener listener, String target) throws IOException, InterruptedException {
        try {
            NativeUtils nativeUtils = NativeUtils.getInstance();
            
            PrintStream out = listener.getLogger();

            File home = HudsonExt.getInstance().getRootDir();
            // do the migration
            NativeZfsFileSystem existing = nativeUtils.getZfsByMountPoint(home);
            if(existing!=null) {
                out.println(home+" is already on ZFS. Doing nothing");
                return true;
            }

            File tmpDir = Util.createTempDir();

            // mount a new file system to a temporary location
            out.println("Opening "+target);
            NativeZfsFileSystem hudson = nativeUtils.openZfs(target);
            hudson.setMountPoint(tmpDir);
            hudson.setProperty("hudson:managed-by","hudson"); // mark this file system as "managed by HudsonExt"
            hudson.mount();

            // copy all the files
            out.println("Copying all existing data files");
            if(system(home,listener, "/usr/bin/cp","-pR",".", tmpDir.getAbsolutePath())!=0) {
                out.println("Failed to copy "+home+" to "+tmpDir);
                return false;
            }

            // unmount
            out.println("Unmounting "+target);
            hudson.unmount(NativeZfsFileSystem.MS_FORCE);

            // move the original directory to the side
            File backup = new File(home.getPath()+".backup");
            out.println("Moving "+home+" to "+backup);
            if(backup.exists())
                Util.deleteRecursive(backup);
            if(!home.renameTo(backup)) {
                out.println("Failed to move your current data "+home+" out of the way");
            }

            // update the mount point
            out.println("Creating a new mount point at "+home);
            if(!home.mkdir())
                throw new IOException("Failed to create mount point "+home);

            out.println("Mounting "+target);
            hudson.setMountPoint(home);
            hudson.mount();

            out.println("Sharing " + target);

            hudson.setProperty("sharesmb", "on");
            hudson.setProperty("sharenfs", "on");
            hudson.share();

            // delete back up
            out.println("Deleting "+backup);
            if(system(new File("/"),listener,"/usr/bin/rm","-rf",backup.getAbsolutePath())!=0) {
                out.println("Failed to delete "+backup.getAbsolutePath());
                return false;
            }

            out.println("Migration completed");
            return true;
        } catch (NativeAccessException ex) {
            Logger.getLogger(ZFSInstallerExt.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private static int system(File pwd, TaskListener listener, String... args) throws IOException, InterruptedException {
        return new LocalLauncher(listener).launch().cmds(args).stdout(System.out).pwd(pwd).join();
    }

    private static String computeHudsonFileSystemName(NativeZfsFileSystem top) {
        try {
            NativeUtils nativeUtils = NativeUtils.getInstance();
            
            if(!nativeUtils.zfsExists(top.getName()+"/hudson"))
                return top.getName()+"/hudson";
            for( int i = 2; ; i++ ) {
                String name = top.getName() + "/hudson" + i;
                if(!nativeUtils.zfsExists(name))
                    return name;
            }
        } catch (NativeAccessException ex) {
            Logger.getLogger(ZFSInstallerExt.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Used to indicate that the migration was completed successfully.
     */
    public static final class MigrationCompleteNotice extends AdministrativeMonitorExt {
        public boolean isActivated() {
            return true;
        }
    }

    /**
     * Used to indicate a failure in the migration.
     */
    public static final class MigrationFailedNotice extends AdministrativeMonitorExt {
        ByteArrayOutputStream record;

        MigrationFailedNotice(ByteArrayOutputStream record) {
            this.record = record;
        }

        public boolean isActivated() {
            return true;
        }
        
        public String getLog() {
            return record.toString();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ZFSInstallerExt.class.getName());

    /**
     * Escape hatch in case JNI calls fatally crash, like in HUDSON-3733.
     */
    public static boolean disabled = Boolean.getBoolean(ZFSInstallerExt.class.getName()+".disabled");
}

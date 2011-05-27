/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Winston Prakash
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
package hudson.lifecycle;

import hudson.FilePathExt;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.HudsonExt;
import hudson.util.StreamTaskListener;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeUtils;
 
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Lifecycle} for HudsonExt installed as Windows service.
 * 
 * @author Kohsuke Kawaguchi
 * @see WindowsInstallerLink
 */
public class WindowsServiceLifecycle extends Lifecycle {
    public WindowsServiceLifecycle() {
        updateHudsonExeIfNeeded();
    }

    /**
     * If <tt>hudson.exe</tt> is old compared to our copy,
     * schedule an overwrite (except that since it's currently running,
     * we can only do it when HudsonExt restarts next time.)
     */
    private void updateHudsonExeIfNeeded() {
        try {
            File rootDir = HudsonExt.getInstance().getRootDir();

            URL exe = getClass().getResource("/windows-service/hudson.exe");
            String ourCopy = Util.getDigestOf(exe.openStream());
            File currentCopy = new File(rootDir,"hudson.exe");
            if(!currentCopy.exists())   return;
            String curCopy = new FilePathExt(currentCopy).digest();

            if(ourCopy.equals(curCopy))
            return; // identical

            File stage = new File(rootDir,"hudson.exe.new");
            FileUtils.copyURLToFile(exe, stage);
            
            NativeUtils.getInstance().windowsMoveFile(stage, currentCopy);
            
            LOGGER.info("Scheduled a replacement of hudson.exe");
        } catch (NativeAccessException exc) {
            LOGGER.log(Level.SEVERE, "Failed to replace hudson.exe", exc);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to replace hudson.exe",e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Failed to replace hudson.exe",e);
        }
    }

    /**
     * On Windows, hudson.war is locked, so we place a new version under a special name,
     * which is picked up by the service wrapper upon restart.
     */
    @Override
    public void rewriteHudsonWar(File by) throws IOException {
        File dest = getHudsonWar();
        // this should be impossible given the canRewriteHudsonWar method,
        // but let's be defensive
        if(dest==null)  throw new IOException("hudson.war location is not known.");

        // backing up the old hudson.war before its lost due to upgrading
        // unless we are trying to rewrite hudson.war by a backup itself
        File bak = new File(dest.getPath() + ".bak");
        if (!by.equals(bak))
            FileUtils.copyFile(dest, bak);

        File rootDir = HudsonExt.getInstance().getRootDir();
        File copyFiles = new File(rootDir,"hudson.copies");

        FileWriter w = new FileWriter(copyFiles, true);
        w.write(by.getAbsolutePath()+'>'+getHudsonWar().getAbsolutePath()+'\n');
        w.close();
    }

    @Override
    public void restart() throws IOException, InterruptedException {
        File me = getHudsonWar();
        File home = me.getParentFile();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener task = new StreamTaskListener(baos);
        task.getLogger().println("Restarting a service");
        int r = new LocalLauncher(task).launch().cmds(new File(home, "hudson.exe"), "restart")
                .stdout(task).pwd(home).join();
        if(r!=0)
            throw new IOException(baos.toString());
    }

    private static final Logger LOGGER = Logger.getLogger(WindowsServiceLifecycle.class.getName());
}

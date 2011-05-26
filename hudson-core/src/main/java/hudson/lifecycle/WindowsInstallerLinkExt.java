/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, CloudBees, Inc.
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

import hudson.FunctionsExt;
import hudson.model.ManagementLink;
import hudson.model.Hudson;
import hudson.Extension;


import java.io.File;
import java.util.logging.Logger;

/**
 * {@link ManagementLink} that allows the installation as a Windows service.
 *
 * @author Kohsuke Kawaguchi
 */
public class WindowsInstallerLinkExt extends ManagementLink {
    
    protected static final Logger LOGGER = Logger.getLogger(WindowsInstallerLinkExt.class.getName());

    /**
     * Location of the hudson.war.
     * In general case, we can't determine this value, yet having this is a requirement for the installer.
     */
    protected final File hudsonWar;

    /**
     * If the installation is completed, this value holds the installation directory.
     */
    protected volatile File installationDir;

    protected WindowsInstallerLinkExt(File hudsonWar) {
        this.hudsonWar = hudsonWar;
    }

    public String getIconFileName() {
        return "installer.gif";
    }

    public String getUrlName() {
        return "install";
    }

    public String getDisplayName() {
        return Messages.WindowsInstallerLink_DisplayName();
    }

    public String getDescription() {
        return Messages.WindowsInstallerLink_Description();
    }

    /**
     * Is the installation successful?
     */
    public boolean isInstalled() {
        return installationDir!=null;
    }

     
    /**
     * Decide if {@link WindowsInstallerLinkExt} should show up in UI, and if so, register it.
     */
    @Extension
    public static ManagementLink registerIfApplicable() {
        if(!FunctionsExt.isWindows())
            return null; // this is a Windows only feature

        if(Lifecycle.get() instanceof WindowsServiceLifecycle)
            return null; // already installed as Windows service

        // this system property is set by the launcher when we run "java -jar hudson.war"
        // and this is how we know where is hudson.war.
        String war = System.getProperty("executable-war");
        if(war!=null && new File(war).exists()) {
            WindowsInstallerLinkExt link = new WindowsInstallerLinkExt(new File(war));

            // in certain situations where we know the user is just trying Hudson (like when Hudson is launched
            // from JNLP from https://hudson.java.net/), also put this link on the navigation bar to increase
            // visibility
            if(System.getProperty(WindowsInstallerLinkExt.class.getName()+".prominent")!=null)
                Hudson.getInstance().getActions().add(link);

            return link;
        }

        return null;
    }

}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe
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
package hudson.model;

import hudson.PluginManagerExt;
import hudson.PluginWrapperExt;
import hudson.StaplerUtils;
import hudson.lifecycle.Lifecycle;
import hudson.model.UpdateSiteExt.PluginExt;
import hudson.security.ACL;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;
import org.acegisecurity.context.SecurityContextHolder;


/**
 * Controls update center capability.
 *
 * <p>
 * The main job of this class is to keep track of the latest update center metadata file, and perform installations.
 * Much of the UI about choosing plugins to install is done in {@link PluginManagerExt}.
 * <p>
 * The update center can be configured to contact alternate servers for updates
 * and plugins, and to use alternate strategies for downloading, installing
 * and updating components. See the Javadocs for {@link UpdateCenterConfiguration}
 * for more information.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.220
 */
public class UpdateCenter extends UpdateCenterExt {
    
     private static final Logger LOGGER = Logger.getLogger(UpdateCenter.class.getName());
     
    /**
     * Schedules a HudsonExt upgrade.
     */
    public void doUpgrade(StaplerResponse rsp) throws IOException, ServletException {
        StaplerUtils.requirePOST();
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);
        HudsonUpgradeJob job = new HudsonUpgradeJob(getCoreSource(), HudsonExt.getAuthentication());
        if(!Lifecycle.get().canRewriteHudsonWar()) {
            StaplerUtils.sendError(this, "Hudson upgrade not supported in this running mode");
            return;
        }

        LOGGER.info("Scheduling the core upgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }


    /**
     * Performs hudson downgrade.
     */
    public void doDowngrade(StaplerResponse rsp) throws IOException, ServletException {
        StaplerUtils.requirePOST();
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);
        if(!isDowngradable()) {
            StaplerUtils.sendError(this, "Hudson downgrade is not possible, probably backup does not exist");
            return;
        }

        HudsonDowngradeJob job = new HudsonDowngradeJob(getCoreSource(), HudsonExt.getAuthentication());
        LOGGER.info("Scheduling the core downgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }

    /**
     * Represents the state of the installation activity of one plugin.
     */
    public final class InstallationJob extends UpdateCenterExt.InstallationJob {
        

        public InstallationJob(PluginExt plugin, UpdateSiteExt site, Authentication auth) {
            super(plugin, site, auth);
        }
        
       


        @Override
        public void _run() throws IOException {
            super._run();

            // if this is a bundled plugin, make sure it won't get overwritten
            PluginWrapperExt pw = plugin.getInstalled();
            if (pw != null && pw.isBundled())
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                    pw.doPin();
                } finally {
                    SecurityContextHolder.clearContext();
                }
        }

    }

     
}

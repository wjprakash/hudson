package hudson.tools;

import org.kohsuke.stapler.DataBoundConstructor;


/**
 * Partial convenience implementation of {@link ToolInstaller} that just downloads
 * an archive from the URL and extracts it.
 *
 * <p>
 * Each instance of this is configured to download from a specific URL identified by an ID.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.308
 */
public abstract class DownloadFromUrlInstaller extends DownloadFromUrlInstallerExt {
    
    @DataBoundConstructor
    protected DownloadFromUrlInstaller(String id) {
         super(id);
    }
}

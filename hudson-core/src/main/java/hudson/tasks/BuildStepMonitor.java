package hudson.tasks;

import hudson.model.AbstractBuildExt;
import hudson.model.BuildListener;
import hudson.model.CheckPoint;
import hudson.Launcher;

import java.io.IOException;

/**
 * Used by {@link BuildStep#getRequiredMonitorService()}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
public enum BuildStepMonitor {
    NONE {
        public boolean perform(BuildStep bs, AbstractBuildExt build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return bs.perform(build,launcher,listener);
        }
    },
    STEP {
        public boolean perform(BuildStep bs, AbstractBuildExt build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            CheckPoint cp = new CheckPoint(bs.getClass().getName(),bs.getClass());
            cp.block();
            try {
                return bs.perform(build,launcher,listener);
            } finally {
                cp.report();
            }
        }
    },
    BUILD {
        public boolean perform(BuildStep bs, AbstractBuildExt build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            CheckPoint.COMPLETED.block();
            return bs.perform(build,launcher,listener);
        }
    };

    /**
     * Calls {@link BuildStep#perform(AbstractBuildExt, Launcher, BuildListener)} with the proper synchronization.
     */
    public abstract boolean perform(BuildStep bs, AbstractBuildExt build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
}

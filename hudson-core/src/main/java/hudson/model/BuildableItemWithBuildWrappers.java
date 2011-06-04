package hudson.model;

import hudson.tasks.BuildWrapper;
import hudson.util.DescribableListExt;

/**
 * {@link AbstractProjectExt} that has associated {@link BuildWrapper}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.335
 */
public interface BuildableItemWithBuildWrappers extends BuildableItem {
    /**
     * {@link BuildableItemWithBuildWrappers} needs to be an instance of
     * {@link AbstractProjectExt}.
     *
     * <p>
     * This method must be always implemented as {@code (AbstractProjectExt)this}, but
     * defining this method emphasizes the fact that this cast must be doable.
     */
    AbstractProjectExt<?,?> asProject();

    /**
     * {@link BuildWrapper}s associated with this {@link AbstractProjectExt}.
     *
     * @return
     *      can be empty but never null. This list is live, and changes to it will be reflected
     *      to the project configuration.
     */
    DescribableListExt<BuildWrapper,DescriptorExt<BuildWrapper>> getBuildWrappersList();
}


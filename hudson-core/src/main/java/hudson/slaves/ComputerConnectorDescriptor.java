package hudson.slaves;

import hudson.DescriptorExtensionListExt;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;

/**
 * {@link DescriptorExt} for {@link ComputerConnector}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.383
 */
public abstract class ComputerConnectorDescriptor extends DescriptorExt<ComputerConnector> {
    public static DescriptorExtensionListExt<ComputerConnector,ComputerConnectorDescriptor> all() {
        return HudsonExt.getInstance().getDescriptorList(ComputerConnector.class);
    }
}

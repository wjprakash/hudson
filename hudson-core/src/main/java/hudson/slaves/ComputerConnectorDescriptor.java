package hudson.slaves;

import hudson.DescriptorExtensionListExt;
import hudson.model.Descriptor;
import hudson.model.Hudson;

/**
 * {@link Descriptor} for {@link ComputerConnector}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.383
 */
public abstract class ComputerConnectorDescriptor extends Descriptor<ComputerConnector> {
    public static DescriptorExtensionListExt<ComputerConnector,ComputerConnectorDescriptor> all() {
        return Hudson.getInstance().getDescriptorList(ComputerConnector.class);
    }
}

package hudson.node_monitors;

import org.kohsuke.stapler.DataBoundConstructor;

import java.text.ParseException;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDiskSpaceMonitor extends AbstractDiskSpaceMonitorExt {
     
    @DataBoundConstructor
    public AbstractDiskSpaceMonitor(String threshold) throws ParseException {
       super(threshold);
    }
    
     public AbstractDiskSpaceMonitor() {
        super();
    }
     
}

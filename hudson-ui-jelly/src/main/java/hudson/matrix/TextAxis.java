package hudson.matrix;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * User-defined plain text axis.
 * 
 * @author Kohsuke Kawaguchi
 */
public class TextAxis extends TextAxisExt {
    public TextAxis(String name, List<String> values) {
        super(name, values);
    }

    public TextAxis(String name, String... values) {
        super(name, values);
    }

    @DataBoundConstructor
    public TextAxis(String name, String valueString) {
        super(name, valueString);
    }
}

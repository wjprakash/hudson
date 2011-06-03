package hudson.matrix;

import hudson.Extension;

import java.util.List;

/**
 * User-defined plain text axis.
 * 
 * @author Kohsuke Kawaguchi
 */
public class TextAxisExt extends AxisExt {
    public TextAxisExt(String name, List<String> values) {
        super(name, values);
    }

    public TextAxisExt(String name, String... values) {
        super(name, values);
    }

    public TextAxisExt(String name, String valueString) {
        super(name, valueString);
    }

    @Extension
    public static class DescriptorImpl extends AxisDescriptorExt {
        @Override
        public String getDisplayName() {
            return Messages.TextArea_DisplayName();
        }
    }
}

package hudson.model;

import hudson.cli.CLICommand;

import java.io.IOException;

/**
 * Convenient base class for {@link ParameterDefinitionExt} whose value can be represented in a context-independent single string token.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SimpleParameterDefinitionExt extends ParameterDefinitionExt {
    protected SimpleParameterDefinitionExt(String name) {
        super(name);
    }

    protected SimpleParameterDefinitionExt(String name, String description) {
        super(name, description);
    }

    /**
     * Creates a {@link ParameterValueExt} from the string representation.
     */
    public abstract ParameterValueExt createValue(String value);

    
    @Override
    public final ParameterValueExt createValue(CLICommand command, String value) throws IOException, InterruptedException {
        return createValue(value);
    }
}

package hudson.model;

import org.apache.commons.lang.StringUtils;
import hudson.Extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * @author huybrechts
 */
public abstract class ChoiceParameterDefinitionExt extends SimpleParameterDefinition {
    private final List<String> choices;

    public ChoiceParameterDefinitionExt(String name, String choices, String description) {
        super(name, description);
        this.choices = Arrays.asList(choices.split("\\r?\\n"));
        if (choices.length()==0) {
            throw new IllegalArgumentException("No choices found");
        }
    }

    public ChoiceParameterDefinitionExt(String name, String[] choices, String description) {
        super(name, description);
        this.choices = new ArrayList<String>(Arrays.asList(choices));
        if (this.choices.isEmpty()) {
            throw new IllegalArgumentException("No choices found");
        }
    }
    
    public List<String> getChoices() {
        return choices;
    }

    public String getChoicesText() {
        return StringUtils.join(choices, "\n");
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), choices.get(0), getDescription());
    }


    private StringParameterValue checkValue(StringParameterValue value) {
        if (!choices.contains(value.value))
            throw new IllegalArgumentException("Illegal choice: " + value.value);
        return value;
    }

    public StringParameterValue createValue(String value) {
        return checkValue(new StringParameterValue(getName(), value, getDescription()));
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ChoiceParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/choice.html";
        }
    }

}
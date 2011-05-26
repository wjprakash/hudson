package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONObject;
import hudson.Extension;
import hudson.cli.CLICommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.io.IOException;

/**
 * @author huybrechts
 */
public class ChoiceParameterDefinition extends ChoiceParameterDefinitionExt {
     
    @DataBoundConstructor
    public ChoiceParameterDefinition(String name, String choices, String description) {
        super(name, choices, description);
        
    }

    public ChoiceParameterDefinition(String name, String[] choices, String description) {
        super(name, choices, description);
    }
    
    @Exported
    public List<String> getChoices() {
        return super.getChoices();
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        value.setDescription(getDescription());
        return checkValue(value);
    }

    @Extension
    public static class DescriptorImpl extends ChoiceParameterDefinitionExt.DescriptorImpl {
        
    }

}
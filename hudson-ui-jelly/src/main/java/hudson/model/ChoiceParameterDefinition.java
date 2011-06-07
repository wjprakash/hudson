package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import net.sf.json.JSONObject;
import hudson.Extension;

import java.util.List;

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
    public ParameterValueExt createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValueExt value = req.bindJSON(StringParameterValueExt.class, jo);
        value.setDescription(getDescription());
        return checkValue(value);
    }

    @Extension
    public static class DescriptorImpl extends ChoiceParameterDefinitionExt.DescriptorImpl {
        
    }

}
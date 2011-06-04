/**
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import java.util.logging.Logger;

import hudson.Extension;

import hudson.model.Descriptor.FormException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A crumb issuing algorithm based on the request principal and the remote address.
 * 
 * @author dty
 */
public class DefaultCrumbIssuer extends DefaultCrumbIssuerExt {
    
    @DataBoundConstructor
    public DefaultCrumbIssuer(boolean excludeClientIPFromCrumb) {
         super(excludeClientIPFromCrumb);
    }
    
    @Extension
    public static final class DescriptorImpl extends DescriptorImplExt{

        public DescriptorImpl() {
            super();
        }

        public DefaultCrumbIssuer newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(DefaultCrumbIssuer.class, formData);
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuer.class.getName());
}

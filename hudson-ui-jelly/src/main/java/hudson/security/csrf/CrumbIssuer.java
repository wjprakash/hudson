/**
 * Copyright (c) 2008-2009 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;


import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.Api;

/**
 * A CrumbIssuer represents an algorithm to generate a nonce value, known as a
 * crumb, to counter cross site request forgery exploits. Crumbs are typically
 * hashes incorporating information that uniquely identifies an agent that sends
 * a request, along with a guarded secret so that the crumb value cannot be
 * forged by a third party.
 *
 * @author dty
 * @see http://en.wikipedia.org/wiki/XSRF
 */
@ExportedBean
public abstract class CrumbIssuer extends CrumbIssuerExt {

    private static final String CRUMB_ATTRIBUTE = CrumbIssuer.class.getName() + "_crumb";

    /**
     * Get the name of the request parameter the crumb will be stored in. Exposed
     * here for the remote API.
     */
    @Exported
    @Override
    public String getCrumbRequestField() {
        return super.getCrumbRequestField();
    }

    /**
     * Get a crumb value based on user specific information in the current request.
     * Intended for use only by the remote API.
     * @return
     */
    @Exported
    public String getCrumb() {
        return getCrumb(Stapler.getCurrentRequest());
    }

    public Api getApi() {
        return new Api(this);
    }
}

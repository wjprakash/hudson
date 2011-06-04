/**
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.HudsonExt;
import hudson.model.ModelObject;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.acegisecurity.Authentication;


/**
 * A crumb issuing algorithm based on the request principal and the remote address.
 * 
 * @author dty
 */
public class DefaultCrumbIssuerExt extends CrumbIssuerExt {
    
    private transient MessageDigest md;
    private boolean excludeClientIPFromCrumb;

    public DefaultCrumbIssuerExt(boolean excludeClientIPFromCrumb) {
        try {
            this.md = MessageDigest.getInstance("MD5");
            this.excludeClientIPFromCrumb = excludeClientIPFromCrumb;
        } catch (NoSuchAlgorithmException e) {
            this.md = null;
            this.excludeClientIPFromCrumb = false;
            LOGGER.log(Level.SEVERE, "Can't find MD5", e);
        }
    }

    public boolean isExcludeClientIPFromCrumb() {
        return this.excludeClientIPFromCrumb;
    }
    
    private Object readResolve() {
        try {
            this.md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            this.md = null;
            LOGGER.log(Level.SEVERE, "Can't find MD5", e);
        }
        
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected String issueCrumb(ServletRequest request, String salt) {
        if (request instanceof HttpServletRequest) {
            if (md != null) {
                HttpServletRequest req = (HttpServletRequest) request;
                StringBuilder buffer = new StringBuilder();
                Authentication a = HudsonExt.getAuthentication();
                if (a != null) {
                    buffer.append(a.getName());
                }
                buffer.append(';');
                if (!isExcludeClientIPFromCrumb()) {
                    buffer.append(getClientIP(req));
                }

                md.update(buffer.toString().getBytes());
                byte[] crumbBytes = md.digest(salt.getBytes());

                StringBuilder hexString = new StringBuilder();
                for (int i = 0; i < crumbBytes.length; i++) {
                    String hex = Integer.toHexString(0xFF & crumbBytes[i]);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                return hexString.toString();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        if (request instanceof HttpServletRequest) {
            String newCrumb = issueCrumb(request, salt);
            if ((newCrumb != null) && (crumb != null)) {
                return newCrumb.equals(crumb);
            }
        }
        return false;
    }

    private final String PROXY_HEADER = "X-Forwarded-For";

    private String getClientIP(HttpServletRequest req) {
        String defaultAddress = req.getRemoteAddr();
        String forwarded = req.getHeader(PROXY_HEADER);
        if (forwarded != null) {
	        String[] hopList = forwarded.split(",");
            if (hopList.length >= 1) {
                return hopList[0];
            }
        }
        return defaultAddress;
    }
    
    @Extension
    public static class DescriptorImplExt extends CrumbIssuerDescriptor<DefaultCrumbIssuerExt> implements ModelObject {

        public DescriptorImplExt() {
            super(HudsonExt.getInstance().getSecretKey(), System.getProperty("hudson.security.csrf.requestfield", ".crumb"));
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.DefaultCrumbIssuer_DisplayName();
        }

    }
    
    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuerExt.class.getName());
}

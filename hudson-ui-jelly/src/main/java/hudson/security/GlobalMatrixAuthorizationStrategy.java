/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.security;

import hudson.model.HudsonExt;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.FunctionsExt;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.QueryParameter;
import org.springframework.dao.DataAccessException;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Role-based authorization via a matrix.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO: think about the concurrency commitment of this class
public class GlobalMatrixAuthorizationStrategy extends GlobalMatrixAuthorizationStrategyExt {

     
    public static class DescriptorImpl extends DescriptorImplExt {
        protected DescriptorImpl(Class<? extends GlobalMatrixAuthorizationStrategy> clazz) {
            super(clazz);
        }

        public DescriptorImpl() {
            super();
        }

        public FormValidation doCheckName(@QueryParameter String value ) throws IOException, ServletException {
            return doCheckName(value, HudsonExt.getInstance(), HudsonExt.ADMINISTER);
        }

        FormValidation doCheckName(String value, AccessControlled subject, Permission permission) throws IOException, ServletException {
            if(!subject.hasPermission(permission))  return FormValidation.ok(); // can't check

            final String v = value.substring(1,value.length()-1);
            SecurityRealmExt sr = HudsonExt.getInstance().getSecurityRealm();
            String ev = FunctionsExt.escape(v);

            if(v.equals("authenticated"))
                // system reserved group
                return FormValidation.respond(Kind.OK, makeImg("user.gif") +ev);

            try {
                sr.loadUserByUsername(v);
                return FormValidation.respond(Kind.OK, makeImg("person.gif")+ev);
            } catch (UserMayOrMayNotExistException e) {
                // undecidable, meaning the user may exist
                return FormValidation.respond(Kind.OK, ev);
            } catch (UsernameNotFoundException e) {
                // fall through next
            } catch (DataAccessException e) {
                // fall through next
            }

            try {
                sr.loadGroupByGroupname(v);
                return FormValidation.respond(Kind.OK, makeImg("user.gif") +ev);
            } catch (UserMayOrMayNotExistException e) {
                // undecidable, meaning the group may exist
                return FormValidation.respond(Kind.OK, ev);
            } catch (UsernameNotFoundException e) {
                // fall through next
            } catch (DataAccessException e) {
                // fall through next
            }

            // couldn't find it. it doesn't exist
            return FormValidation.respond(Kind.ERROR, makeImg("error.gif") +ev);
        }

        private String makeImg(String gif) {
            return String.format("<img src='%s%s/images/16x16/%s' style='margin-right:0.2em'>", Stapler.getCurrentRequest().getContextPath(), HudsonExt.RESOURCE_PATH, gif);
        }
    }
}


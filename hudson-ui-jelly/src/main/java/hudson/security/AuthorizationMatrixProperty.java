/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Peter Hayes, Tom Huybrechts
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

import hudson.model.AbstractProjectExt;
import hudson.model.ItemExt;
import hudson.model.JobExt;
import hudson.model.JobPropertyExt;
import hudson.model.HudsonExt;
import hudson.model.RunExt;
import hudson.Extension;
import hudson.util.FormValidation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;

import hudson.model.Descriptor.FormException;
import hudson.model.JobPropertyDescriptor;

import javax.servlet.ServletException;

/**
 * {@link JobPropertyExt} to associate ACL for each project.
 *
 * <p>
 * Once created (and initialized), this object becomes immutable.
 */
public class AuthorizationMatrixProperty extends AuthorizationMatrixPropertyExt {

    public AuthorizationMatrixProperty(Map<Permission, Set<String>> grantedPermissions) {
        super(grantedPermissions);
    }

     
    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public JobPropertyExt<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            formData = formData.getJSONObject("useProjectSecurity");
            if (formData.isNullObject()) {
                return null;
            }

            AuthorizationMatrixPropertyExt amp = new AuthorizationMatrixPropertyExt();
            for (Map.Entry<String, Object> r : (Set<Map.Entry<String, Object>>) formData.getJSONObject("data").entrySet()) {
                String sid = r.getKey();
                if (r.getValue() instanceof JSONObject) {
                    for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) ((JSONObject) r.getValue()).entrySet()) {
                        if (e.getValue()) {
                            Permission p = Permission.fromId(e.getKey());
                            amp.add(p, sid);
                        }
                    }
                }
            }
            return amp;
        }

        @Override
        public boolean isApplicable(Class<? extends JobExt> jobType) {
            // only applicable when ProjectMatrixAuthorizationStrategy is in charge
            return HudsonExt.getInstance().getAuthorizationStrategy() instanceof ProjectMatrixAuthorizationStrategyExt;
        }

        @Override
        public String getDisplayName() {
            return "Authorization Matrix";
        }

        public List<PermissionGroup> getAllGroups() {
            return Arrays.asList(PermissionGroup.get(ItemExt.class), PermissionGroup.get(RunExt.class));
        }

        public boolean showPermission(Permission p) {
            return p.getEnabled() && p != ItemExt.CREATE;
        }

        public FormValidation doCheckName(@AncestorInPath JobExt project, @QueryParameter String value) throws IOException, ServletException {
            return GlobalMatrixAuthorizationStrategy.DESCRIPTOR.doCheckName(value, project, AbstractProjectExt.CONFIGURE);
        }
    }
}

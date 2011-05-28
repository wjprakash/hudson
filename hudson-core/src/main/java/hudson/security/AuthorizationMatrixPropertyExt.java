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

import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.JobExt;
import hudson.model.JobPropertyExt;
import hudson.util.RobustReflectionConverter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.acegisecurity.acls.sid.Sid;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * {@link JobPropertyExt} to associate ACL for each project.
 *
 * <p>
 * Once created (and initialized), this object becomes immutable.
 */
public class AuthorizationMatrixPropertyExt extends JobPropertyExt<JobExt<?, ?>> {

    private transient SidACL acl = new AclImpl();
    /**
     * List up all permissions that are granted.
     * 
     * Strings are either the granted authority or the principal, which is not
     * distinguished.
     */
    private final Map<Permission, Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();
    private Set<String> sids = new HashSet<String>();

    protected AuthorizationMatrixPropertyExt() {
    }

    public AuthorizationMatrixPropertyExt(Map<Permission, Set<String>> grantedPermissions) {
        // do a deep copy to be safe
        for (Entry<Permission, Set<String>> e : grantedPermissions.entrySet()) {
            this.grantedPermissions.put(e.getKey(), new HashSet<String>(e.getValue()));
        }
    }

    public Set<String> getGroups() {
        return sids;
    }

    /**
     * Returns all SIDs configured in this matrix, minus "anonymous"
     * 
     * @return Always non-null.
     */
    public List<String> getAllSIDs() {
        Set<String> r = new HashSet<String>();
        for (Set<String> set : grantedPermissions.values()) {
            r.addAll(set);
        }
        r.remove("anonymous");

        String[] data = r.toArray(new String[r.size()]);
        Arrays.sort(data);
        return Arrays.asList(data);
    }

    /**
     * Returns all the (Permission,sid) pairs that are granted, in the multi-map form.
     *
     * @return
     *      read-only. never null.
     */
    public Map<Permission, Set<String>> getGrantedPermissions() {
        return Collections.unmodifiableMap(grantedPermissions);
    }

    /**
     * Adds to {@link #grantedPermissions}. Use of this method should be limited
     * during construction, as this object itself is considered immutable once
     * populated.
     */
    protected void add(Permission p, String sid) {
        Set<String> set = grantedPermissions.get(p);
        if (set == null) {
            grantedPermissions.put(p, set = new HashSet<String>());
        }
        set.add(sid);
        sids.add(sid);
    }

    private final class AclImpl extends SidACL {

        protected Boolean hasPermission(Sid sid, Permission p) {
            if (AuthorizationMatrixPropertyExt.this.hasPermission(toString(sid), p)) {
                return true;
            }
            return null;
        }
    }

    public SidACL getACL() {
        return acl;
    }

    /**
     * Checks if the given SID has the given permission.
     */
    public boolean hasPermission(String sid, Permission p) {
        for (; p != null; p = p.impliedBy) {
            Set<String> set = grantedPermissions.get(p);
            if (set != null && set.contains(sid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the permission is explicitly given, instead of implied through {@link Permission#impliedBy}.
     */
    public boolean hasExplicitPermission(String sid, Permission p) {
        Set<String> set = grantedPermissions.get(p);
        return set != null && set.contains(sid);
    }

    /**
     * Works like {@link #add(Permission, String)} but takes both parameters
     * from a single string of the form <tt>PERMISSIONID:sid</tt>
     */
    private void add(String shortForm) {
        int idx = shortForm.indexOf(':');
        Permission p = Permission.fromId(shortForm.substring(0, idx));
        if (p == null) {
            throw new IllegalArgumentException("Failed to parse '" + shortForm + "' --- no such permission");
        }
        add(p, shortForm.substring(idx + 1));
    }

    /**
     * Persist {@link ProjectMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link ProjectMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static final class ConverterImpl implements Converter {

        public boolean canConvert(Class type) {
            return type == AuthorizationMatrixPropertyExt.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer,
                MarshallingContext context) {
            AuthorizationMatrixPropertyExt amp = (AuthorizationMatrixPropertyExt) source;

            for (Entry<Permission, Set<String>> e : amp.grantedPermissions.entrySet()) {
                String p = e.getKey().getId();
                for (String sid : e.getValue()) {
                    writer.startNode("permission");
                    writer.setValue(p + ':' + sid);
                    writer.endNode();
                }
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader,
                final UnmarshallingContext context) {
            AuthorizationMatrixPropertyExt as = new AuthorizationMatrixPropertyExt();

            String prop = reader.peekNextChild();
            if (prop != null && prop.equals("useProjectSecurity")) {
                reader.moveDown();
                reader.getValue(); // we used to use this but not any more.
                reader.moveUp();
            }
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                try {
                    as.add(reader.getValue());
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(AuthorizationMatrixPropertyExt.class.getName()).log(Level.WARNING, "Skipping a non-existent permission", ex);
                    RobustReflectionConverter.addErrorInContext(context, ex);
                }
                reader.moveUp();
            }

            if (GlobalMatrixAuthorizationStrategyExt.migrateHudson2324(as.grantedPermissions)) {
                OldDataMonitorExt.report(context, "1.301");
            }

            return as;
        }
    }
}

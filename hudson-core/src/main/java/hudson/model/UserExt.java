/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Tom Huybrechts
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
package hudson.model;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import com.thoughtworks.xstream.XStream;
import hudson.CopyOnWrite;
import hudson.FunctionsExt;
import hudson.Util;
import hudson.XmlFile;
import hudson.BulkChange;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.RunList;
import hudson.util.XStream2;

import org.acegisecurity.Authentication;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import java.io.File;
import java.io.IOException;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a user.
 *
 * <p>
 * In HudsonExt, {@link User} objects are created in on-demand basis;
 * for example, when a build is performed, its change log is computed
 * and as a result commits from users who HudsonExt has never seen may be discovered.
 * When this happens, new {@link User} object is created.
 *
 * <p>
 * If the persisted record for an user exists, the information is loaded at
 * that point, but if there's no such record, a fresh instance is created from
 * thin air (this is where {@link UserPropertyDescriptor#newInstance(User)} is
 * called to provide initial {@link UserProperty} objects.
 *
 * <p>
 * Such newly created {@link User} objects will be simply GC-ed without
 * ever leaving the persisted record, unless {@link User#save()} method
 * is explicitly invoked (perhaps as a result of a browser submitting a
 * configuration.)
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class UserExt extends AbstractModelObjectExt implements AccessControlled, Saveable, Comparable<UserExt> {

    private transient final String id;

    protected volatile String fullName;

    protected volatile String description;

    /**
     * List of {@link UserProperty}s configured for this project.
     */
    @CopyOnWrite
    private volatile List<UserPropertyExt> properties = new ArrayList<UserPropertyExt>();


    protected UserExt(String id, String fullName) {
        this.id = id;
        this.fullName = fullName;
        load();
    }

    public int compareTo(UserExt that) {
        return this.id.compareTo(that.id);
    }

    /**
     * Loads the other data from disk if it's available.
     */
    private synchronized void load() {
        properties.clear();

        XmlFile config = getConfigFile();
        try {
            if(config.exists())
                config.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load "+config,e);
        }

        // remove nulls that have failed to load
        for (Iterator<UserPropertyExt> itr = properties.iterator(); itr.hasNext();) {
            if(itr.next()==null)
                itr.remove();            
        }

        // allocate default instances if needed.
        // doing so after load makes sure that newly added user properties do get reflected
        for (UserPropertyDescriptor d : UserPropertyExt.all()) {
            if(getProperty(d.clazz)==null) {
                UserPropertyExt up = d.newInstance(this);
                if(up!=null)
                    properties.add(up);
            }
        }

        for (UserPropertyExt p : properties)
            p.setUser(this);
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return "user/"+Util.rawEncode(id);
    }

    public String getSearchUrl() {
        return "/user/"+Util.rawEncode(id);
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     *
     * @return
     *      never null.
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the human readable name of thie user.
     */
    public void setFullName(String name) {
        if(Util.fixEmptyAndTrim(name)==null)    name=id;
        this.fullName = name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Gets the user properties configured for this user.
     */
    public Map<DescriptorExt<UserPropertyExt>,UserPropertyExt> getProperties() {
        return DescriptorExt.toMap(properties);
    }

    /**
     * Updates the user object by adding a property.
     */
    public synchronized void addProperty(UserPropertyExt p) throws IOException {
        UserPropertyExt old = getProperty(p.getClass());
        List<UserPropertyExt> ps = new ArrayList<UserPropertyExt>(properties);
        if(old!=null)
            ps.remove(old);
        ps.add(p);
        p.setUser(this);
        properties = ps;
        save();
    }

    /**
     * List of all {@link UserProperty}s exposed primarily for the remoting API.
     */
    public List<UserPropertyExt> getAllProperties() {
        return Collections.unmodifiableList(properties);
    }
    
    /**
     * Gets the specific property, or null.
     */
    public <T extends UserPropertyExt> T getProperty(Class<T> clazz) {
        for (UserPropertyExt p : properties) {
            if(clazz.isInstance(p))
                return clazz.cast(p);
        }
        return null;
    }

    /**
     * Gets the fallback "unknown" user instance.
     * <p>
     * This is used to avoid null {@link User} instance.
     */
    public static UserExt getUnknown() {
        return get("unknown");
    }

    /**
     * Gets the {@link User} object by its id or full name.
     *
     * @param create
     *      If true, this method will never return null for valid input
     *      (by creating a new {@link User} object if none exists.)
     *      If false, this method will return null if {@link User} object
     *      with the given name doesn't exist.
     */
    public static UserExt get(String idOrFullName, boolean create) {
        if(idOrFullName==null)
            return null;
        String id = idOrFullName.replace('\\', '_').replace('/', '_').replace('<','_')
                                .replace('>','_');  // 4 replace() still faster than regex
        if (FunctionsExt.isWindows()) id = id.replace(':','_');

        synchronized(byName) {
            UserExt u = byName.get(id);
            if(u==null) {
                UserExt tmp = new UserExt(id, idOrFullName);
                if (create || tmp.getConfigFile().exists()) {
                    byName.put(id,u=tmp);
                }
            }
            return u;
        }
    }

    /**
     * Gets the {@link User} object by its id or full name.
     */
    public static UserExt get(String idOrFullName) {
        return get(idOrFullName,true);
    }

    /**
     * Gets the {@link User} object representing the currently logged-in user, or null
     * if the current user is anonymous.
     * @since 1.172
     */
    public static UserExt current() {
        Authentication a = HudsonExt.getAuthentication();
        if(a instanceof AnonymousAuthenticationToken)
            return null;
        return get(a.getName());
    }

    private static volatile long lastScanned;

    /**
     * Gets all the users.
     */
    public static Collection<UserExt> getAll() {
        if(System.currentTimeMillis() -lastScanned>10000) {
            // occasionally scan the file system to check new users
            // whether we should do this only once at start up or not is debatable.
            // set this right away to avoid another thread from doing the same thing while we do this.
            // having two threads doing the work won't cause race condition, but it's waste of time.
            lastScanned = System.currentTimeMillis();

            File[] subdirs = getRootDir().listFiles((FileFilter)DirectoryFileFilter.INSTANCE);
            if(subdirs==null)       return Collections.emptyList(); // shall never happen

            for (File subdir : subdirs)
                if(new File(subdir,"config.xml").exists())
                    UserExt.get(subdir.getName());

            lastScanned = System.currentTimeMillis();
        }

        synchronized (byName) {
            return new ArrayList<UserExt>(byName.values());
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        // iterate over an array to be concurrency-safe
        for( UserExt u : byName.values().toArray(new UserExt[0]) )
            u.load();
    }

    /**
     * Stop gap hack. Don't use it. To be removed in the trunk.
     */
    public static void clear() {
        byName.clear();
    }

    /**
     * Returns the user name.
     */
    public String getDisplayName() {
        return getFullName();
    }

    /**
     * Gets the list of {@link Build}s that include changes by this user,
     * by the timestamp order.
     * 
     * TODO: do we need some index for this?
     */
    @WithBridgeMethods(List.class)
    public RunList getBuilds() {
        List<AbstractBuildExt> r = new ArrayList<AbstractBuildExt>();
        for (AbstractProjectExt<?,?> p : HudsonExt.getInstance().getAllItems(AbstractProjectExt.class))
            for (AbstractBuildExt<?,?> b : p.getBuilds())
                if(b.hasParticipant(this))
                    r.add(b);
        return RunList.fromRuns(r);
    }

    /**
     * Gets all the {@link AbstractProjectExt}s that this user has committed to.
     * @since 1.191
     */
    public Set<AbstractProjectExt<?,?>> getProjects() {
        Set<AbstractProjectExt<?,?>> r = new HashSet<AbstractProjectExt<?,?>>();
        for (AbstractProjectExt<?,?> p : HudsonExt.getInstance().getAllItems(AbstractProjectExt.class))
            if(p.hasParticipant(this))
                r.add(p);
        return r;
    }

    public @Override String toString() {
        return fullName;
    }

    /**
     * The file we save our configuration.
     */
    protected final XmlFile getConfigFile() {
        return new XmlFile(XSTREAM,new File(getRootDir(),id +"/config.xml"));
    }

    /**
     * Gets the directory where HudsonExt stores user information.
     */
    private static File getRootDir() {
        return new File(HudsonExt.getInstance().getRootDir(), "users");
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    /**
     * Deletes the data directory and removes this user from HudsonExt.
     *
     * @throws IOException
     *      if we fail to delete.
     */
    public synchronized void delete() throws IOException {
        synchronized (byName) {
            byName.remove(id);
            Util.deleteRecursive(new File(getRootDir(), id));
        }
    }

    /**
     * Keyed by {@link User#id}. This map is used to ensure
     * singleton-per-id semantics of {@link User} objects.
     */
    private static final Map<String,UserExt> byName = new TreeMap<String,UserExt>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Used to load/save user configuration.
     */
    private static final XStream XSTREAM = new XStream2();

    private static final Logger LOGGER = Logger.getLogger(UserExt.class.getName());

    static {
        XSTREAM.alias("user",UserExt.class);
    }

    public ACL getACL() {
        final ACL base = HudsonExt.getInstance().getAuthorizationStrategy().getACL(this);
        // always allow a non-anonymous user full control of himself.
        return new ACL() {
            public boolean hasPermission(Authentication a, Permission permission) {
                return (a.getName().equals(id) && !(a instanceof AnonymousAuthenticationToken))
                        || base.hasPermission(a, permission);
            }
        };
    }

    public void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * With ADMINISTER permission, can delete users with persisted data but can't delete self.
     */
    public boolean canDelete() {
        return hasPermission(HudsonExt.ADMINISTER) && !id.equals(HudsonExt.getAuthentication().getName())
                && new File(getRootDir(), id).exists();
    }

    public Object getDynamic(String token) {
        for (UserPropertyExt property: getProperties().values()) {
            if (property instanceof Action) {
                Action a= (Action) property;
            if(a.getUrlName().equals(token) || a.getUrlName().equals('/'+token))
                return a;
            }
        }
        return null;
    }
}

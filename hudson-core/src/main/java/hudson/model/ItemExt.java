/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

import java.io.IOException;
import java.util.Collection;

import hudson.search.SearchableModelObject;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.AccessControlled;

/**
 * Basic configuration unit in Hudson.
 *
 * <p>
 * Every {@link ItemExt} is hosted in an {@link ItemGroup} called "parent",
 * and some {@link ItemExt}s are {@link ItemGroup}s. This form a tree
 * structure, which is rooted at {@link Hudson}.
 *
 * <p>
 * Unlike file systems, where a file can be moved from one directory
 * to another, {@link ItemExt} inherently belongs to a single {@link ItemGroup}
 * and that relationship will not change.
 * Think of
 * <a href="http://images.google.com/images?q=Windows%20device%20manager">Windows device manager</a>
 * &mdash; an HDD always show up under 'Disk drives' and it can never be moved to another parent.
 *
 * Similarly, {@link ItemGroup} is not a generic container. Each subclass
 * of {@link ItemGroup} can usually only host a certain limited kinds of
 * {@link ItemExt}s.
 *
 * <p>
 * {@link ItemExt}s have unique {@link #getName() name}s that distinguish themselves
 * among their siblings uniquely. The names can be combined by '/' to form an
 * item full name, which uniquely identifies an {@link ItemExt} inside the whole {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 * @see Items
 */
public interface ItemExt extends PersistenceRoot, SearchableModelObject, AccessControlled {
    /**
     * Gets the parent that contains this item.
     */
    ItemGroup<? extends ItemExt> getParent();

    /**
     * Gets all the jobs that this {@link ItemExt} contains as descendants.
     */
    abstract Collection<? extends JobExt> getAllJobs();

    /**
     * Gets the name of the item.
     *
     * <p>
     * The name must be unique among other {@link ItemExt}s that belong
     * to the same parent.
     *
     * <p>
     * This name is also used for directory name, so it cannot contain
     * any character that's not allowed on the file system.
     *
     * @see #getFullName() 
     */
    String getName();

    /**
     * Gets the full name of this item, like "abc/def/ghi".
     *
     * <p>
     * Full name consists of {@link #getName() name}s of {@link ItemExt}s
     * that lead from the root {@link Hudson} to this {@link ItemExt},
     * separated by '/'. This is the unique name that identifies this
     * {@link ItemExt} inside the whole {@link Hudson}.
     *
     * @see Hudson#getItemByFullName(String,Class)
     */
    String getFullName();

    /**
     * Gets the human readable short name of this item.
     *
     * <p>
     * This method should try to return a short concise human
     * readable string that describes this item.
     * The string need not be unique.
     *
     * <p>
     * The returned string should not include the display names
     * of {@link #getParent() ancestor items}.
     */
    String getDisplayName();

    /**
     * Works like {@link #getDisplayName()} but return
     * the full path that includes all the display names
     * of the ancestors.
     */
    String getFullDisplayName();

    /**
     * Called right after when a {@link ItemExt} is loaded from disk.
     * This is an opporunity to do a post load processing.
     *
     * @param name
     *      Name of the directory (not a path --- just the name portion) from
     *      which the configuration was loaded. This usually becomes the
     *      {@link #getName() name} of this item.
     */
    void onLoad(ItemGroup<? extends ItemExt> parent, String name) throws IOException;

    /**
     * When a {@link ItemExt} is copied from existing one,
     * the files are first copied on the file system,
     * then it will be loaded, then this method will be invoked
     * to perform any implementation-specific work.
     */
    void onCopiedFrom(ItemExt src);

    /**
     * When an item is created from scratch (instead of copied),
     * this method will be invoked. Used as the post-construction initialization.
      */
    void onCreatedFromScratch();

    /**
     * Save the settings to a file.
     *
     * Use {@link Items#getConfigFile(ItemExt)}
     * or {@link AbstractItem#getConfigFile()} to obtain the file
     * to save the data.
     */
    public void save() throws IOException;

    /**
     * Deletes this item.
     */
    public void delete() throws IOException, InterruptedException;

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(ItemExt.class,Messages._Item_Permissions_Title());
    public static final Permission CREATE = new Permission(PERMISSIONS, "Create", null, Permission.CREATE);
    public static final Permission DELETE = new Permission(PERMISSIONS, "Delete", null, Permission.DELETE);
    public static final Permission CONFIGURE = new Permission(PERMISSIONS, "Configure", null, Permission.CONFIGURE);
    public static final Permission READ = new Permission(PERMISSIONS, "Read", null, Permission.READ);
    public static final Permission EXTENDED_READ = new Permission(PERMISSIONS,"ExtendedRead", Messages._AbstractProject_ExtendedReadPermission_Description(), CONFIGURE, Boolean.getBoolean("hudson.security.ExtendedReadPermission"));
    public static final Permission BUILD = new Permission(PERMISSIONS, "Build", Messages._AbstractProject_BuildPermission_Description(),  Permission.UPDATE);
    public static final Permission WORKSPACE = new Permission(PERMISSIONS, "Workspace", Messages._AbstractProject_WorkspacePermission_Description(), Permission.READ);
}

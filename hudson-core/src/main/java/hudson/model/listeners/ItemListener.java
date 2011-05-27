/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model.listeners;

import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.Extension;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;

/**
 * Receives notifications about CRUD operations of {@link ItemExt}.
 *
 * @since 1.74
 * @author Kohsuke Kawaguchi
 */
public class ItemListener implements ExtensionPoint {
    /**
     * Called after a new job is created and added to {@link HudsonExt},
     * before the initial configuration page is provided.
     * <p>
     * This is useful for changing the default initial configuration of newly created jobs.
     * For example, you can enable/add builders, etc.
     */
    public void onCreated(ItemExt item) {
    }

    /**
     * Called after a new job is created by copying from an existing job.
     *
     * For backward compatibility, the default implementation of this method calls {@link #onCreated(ItemExt)}.
     * If you choose to handle this method, think about whether you want to call super.onCopied or not.
     *
     *
     * @param src
     *      The source item that the new one was copied from. Never null.
     * @param  item
     *      The newly created item. Never null.
     *
     * @since 1.325
     *      Before this version, a copy triggered {@link #onCreated(ItemExt)}.
     */
    public void onCopied(ItemExt src, ItemExt item) {
        onCreated(item);
    }

    /**
     * Called after all the jobs are loaded from disk into {@link HudsonExt}
     * object.
     */
    public void onLoaded() {
    }

    /**
     * Called right before a job is going to be deleted.
     *
     * At this point the data files of the job is already gone.
     */
    public void onDeleted(ItemExt item) {
    }

    /**
     * Called after a job is renamed.
     *
     * @param item
     *      The job being renamed.
     * @param oldName
     *      The old name of the job.
     * @param newName
     *      The new name of the job. Same as {@link ItemExt#getName()}.
     * @since 1.146
     */
    public void onRenamed(ItemExt item, String oldName, String newName) {
    }

    /**
     * Registers this instance to HudsonExt and start getting notifications.
     *
     * @deprecated as of 1.286
     *      put {@link Extension} on your class to have it auto-registered.
     */
    public void register() {
        all().add(this);
    }

    /**
     * All the registered {@link ItemListener}s.
     */
    public static ExtensionList<ItemListener> all() {
        return HudsonExt.getInstance().getExtensionList(ItemListener.class);
    }

    public static void fireOnCopied(ItemExt src, ItemExt result) {
        for (ItemListener l : all())
            l.onCopied(src,result);
    }

    public static void fireOnCreated(ItemExt item) {
        for (ItemListener l : all())
            l.onCreated(item);
    }
}

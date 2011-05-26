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
package hudson;

import hudson.model.Hudson;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.File;

import com.thoughtworks.xstream.XStream;

/**
 * Base class of Hudson plugin.
 *
 * <p>
 * A plugin needs to derive from this class.
 *
 * <p>
 * One instance of a plugin is created by Hudson, and used as the entry point
 * to plugin functionality.
 *
 * <p>
 * A plugin is bound to URL space of Hudson as <tt>${rootURL}/plugin/foo/</tt>,
 * where "foo" is taken from your plugin name "foo.hpi". All your web resources
 * in src/main/webapp are visible from this URL, and you can also define Jelly
 * views against your PluginExt class, and those are visible in this URL, too.
 *
 * <p>
 * {@link PluginExt} can have an optional <tt>config.jelly</tt> page. If present,
 * it will become a part of the system configuration page (http://server/hudson/configure).
 * This is convenient for exposing/maintaining configuration that doesn't
 * fit any {@link Descriptor}s.
 *
 * <p>
 * Up until Hudson 1.150 or something, subclasses of {@link PluginExt} required
 * <tt>@plugin</tt> javadoc annotation, but that is no longer a requirement.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.42
 */
public abstract class PluginExt implements Saveable {

    /**
     * Set by the {@link PluginManager}.
     * This points to the {@link PluginWrapperExt} that wraps
     * this {@link PluginExt} object.
     */
    /*package*/ transient PluginWrapperExt wrapper;

    /**
     * Called when a plugin is loaded to make the {@link ServletContext} object available to a plugin.
     * This object allows plugins to talk to the surrounding environment.
     *
     * <p>
     * The default implementation is no-op.
     *
     * @param context
     *      Always non-null.
     *
     * @since 1.42
     */
    public void setServletContext(ServletContext context) {
    }

    /**
     * Called to allow plugins to initialize themselves.
     *
     * <p>
     * This method is called after {@link #setServletContext(ServletContext)} is invoked.
     * You can also use {@link Hudson#getInstance()} to access the singleton hudson instance,
     * although the plugin start up happens relatively early in the initialization
     * stage and not all the data are loaded in Hudson.
     *
     * <p>
     * If a plugin wants to run an initialization step after all plugins and extension points
     * are registered, a good place to do that is {@link #postInitialize()}.
     * If a plugin wants to run an initialization step after all the jobs are loaded,
     * {@link ItemListener#onLoaded()} is a good place.
     *
     * @throws Exception
     *      any exception thrown by the plugin during the initialization will disable plugin.
     *
     * @since 1.42
     * @see ExtensionPoint
     * @see #postInitialize()
     */
    public void start() throws Exception {
    }

    /**
     * Called after {@link #start()} is called for all the plugins.
     *
     * @throws Exception
     *      any exception thrown by the plugin during the initialization will disable plugin.
     */
    public void postInitialize() throws Exception {}

    /**
     * Called to orderly shut down Hudson.
     *
     * <p>
     * This is a good opportunity to clean up resources that plugin started.
     * This method will not be invoked if the {@link #start()} failed abnormally.
     *
     * @throws Exception
     *      if any exception is thrown, it is simply recorded and shut-down of other
     *      plugins continue. This is primarily just a convenience feature, so that
     *      each plugin author doesn't have to worry about catching an exception and
     *      recording it.
     *
     * @since 1.42
     */
    public void stop() throws Exception {
    }

//
// Convenience methods for those plugins that persist configuration
//
    /**
     * Loads serializable fields of this instance from the persisted storage.
     *
     * <p>
     * If there was no previously persisted state, this method is no-op.
     *
     * @since 1.245
     */
    protected void load() throws IOException {
        XmlFile xml = getConfigXml();
        if(xml.exists())
            xml.unmarshal(this);
    }

    /**
     * Saves serializable fields of this instance to the persisted storage.
     *
     * @since 1.245
     */
    public void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getConfigXml().write(this);
        SaveableListener.fireOnChange(this, getConfigXml());
    }

    /**
     * Controls the file where {@link #load()} and {@link #save()}
     * persists data.
     *
     * This method can be also overriden if the plugin wants to
     * use a custom {@link XStream} instance to persist data.
     *
     * @since 1.245
     */
    protected XmlFile getConfigXml() {
        return new XmlFile(Hudson.XSTREAM,
                new File(Hudson.getInstance().getRootDir(),wrapper.getShortName()+".xml"));
    }


    /**
     * Dummy instance of {@link PluginExt} to be used when a plugin didn't
     * supply one on its own.
     *
     * @since 1.321
     */
    public static final class DummyImpl extends PluginExt {}
}

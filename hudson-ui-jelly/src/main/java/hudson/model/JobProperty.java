/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Launcher;
import hudson.PluginExt;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;


import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extensible property of {@link JobExt}.
 *
 * <p>
 * {@link PluginExt}s can extend this to define custom properties
 * for {@link JobExt}s. {@link JobPropertyExt}s show up in the user
 * configuration screen, and they are persisted with the job object.
 *
 * <p>
 * Configuration screen should be defined in <tt>config.jelly</tt>.
 * Within this page, the {@link JobPropertyExt} instance is available
 * as <tt>instance</tt> variable (while <tt>it</tt> refers to {@link JobExt}.
 *
 * <p>
 * Starting 1.150, {@link JobPropertyExt} implements {@link BuildStep},
 * meaning it gets the same hook as {@link Publisher} and {@link Builder}.
 * The primary intention of this mechanism is so that {@link JobPropertyExt}s
 * can add actions to the new build. The {@link #perform(AbstractBuildExt, Launcher, BuildListener)}
 * and {@link #prebuild(AbstractBuildExt, BuildListener)} are invoked after those
 * of {@link Publisher}s.
 *
 * @param <J>
 *      When you restrict your job property to be only applicable to a certain
 *      subtype of {@link JobExt}, you can use this type parameter to improve
 *      the type signature of this class. See {@link JobPropertyDescriptorExt#isApplicable(Class)}. 
 *
 * @author Kohsuke Kawaguchi
 * @see JobPropertyDescriptorExt
 * @since 1.72
 */
@ExportedBean
public abstract class JobProperty<J extends JobExt<?,?>>  extends JobPropertyExt {
     
}

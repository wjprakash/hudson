/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Michael B. Donohue, Seiji Sogabe
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hudson.console.HyperlinkNote;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.util.XStream2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

/**
 * CauseExt object base class.  This class hierarchy is used to keep track of why 
 * a given build was started. This object encapsulates the UI rendering of the cause,
 * as well as providing more useful information in respective subypes.
 *
 * The CauseExt object is connected to a build via the {@link CauseAction} object.
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>description.jelly
 * <dd>Renders the cause to HTML. By default, it puts the short description.
 * </dl>
 *
 * @author Michael Donohue
 * @see Run#getCauses()
 * @see Queue.Item#getCauses()
 */
@ExportedBean
public abstract class Cause extends CauseExt{
    /**
     * One-line human-readable text of the cause.
     *
     * <p>
     * By default, this method is used to render HTML as well.
     */
    @Exported(visibility=3)
    @Override
    public abstract String getShortDescription();

     
    /**
     * A build is triggered by the completion of another build (AKA upstream build.)
     */
    public static class UpstreamCause extends CauseExt.UpstreamCause {
         
        
        public UpstreamCause(Run<?, ?> up) {
             super(up);
        }

        
        @Exported(visibility=3)
        @Override
        public String getUpstreamProject() {
            return super.getUpstreamProject();
        }

        @Exported(visibility=3)
        @Override
        public int getUpstreamBuild() {
            return super.getUpstreamBuild();
        }

        @Exported(visibility=3)
        @Override
        public String getUpstreamUrl() {
            return super.getUpstreamUrl();
        }
    }

    /**
     * A build is started by an user action.
     */
    public static class UserCause extends CauseExt.UserCause {
         
        @Exported(visibility=3)
        @Override
        public String getUserName() {
           return super.getUserName();
        }
    }
}

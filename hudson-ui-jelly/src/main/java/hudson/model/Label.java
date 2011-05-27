/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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


import hudson.slaves.Cloud;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.List;
import java.util.Set;


/**
 * Group of {@link Node}s.
 * 
 * @author Kohsuke Kawaguchi
 * @see HudsonExt#getLabels()
 * @see HudsonExt#getLabel(String) 
 */
@ExportedBean
public abstract class Label extends LabelExt {
   
    @Exported
    public transient final LoadStatistics loadStatistics = null;

    public Label(String name) {
        super(name);
    }

    /**
     * Alias for {@link #getDisplayName()}.
     */
    @Exported
    @Override
    public String getName() {
        return super.getName();
    }


    /**
     * Relative URL from the context path, that ends with '/'.
     */
    public String getUrl() {
        return "label/"+name+'/';
    }

    public String getSearchUrl() {
        return getUrl();
    }

    

    /**
     * Gets all {@link Node}s that belong to this label.
     */
    @Exported
    @Override
    public Set<Node> getNodes() {
        return super.getNodes();
    }

    /**
     * Gets all {@link Cloud}s that can launch for this label.
     */
    @Exported
    @Override
    public Set<Cloud> getClouds() {
         return super.getClouds();
    }
    
    

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    @Exported
    @Override
    public int getTotalExecutors() {
         return super.getTotalExecutors();
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    @Exported
    @Override
    public int getBusyExecutors() {
        return super.getBusyExecutors();
    }

    /**
     * Number of idle {@link Executor}s that can start working immediately.
     */
    @Exported
    @Override
    public int getIdleExecutors() {
         return super.getIdleExecutors();
    }

    /**
     * Returns true if all the nodes of this label is offline.
     */
    @Exported
    @Override
    public boolean isOffline() {
         return super.isOffline();
    }

    /**
     * Returns a human readable text that explains this label.
     */
    @Exported
    @Override
    public String getDescription() {
        return super.getDescription();
    }

    

    /**
     * Returns projects that are tied on this node.
     */
    @Exported
    @Override
    public List<AbstractProjectExt> getTiedJobs() {
        return super.getTiedJobs();
    }

    /**
     * Expose this object to the remote API.
     */
    public Api getApi() {
        return new Api(this);
    }

     
}

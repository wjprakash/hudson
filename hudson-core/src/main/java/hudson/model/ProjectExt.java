/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans, Stephen Connolly, Tom Huybrechts
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

import hudson.Util;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.tasks.Maven;
import hudson.tasks.Maven.ProjectWithMaven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buildable software project.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ProjectExt<P extends ProjectExt<P,B>,B extends Build<P,B>>
    extends AbstractProjectExt<P,B> implements SCMedItem, Saveable, ProjectWithMaven, BuildableItemWithBuildWrappers {

    /**
     * List of active {@link Builder}s configured for this project.
     */
    protected DescribableList<Builder,DescriptorExt<Builder>> builders =
            new DescribableList<Builder,DescriptorExt<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    protected DescribableList<Publisher,DescriptorExt<Publisher>> publishers =
            new DescribableList<Publisher,DescriptorExt<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     */
    protected DescribableList<BuildWrapper,DescriptorExt<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,DescriptorExt<BuildWrapper>>(this);

    /**
     * Creates a new project.
     */
    public ProjectExt(ItemGroup parent,String name) {
        super(parent,name);
    }

    @Override
    public void onLoad(ItemGroup<? extends ItemExt> parent, String name) throws IOException {
        super.onLoad(parent, name);

        if (buildWrappers==null) {
            // it didn't exist in < 1.64
            buildWrappers = new DescribableList<BuildWrapper, DescriptorExt<BuildWrapper>>(this);
            OldDataMonitorExt.report(this, "1.64");
        }
        builders.setOwner(this);
        publishers.setOwner(this);
        buildWrappers.setOwner(this);
    }

    public AbstractProjectExt<?, ?> asProject() {
        return this;
    }

    public List<Builder> getBuilders() {
        return builders.toList();
    }

    public Map<DescriptorExt<Publisher>,Publisher> getPublishers() {
        return publishers.toMap();
    }

    public DescribableList<Builder,DescriptorExt<Builder>> getBuildersList() {
        return builders;
    }
    
    public DescribableList<Publisher,DescriptorExt<Publisher>> getPublishersList() {
        return publishers;
    }

    public Map<DescriptorExt<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return buildWrappers.toMap();
    }

    public DescribableList<BuildWrapper, DescriptorExt<BuildWrapper>> getBuildWrappersList() {
        return buildWrappers;
    }

    @Override
    protected Set<ResourceActivity> getResourceActivities() {
        final Set<ResourceActivity> activities = new HashSet<ResourceActivity>();

        activities.addAll(super.getResourceActivities());
        activities.addAll(Util.filter(builders,ResourceActivity.class));
        activities.addAll(Util.filter(publishers,ResourceActivity.class));
        activities.addAll(Util.filter(buildWrappers,ResourceActivity.class));

        return activities;
    }

    /**
     * Adds a new {@link BuildStep} to this {@link ProjectExt} and saves the configuration.
     *
     * @deprecated as of 1.290
     *      Use {@code getPublishersList().add(x)}
     */
    public void addPublisher(Publisher buildStep) throws IOException {
        publishers.add(buildStep);
    }

    /**
     * Removes a publisher from this project, if it's active.
     *
     * @deprecated as of 1.290
     *      Use {@code getPublishersList().remove(x)}
     */
    public void removePublisher(DescriptorExt<Publisher> descriptor) throws IOException {
        publishers.remove(descriptor);
    }

    public Publisher getPublisher(DescriptorExt<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        builders.buildDependencyGraph(this,graph);
        buildWrappers.buildDependencyGraph(this,graph);
    }

    @Override
    public boolean isFingerprintConfigured() {
        return getPublishersList().get(Fingerprinter.class)!=null;
    }

    public MavenInstallation inferMavenInstallation() {
        Maven m = getBuildersList().get(Maven.class);
        if (m!=null)    return m.getMaven();
        return null;
    }


    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        for (BuildStep step : getBuildersList())
            r.addAll(step.getProjectActions(this));
        for (BuildStep step : getPublishersList())
            r.addAll(step.getProjectActions(this));
        for (BuildWrapper step : getBuildWrappers().values())
            r.addAll(step.getProjectActions(this));
        for (Trigger trigger : getTriggers().values())
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * @deprecated since 2006-11-05.
     *      Left for legacy config file compatibility
     */
    @Deprecated
    private transient String slave;

    private Object readResolve() {
        if (slave != null) OldDataMonitorExt.report(this, "1.60");
        return this;
    }
}

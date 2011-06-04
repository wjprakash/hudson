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
package hudson.matrix;

import hudson.UtilExt;
import hudson.util.DescribableListExt;
import hudson.model.AbstractBuildExt;
import hudson.model.CauseExt;
import hudson.model.CauseActionExt;
import hudson.model.DependencyGraph;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.ItemGroup;
import hudson.model.JDKExt;
import hudson.model.LabelExt;
import hudson.model.ParametersActionExt;
import hudson.model.ProjectExt;
import hudson.model.SCMedItem;
import hudson.model.QueueExt.NonBlockingTask;
import hudson.model.CauseExt.LegacyCodeCause;
import hudson.scm.SCMExt;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.LogRotatorExt;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * One configuration of {@link MatrixProjectExt}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfiguration extends ProjectExt<MatrixConfiguration,MatrixRunExt> implements SCMedItem, NonBlockingTask {
    /**
     * The actual value combination.
     */
    private transient /*final*/ Combination combination;

    /**
     * Hash value of {@link #combination}. Cached for efficiency.
     */
    private transient String digestName;

    public MatrixConfiguration(MatrixProjectExt parent, Combination c) {
        super(parent,c.toString());
        setCombination(c);
    }

    @Override
    public void onLoad(ItemGroup<? extends ItemExt> parent, String name) throws IOException {
        // directory name is not a name for us --- it's taken from the combination name
        super.onLoad(parent, combination.toString());
    }

    /**
     * Used during loading to set the combination back.
     */
    /*package*/ void setCombination(Combination c) {
        this.combination = c;
        this.digestName = c.digest().substring(0,8);
    }

    /**
     * Build numbers are always synchronized with the parent.
     *
     * <p>
     * Computing this is bit tricky. Several considerations:
     *
     * <ol>
     * <li>A new configuration build #N is started while the parent build #N is building,
     *     and when that happens we want to return N.
     * <li>But the configuration build #N is done before the parent build #N finishes,
     *     and when that happens we want to return N+1 because that's going to be the next one.
     * <li>Configuration builds might skip some numbers if the parent build is aborted
     *     before this configuration is built.
     * <li>If nothing is building right now and the last build of the parent is #N,
     *     then we want to return N+1.
     * </ol>
     */
    @Override
    public int getNextBuildNumber() {
        AbstractBuildExt lb = getParent().getLastBuild();
        if(lb==null)    return 0;
        

        int n=lb.getNumber();
        if(!lb.isBuilding())    n++;

        lb = getLastBuild();
        if(lb!=null)
            n = Math.max(n,lb.getNumber()+1);

        return n;
    }

    @Override
    public int assignBuildNumber() throws IOException {
        int nb = getNextBuildNumber();
        MatrixRunExt r = getLastBuild();
        if(r!=null && r.getNumber()>=nb) // make sure we don't schedule the same build twice
            throw new IllegalStateException("Build #"+nb+" is already completed");
        return nb;
    }

    @Override
    public String getDisplayName() {
        return combination.toCompactString(getParent().getAxes());
    }

    @Override
    public MatrixProjectExt getParent() {
        return (MatrixProjectExt)super.getParent();
    }

    /**
     * Get the actual combination of the axes values for this {@link MatrixConfiguration}
     */
    public Combination getCombination() {
        return combination;
    }

    /**
     * Since {@link MatrixConfiguration} is always invoked from {@link MatrixRunExt}
     * once and just once, there's no point in having a quiet period.
     */
    @Override
    public int getQuietPeriod() {
        return 0;
    }

    /**
     * Inherit the value from the parent.
     */
    @Override
    public int getScmCheckoutRetryCount() {
        return getParent().getScmCheckoutRetryCount();
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    protected Class<MatrixRunExt> getBuildClass() {
        return MatrixRunExt.class;
    }

    @Override
    protected MatrixRunExt newBuild() throws IOException {
        // for every MatrixRunExt there should be a parent MatrixBuildExt
        MatrixBuildExt lb = getParent().getLastBuild();
        MatrixRunExt lastBuild = new MatrixRunExt(this, lb.getTimestamp());
        lastBuild.number = lb.getNumber();

        builds.put(lastBuild);
        return lastBuild;
    }

    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
    }

    @Override
    public MatrixConfiguration asProject() {
        return this;
    }

    @Override
    public LabelExt getAssignedLabel() {
        // combine all the label axes by &&.
        String expr = UtilExt.join(combination.values(getParent().getAxes().subList(LabelAxisExt.class)), "&&");
        return HudsonExt.getInstance().getLabel(UtilExt.fixEmpty(expr));
    }

    @Override
    public String getPronoun() {
        return Messages.MatrixConfiguration_Pronoun();
    }

    @Override
    public JDKExt getJDK() {
        return HudsonExt.getInstance().getJDK(combination.get("jdk"));
    }

//
// inherit build setting from the parent project
//
    @Override
    public List<Builder> getBuilders() {
        return getParent().getBuilders();
    }

    @Override
    public Map<DescriptorExt<Publisher>, Publisher> getPublishers() {
        return getParent().getPublishers();
    }

    @Override
    public DescribableListExt<Builder, DescriptorExt<Builder>> getBuildersList() {
        return getParent().getBuildersList();
    }

    @Override
    public DescribableListExt<Publisher, DescriptorExt<Publisher>> getPublishersList() {
        return getParent().getPublishersList();
    }

    @Override
    public Map<DescriptorExt<BuildWrapper>, BuildWrapper> getBuildWrappers() {
        return getParent().getBuildWrappers();
    }

    @Override
    public DescribableListExt<BuildWrapper, DescriptorExt<BuildWrapper>> getBuildWrappersList() {
        return getParent().getBuildWrappersList();
    }

    @Override
    public Publisher getPublisher(DescriptorExt<Publisher> descriptor) {
        return getParent().getPublisher(descriptor);
    }

    @Override
    public LogRotatorExt getLogRotator() {
        LogRotatorExt lr = getParent().getLogRotator();
        return new LinkedLogRotator(lr != null ? lr.getArtifactDaysToKeep() : -1,
                                    lr != null ? lr.getArtifactNumToKeep() : -1);
    }

    @Override
    public SCMExt getScm() {
        return getParent().getScm();
    }

    /*package*/ String getDigestName() {
        return digestName;
    }

    /**
     * JDKExt cannot be set on {@link MatrixConfiguration} because
     * it's controlled by {@link MatrixProjectExt}.
     * @deprecated
     *      Not supported.
     */
    @Override
    public void setJDK(JDKExt jdk) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      Value is controlled by {@link MatrixProjectExt}.
     */
    @Override
    public void setLogRotator(LogRotatorExt logRotator) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if this configuration is a configuration
     * currently in use today (as opposed to the ones that are
     * there only to keep the past record.) 
     *
     * @see MatrixProjectExt#getActiveConfigurations()
     */
    public boolean isActiveConfiguration() {
        return getParent().getActiveConfigurations().contains(this);
    }

    /**
     * On Cygwin, path names cannot be longer than 256 chars.
     * See http://cygwin.com/ml/cygwin/2005-04/msg00395.html and
     * http://www.nabble.com/Windows-Filename-too-long-errors-t3161089.html for
     * the background of this issue. Setting this flag to true would
     * cause HudsonExt to use cryptic but short path name, giving more room for
     * jobs to use longer path names.
     */
    public static boolean useShortWorkspaceName = Boolean.getBoolean(MatrixConfiguration.class.getName()+".useShortWorkspaceName");

	/**
	 * @deprecated
	 *    Use {@link #scheduleBuild(ParametersAction, CauseExt)}.  Since 1.283
	 */
    public boolean scheduleBuild(ParametersActionExt parameters) {
    	return scheduleBuild(parameters, new LegacyCodeCause());
    }

    /**
     *
     * @param parameters
     *      Can be null.
     */
    public boolean scheduleBuild(ParametersActionExt parameters, CauseExt c) {
        return HudsonExt.getInstance().getQueue().schedule(this, getQuietPeriod(), parameters, new CauseActionExt(c))!=null;
    }

    public String getUrl() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans, Red Hat, Inc., id:cactusman
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

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.UtilExt;
import hudson.XmlFile;
import hudson.model.AbstractProjectExt;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.DependencyGraph;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDKExt;
import hudson.model.JobExt;
import hudson.model.LabelExt;
import hudson.model.NodeExt;
import hudson.model.QueueExt.FlyweightTask;
import hudson.model.ResourceController;
import hudson.model.ResultExt;
import hudson.model.SCMedItem;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.TokenList;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * {@link JobExt} that allows you to run multiple different configurations
 * from a single setting.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectExt extends AbstractProjectExt<MatrixProjectExt,MatrixBuildExt> implements TopLevelItem, SCMedItem, ItemGroup<MatrixConfiguration>, Saveable, FlyweightTask, BuildableItemWithBuildWrappers {
    /**
     * Configuration axes.
     */
    protected volatile AxisList axes = new AxisList();
    
    /**
     * The filter that is applied to combinations. It is a Groovy if condition.
     * This can be null, which means "true".
     *
     * @see #getCombinationFilter()
     */
    protected volatile String combinationFilter;

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
     * All {@link MatrixConfiguration}s, keyed by their {@link MatrixConfiguration#getName() names}.
     */
    protected transient /*final*/ Map<Combination,MatrixConfiguration> configurations = new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();

    /**
     * @see #getActiveConfigurations()
     */
    @CopyOnWrite
    private transient /*final*/ Set<MatrixConfiguration> activeConfigurations = new LinkedHashSet<MatrixConfiguration>();

    protected boolean runSequentially;
    
    /**
     * Filter to select a number of combinations to build first
     */
    protected String touchStoneCombinationFilter;
    
    /**
     * Required result on the touchstone combinations, in order to
     * continue with the rest
     */
    protected ResultExt touchStoneResultCondition;

    /**
     * See {@link #setCustomWorkspace(String)}.
     */
    protected String customWorkspace;
    
    public MatrixProjectExt(String name) {
        this(HudsonExt.getInstance(), name);
    }

    public MatrixProjectExt(ItemGroup parent, String name) {
        super(parent, name);
    }

    public AxisList getAxes() {
        return axes;
    }

    /**
     * Reconfigures axes.
     */
    public void setAxes(AxisList axes) throws IOException {
        this.axes = new AxisList(axes);
        rebuildConfigurations();
        save();
    }

    /**
     * If true, {@link MatrixRun}s are run sequentially, instead of running in parallel.
     *
     * TODO: this should be subsumed by {@link ResourceController}.
     */
    public boolean isRunSequentially() {
        return runSequentially;
    }

    public void setRunSequentially(boolean runSequentially) throws IOException {
        this.runSequentially = runSequentially;
        save();
    }

    /**
     * Sets the combination filter.
     *
     * @param combinationFilter the combinationFilter to set
     */
    public void setCombinationFilter(String combinationFilter) throws IOException {
        this.combinationFilter = combinationFilter;
        rebuildConfigurations();
        save();
    }

    /**
     * Obtains the combination filter, used to trim down the size of the matrix.
     *
     * <p>
     * By default, a {@link MatrixConfiguration} is created for every possible combination of axes exhaustively.
     * But by specifying a Groovy expression as a combination filter, one can trim down the # of combinations built.
     *
     * <p>
     * Namely, this expression is evaluated for each axis value combination, and only when it evaluates to true,
     * a corresponding {@link MatrixConfiguration} will be created and built. 
     *
     * @return can be null.
     * @since 1.279
     */
    public String getCombinationFilter() {
        return combinationFilter;
    }

    public String getTouchStoneCombinationFilter() {
        return touchStoneCombinationFilter;
    }

    public void setTouchStoneCombinationFilter(
            String touchStoneCombinationFilter) {
        this.touchStoneCombinationFilter = touchStoneCombinationFilter;
    }

    public ResultExt getTouchStoneResultCondition() {
        return touchStoneResultCondition;
    }

    public void setTouchStoneResultCondition(ResultExt touchStoneResultCondition) {
        this.touchStoneResultCondition = touchStoneResultCondition;
    }

    public String getCustomWorkspace() {
      return customWorkspace;
    }
    
    /**
     * User-specified workspace directory, or null if it's up to HudsonExt.
     *
     * <p>
     * Normally a matrix project uses the workspace location assigned by its parent container,
     * but sometimes people have builds that have hard-coded paths.
     *
     * <p>
     * This is not {@link File} because it may have to hold a path representation on another OS.
     *
     * <p>
     * If this path is relative, it's resolved against {@link NodeExt#getRootPath()} on the node where this workspace
     * is prepared.
     */
    public void setCustomWorkspace(String customWorkspace) throws IOException {
        this.customWorkspace= customWorkspace;
    }
    
    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        for (BuildStep step : builders)
            r.addAll(step.getProjectActions(this));
        for (BuildStep step : publishers)
            r.addAll(step.getProjectActions(this));
        for (BuildWrapper step : buildWrappers)
            r.addAll(step.getProjectActions(this));
        for (Trigger trigger : triggers)
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * Gets the subset of {@link AxisList} that are not system axes.
     *
     * @deprecated as of 1.373
     *      System vs user difference are generalized into extension point.
     */
    public List<AxisExt> getUserAxes() {
        List<AxisExt> r = new ArrayList<AxisExt>();
        for (AxisExt a : axes)
            if(!a.isSystem())
                r.add(a);
        return r;
    }

    public Layouter<MatrixConfiguration> getLayouter() {
        return new Layouter<MatrixConfiguration>(axes) {
            protected MatrixConfiguration getT(Combination c) {
                return getItem(c);
            }
        };
    }

    @Override
    public void onLoad(ItemGroup<? extends ItemExt> parent, String name) throws IOException {
        super.onLoad(parent,name);
        Collections.sort(axes); // perhaps the file was edited on disk and the sort order might have been broken
        builders.setOwner(this);
        publishers.setOwner(this);
        buildWrappers.setOwner(this);
        rebuildConfigurations();
    }

    @Override
    public void logRotate() throws IOException, InterruptedException {
        super.logRotate();
        // perform the log rotation of inactive configurations to make sure
        // their logs get eventually discarded 
        for (MatrixConfiguration config : configurations.values()) {
            if(!config.isActiveConfiguration())
                config.logRotate();
        }
    }

    /**
     * Recursively search for configuration and put them to the map
     *
     * <p>
     * The directory structure would be <tt>axis-a/b/axis-c/d/axis-e/f</tt> for
     * combination [a=b,c=d,e=f]. Note that two combinations [a=b,c=d] and [a=b,c=d,e=f]
     * can both co-exist (where one is an archived record and the other is live, for example)
     * so search needs to be thorough.
     *
     * @param dir
     *      Directory to be searched.
     * @param result
     *      Receives the loaded {@link MatrixConfiguration}s.
     * @param combination
     *      Combination of key/values discovered so far while traversing the directories.
     *      Read-only.
     */
    private void loadConfigurations( File dir, CopyOnWriteMap.Tree<Combination,MatrixConfiguration> result, Map<String,String> combination ) {
        File[] axisDirs = dir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory() && child.getName().startsWith("axis-");
            }
        });
        if(axisDirs==null)      return;

        for (File subdir : axisDirs) {
            String axis = subdir.getName().substring(5);    // axis name

            File[] valuesDir = subdir.listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory();
                }
            });
            if(valuesDir==null) continue;   // no values here

            for (File v : valuesDir) {
                Map<String,String> c = new HashMap<String, String>(combination);
                c.put(axis, TokenList.decode(v.getName()));

                try {
                    XmlFile config = Items.getConfigFile(v);
                    if(config.exists()) {
                        Combination comb = new Combination(c);
                        // if we already have this in memory, just use it.
                        // otherwise load it
                        MatrixConfiguration item=null;
                        if(this.configurations!=null)
                            item = this.configurations.get(comb);
                        if(item==null) {
                            item = (MatrixConfiguration) config.read();
                            item.setCombination(comb);
                            item.onLoad(this, v.getName());
                        }
                        result.put(item.getCombination(), item);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load matrix configuration "+v,e);
                }
                loadConfigurations(v,result,c);
            }
        }
    }

    /**
     * Rebuilds the {@link #configurations} list and {@link #activeConfigurations}.
     */
    protected void rebuildConfigurations() throws IOException {
        {
            // backward compatibility check to see if there's any data in the old structure
            // if so, bring them to the newer structure.
            File[] oldDirs = getConfigurationsDir().listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory() && !child.getName().startsWith("axis-");
                }
            });
            if(oldDirs!=null) {
                // rename the old directory to the new one
                for (File dir : oldDirs) {
                    try {
                        Combination c = Combination.fromString(dir.getName());
                        dir.renameTo(getRootDirFor(c));
                    } catch (IllegalArgumentException e) {
                        // it's not a configuration dir. Just ignore.
                    }
                }
            }
        }

        CopyOnWriteMap.Tree<Combination,MatrixConfiguration> configurations =
            new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();
        loadConfigurations(getConfigurationsDir(),configurations,Collections.<String,String>emptyMap());
        this.configurations = configurations;

        // find all active configurations
        Set<MatrixConfiguration> active = new LinkedHashSet<MatrixConfiguration>();
        for (Combination c : axes.list()) {
            if(c.evalGroovyExpression(axes,combinationFilter)) {
        		LOGGER.fine("Adding configuration: " + c);
	            MatrixConfiguration config = configurations.get(c);
	            if(config==null) {
	                config = new MatrixConfiguration(this,c);
	                config.save();
	                configurations.put(config.getCombination(), config);
	            }
	            active.add(config);
        	}
        }
        this.activeConfigurations = active;
    }

    private File getConfigurationsDir() {
        return new File(getRootDir(),"configurations");
    }

    /**
     * Gets all active configurations.
     * <p>
     * In contract, inactive configurations are those that are left for archival purpose
     * and no longer built when a new {@link MatrixBuildExt} is executed.
     */
    public Collection<MatrixConfiguration> getActiveConfigurations() {
        return activeConfigurations;
    }

    public Collection<MatrixConfiguration> getItems() {
        return configurations.values();
    }

    @Override
    public Collection<? extends JobExt> getAllJobs() {
        Set<JobExt> jobs = new HashSet<JobExt>(getItems());
        jobs.add(this);
        return jobs;
    }

    public String getUrlChildPrefix() {
        return ".";
    }

    public MatrixConfiguration getItem(String name) {
        return getItem(Combination.fromString(name));
    }

    public MatrixConfiguration getItem(Combination c) {
        return configurations.get(c);
    }

    public File getRootDirFor(MatrixConfiguration child) {
        return getRootDirFor(child.getCombination());
    }

    public void onRenamed(MatrixConfiguration item, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void onDeleted(MatrixConfiguration item) throws IOException {
        // noop
    }

    public File getRootDirFor(Combination combination) {
        File f = getConfigurationsDir();
        for (Entry<String, String> e : combination.entrySet())
            f = new File(f,"axis-"+e.getKey()+'/'+UtilExt.rawEncode(e.getValue()));
        f.getParentFile().mkdirs();
        return f;
    }

    /**
     * @see #getJDKs()
     */
    @Override @Deprecated
    public JDKExt getJDK() {
        return super.getJDK();
    }

    /**
     * Gets the {@link JDKExt}s where the builds will be run.
     * @return never null but can be empty
     */
    public Set<JDKExt> getJDKs() {
        AxisExt a = axes.find("jdk");
        if(a==null)  return Collections.emptySet();
        Set<JDKExt> r = new HashSet<JDKExt>();
        for (String j : a) {
            JDKExt jdk = HudsonExt.getInstance().getJDK(j);
            if(jdk!=null)
                r.add(jdk);
        }
        return r;
    }

    /**
     * Gets the {@link LabelExt}s where the builds will be run.
     * @return never null
     */
    public Set<LabelExt> getLabels() {
        Set<LabelExt> r = new HashSet<LabelExt>();
        for (Combination c : axes.subList(LabelAxisExt.class).list())
            r.add(HudsonExt.getInstance().getLabel(UtilExt.join(c.values(),"&&")));
        return r;
    }

    public List<Builder> getBuilders() {
        return builders.toList();
    }

    public DescribableList<Builder,DescriptorExt<Builder>> getBuildersList() {
        return builders;
    }

    public Map<DescriptorExt<Publisher>,Publisher> getPublishers() {
        return publishers.toMap();
    }

    public DescribableList<Publisher,DescriptorExt<Publisher>> getPublishersList() {
        return publishers;
    }

    public DescribableList<BuildWrapper, DescriptorExt<BuildWrapper>> getBuildWrappersList() {
        return buildWrappers;
    }

    public Map<DescriptorExt<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return buildWrappers.toMap();
    }

    public Publisher getPublisher(DescriptorExt<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected Class<MatrixBuildExt> getBuildClass() {
        return MatrixBuildExt.class;
    }

    public boolean isFingerprintConfigured() {
        return false;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        builders.buildDependencyGraph(this,graph);
        buildWrappers.buildDependencyGraph(this,graph);
    }

    public MatrixProjectExt asProject() {
        return this;
    }


    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public String getUrl() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static final class DescriptorImpl extends AbstractProjectDescriptorExt {
        public String getDisplayName() {
            return Messages.MatrixProject_DisplayName();
        }

        public MatrixProjectExt newInstance(ItemGroup parent, String name) {
            return new MatrixProjectExt(parent,name);
        }

        /**
         * All {@link AxisDescriptor}s that contribute to the UI.
         */
        public List<AxisDescriptorExt> getAxisDescriptors() {
            List<AxisDescriptorExt> r = new ArrayList<AxisDescriptorExt>();
            for (AxisDescriptorExt d : AxisExt.all()) {
                if (d.isInstantiable())
                    r.add(d);
            }
            return r;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatrixProjectExt.class.getName());
}

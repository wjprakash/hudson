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
package hudson.tasks;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePathExt;
import hudson.model.AbstractBuildExt;
import hudson.model.AbstractProjectExt;
import hudson.model.FingerprintExt;
import hudson.model.FingerprintExt.BuildPtr;
import hudson.model.HudsonExt;
import hudson.model.RunExt;
import hudson.model.RunAction;
import hudson.util.FormValidation;
import hudson.util.PackedMap;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records fingerprints of the specified files.
 *
 * @author Kohsuke Kawaguchi
 */
public class Fingerprinter extends FingerprinterExt implements Serializable {

     
    @DataBoundConstructor
    public Fingerprinter(String targets, boolean recordBuildArtifacts) {
         super(targets, recordBuildArtifacts);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return Messages.Fingerprinter_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/fingerprint.html";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProjectExt project, @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }

        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
            return req.bindJSON(Fingerprinter.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProjectExt> jobType) {
            return true;
        }
    }

    /**
     * Action for displaying fingerprints.
     */
    public static final class FingerprintAction implements RunAction {
        private final AbstractBuildExt build;

        /**
         * From file name to the digest.
         */
        private /*almost final*/ PackedMap<String,String> record;

        private transient WeakReference<Map<String,FingerprintExt>> ref;

        public FingerprintAction(AbstractBuildExt build, Map<String, String> record) {
            this.build = build;
            this.record = PackedMap.of(record);
            onLoad();   // make compact
        }

        public void add(Map<String,String> moreRecords) {
            Map<String,String> r = new HashMap<String, String>(record);
            r.putAll(moreRecords);
            record = PackedMap.of(r);
            ref = null;
            onLoad();
        }

        public String getIconFileName() {
            return "fingerprint.gif";
        }

        public String getDisplayName() {
            return Messages.Fingerprinter_Action_DisplayName();
        }

        public String getUrlName() {
            return "fingerprints";
        }

        public AbstractBuildExt getBuild() {
            return build;
        }

        /**
         * Obtains the raw data.
         */
        public Map<String,String> getRecords() {
            return record;
        }

        public void onLoad() {
            RunExt pb = build.getPreviousBuild();
            if (pb!=null) {
                FingerprintAction a = pb.getAction(FingerprintAction.class);
                if (a!=null)
                    compact(a);
            }
        }

        public void onAttached(RunExt r) {
        }

        public void onBuildComplete() {
        }

        /**
         * Reuse string instances from another {@link FingerprintAction} to reduce memory footprint.
         */
        protected void compact(FingerprintAction a) {
            Map<String,String> intern = new HashMap<String, String>(); // string intern map
            for (Entry<String, String> e : a.record.entrySet()) {
                intern.put(e.getKey(),e.getKey());
                intern.put(e.getValue(),e.getValue());
            }

            Map<String,String> b = new HashMap<String, String>();
            for (Entry<String,String> e : record.entrySet()) {
                String k = intern.get(e.getKey());
                if (k==null)    k = e.getKey();

                String v = intern.get(e.getValue());
                if (v==null)    v = e.getValue();

                b.put(k,v);
            }

            record = PackedMap.of(b);
        }

        /**
         * Map from file names of the fingerprinted file to its fingerprint record.
         */
        public synchronized Map<String,FingerprintExt> getFingerprints() {
            if(ref!=null) {
                Map<String,FingerprintExt> m = ref.get();
                if(m!=null)
                    return m;
            }

            HudsonExt h = HudsonExt.getInstance();

            Map<String,FingerprintExt> m = new TreeMap<String,FingerprintExt>();
            for (Entry<String, String> r : record.entrySet()) {
                try {
                    FingerprintExt fp = h._getFingerprint(r.getValue());
                    if(fp!=null)
                        m.put(r.getKey(), fp);
                } catch (IOException e) {
                    logger.log(Level.WARNING,e.getMessage(),e);
                }
            }

            m = ImmutableMap.copyOf(m);
            ref = new WeakReference<Map<String,FingerprintExt>>(m);
            return m;
        }

        /**
         * Gets the dependency to other builds in a map.
         * Returns build numbers instead of {@link Build}, since log records may be gone.
         */
        public Map<AbstractProjectExt,Integer> getDependencies() {
            Map<AbstractProjectExt,Integer> r = new HashMap<AbstractProjectExt,Integer>();

            for (FingerprintExt fp : getFingerprints().values()) {
                BuildPtr bp = fp.getOriginal();
                if(bp==null)    continue;       // outside HudsonExt
                if(bp.is(build))    continue;   // we are the owner
                AbstractProjectExt job = bp.getJob();
                if (job==null)  continue;   // no longer exists
                if (job.getParent()==build.getParent())
                    continue;   // we are the parent of the build owner, that is almost like we are the owner 

                Integer existing = r.get(job);
                if(existing!=null && existing>bp.getNumber())
                    continue;   // the record in the map is already up to date
                r.put(job,bp.getNumber());
            }
            
            return r;
        }
    }

    private static final Logger logger = Logger.getLogger(Fingerprinter.class.getName());

    private static final long serialVersionUID = 1L;
}

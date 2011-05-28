/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Michael B. Donohue
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

import hudson.diagnosis.OldDataMonitorExt;
import hudson.model.QueueExt.Task;
import hudson.model.queue.FoldableAction;
import hudson.util.XStream2;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CauseActionExt implements FoldableAction, RunAction {

    /**
     * @deprecated since 2009-02-28
     */
    @Deprecated
    // there can be multiple causes, so this is deprecated
    private transient CauseExt cause;
    private List<CauseExt> causes = new ArrayList<CauseExt>();

    public List<CauseExt> getCauses() {
        return causes;
    }

    public CauseActionExt(CauseExt c) {
        this.causes.add(c);
    }

    public CauseActionExt(CauseActionExt ca) {
        this.causes.addAll(ca.causes);
    }

    public String getDisplayName() {
        return "Cause";
    }

    public String getIconFileName() {
        // no icon
        return null;
    }

    public String getUrlName() {
        return "cause";
    }

    /**
     * Get list of causes with duplicates combined into counters.
     * @return Map of CauseExt to number of occurrences of that CauseExt
     */
    public Map<CauseExt, Integer> getCauseCounts() {
        Map<CauseExt, Integer> result = new LinkedHashMap<CauseExt, Integer>();
        for (CauseExt c : causes) {
            Integer i = result.get(c);
            result.put(c, i == null ? 1 : i.intValue() + 1);
        }
        return result;
    }

    /**
     * @deprecated as of 1.288
     *      but left here for backward compatibility.
     */
    public String getShortDescription() {
        if (causes.isEmpty()) {
            return "N/A";
        }
        return causes.get(0).getShortDescription();
    }

    public void onLoad() {
        // noop
    }

    public void onBuildComplete() {
        // noop
    }

    /**
     * When hooked up to build, notify {@link CauseExt}s.
     */
    public void onAttached(RunExt owner) {
        if (owner instanceof AbstractBuildExt) {// this should be always true but being defensive here
            AbstractBuildExt b = (AbstractBuildExt) owner;
            for (CauseExt c : causes) {
                c.onAddedTo(b);
            }
        }
    }

    public void foldIntoExisting(hudson.model.QueueExt.Item item, Task owner, List<Action> otherActions) {
        CauseActionExt existing = item.getAction(CauseActionExt.class);
        if (existing != null) {
            existing.causes.addAll(this.causes);
            return;
        }
        // no CauseActionExt found, so add a copy of this one
        item.getActions().add(new CauseActionExt(this));
    }

    public static class ConverterImpl extends XStream2.PassthruConverter<CauseActionExt> {

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(CauseActionExt ca, UnmarshallingContext context) {
            // if we are being read in from an older version
            if (ca.cause != null) {
                if (ca.causes == null) {
                    ca.causes = new ArrayList<CauseExt>();
                }
                ca.causes.add(ca.cause);
                OldDataMonitorExt.report(context, "1.288");
            }
        }
    }
}

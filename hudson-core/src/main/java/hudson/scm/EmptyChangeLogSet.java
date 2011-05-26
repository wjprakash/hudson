package hudson.scm;

import hudson.model.AbstractBuildExt;

import java.util.Collections;
import java.util.Iterator;

/**
 * {@link ChangeLogSet} that's empty.
 *
 * @author Kohsuke Kawaguchi
 */
final class EmptyChangeLogSet extends ChangeLogSet<ChangeLogSet.Entry> {
    /*package*/ EmptyChangeLogSet(AbstractBuildExt<?, ?> build) {
        super(build);
    }

    @Override
    public boolean isEmptySet() {
        return true;
    }

    public Iterator<Entry> iterator() {
        return Collections.<Entry>emptySet().iterator();
    }
}

/**
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@Oracle.com
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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Winston Prakash
 */
public class MatrixRun extends MatrixRunExt {

    public MatrixRun(MatrixConfiguration job) throws IOException {
        super(job);
    }

    public MatrixRun(MatrixConfiguration job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixRun(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    Object parentObj = ancs.get(i - 1).getObject();
                    if (parentObj instanceof MatrixBuildExt || parentObj instanceof MatrixConfiguration) {
                        return ancs.get(i - 1).getUrl() + '/';
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    @Override
    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    if (ancs.get(i - 1).getObject() instanceof MatrixBuildExt) {
                        return getParent().getCombination().toCompactString(getParent().getParent().getAxes());
                    }
                }
            }
        }
        return super.getDisplayName();
    }
}

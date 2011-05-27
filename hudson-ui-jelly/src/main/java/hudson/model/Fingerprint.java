/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * A file being tracked by Hudson.
 *
 * <p>
 * Lifecycle is managed by {@link FingerprintMap}.
 *
 * @author Kohsuke Kawaguchi
 * @see FingerprintMap
 */
@ExportedBean
public class Fingerprint extends FingerprintExt {

    /**
     * Pointer to a {@link Build}.
     */
    @ExportedBean(defaultVisibility = 2)
    public static class BuildPtr extends FingerprintExt.BuildPtr {

        public BuildPtr(String name, int number) {
            super(name, number);
        }

        public BuildPtr(RunExt run) {
            this(run.getParent().getFullName(), run.getNumber());
        }

        /**
         * Gets {@link Job#getFullName() the full name of the job}.
         * <p>
         * Such job could be since then removed,
         * so there might not be a corresponding
         * {@link Job}.
         */
        @Exported
        @Override
        public String getName() {
            return super.getName();
        }

        /**
         * Gets the project build number.
         * <p>
         * Such {@link RunExt} could be since then
         * discarded.
         */
        @Exported
        @Override
        public int getNumber() {
            return super.getNumber();
        }
    }

    /**
     * Range of build numbers [start,end). Immutable.
     */
    @ExportedBean(defaultVisibility = 4)
    public static final class Range extends FingerprintExt.Range {

        public Range(int start, int end) {
            super(start, end);
        }

        @Exported
        @Override
        public int getStart() {
            return super.getStart();
        }

        @Exported
        @Override
        public int getEnd() {
            return super.getEnd();
        }
    }

    /**
     * Set of {@link Range}s.
     */
    @ExportedBean(defaultVisibility = 3)
    public static final class RangeSet extends FingerprintExt.RangeSet {

        /**
         * Gets all the ranges.
         */
        @Exported
        @Override
        public synchronized List<FingerprintExt.Range> getRanges() {
            return super.getRanges();
        }
    }

    public Fingerprint(RunExt build, String fileName, byte[] md5sum) throws IOException {
        super(build, fileName, md5sum);
    }

    /**
     * The first build in which this file showed up,
     * if the file looked like it's created there.
     * <p>
     * This is considered as the "source" of this file,
     * or the owner, in the sense that this project "owns"
     * this file.
     *
     * @return null
     *      if the file is apparently created outside Hudson.
     */
    @Exported
    @Override
    public BuildPtr getOriginal() {
        return (BuildPtr) super.getOriginal();
    }

    /**
     * The file name (like "foo.jar" without path).
     */
    @Exported
    @Override
    public String getFileName() {
        return super.getFileName();
    }

    /**
     * Gets the MD5 hash string.
     */
    @Exported(name = "hash")
    @Override
    public String getHashString() {
        return super.getHashString();
    }

    /**
     * Gets the timestamp when this record is created.
     */
    @Exported
    @Override
    public Date getTimestamp() {
        return super.getTimestamp();
    }

    @ExportedBean(defaultVisibility = 2)
    public static final class RangeItem {

        @Exported
        public final String name;
        @Exported
        public final RangeSet ranges;

        public RangeItem(String name, RangeSet ranges) {
            this.name = name;
            this.ranges = ranges;
        }
    }

    // this is for remote API
    @Exported(name = "usage")
    @Override
    public List<FingerprintExt.RangeItem> _getUsages() {
        return super._getUsages();
    }

    public Api getApi() {
        return new Api(this);
    }
}

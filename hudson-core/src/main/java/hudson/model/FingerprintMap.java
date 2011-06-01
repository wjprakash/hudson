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
package hudson.model;

import hudson.UtilExt;
import hudson.diagnosis.OldDataMonitorExt;
import hudson.util.KeyedDataStorage;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of {@link FingerprintExt}s.
 *
 * <p>
 * This implementation makes sure that no two {@link FingerprintExt} objects
 * lie around for the same hash code, and that unused {@link FingerprintExt}
 * will be adequately GC-ed to prevent memory leak.
 *
 * @author Kohsuke Kawaguchi
 * @see HudsonExt#getFingerprintMap() 
 */
public final class FingerprintMap extends KeyedDataStorage<FingerprintExt,FingerprintParams> {

    /**
     * @deprecated since 2007-03-26.
     *      Some old version of HudsonExt incorrectly serialized this information to the disk.
     *      So we need this field to be here for such configuration to be read correctly.
     *      This field is otherwise no longer in use.
     */
    private transient ConcurrentHashMap<String,Object> core = new ConcurrentHashMap<String,Object>();

    /**
     * Returns true if there's some data in the fingerprint database.
     */
    public boolean isReady() {
        return new File(HudsonExt.getInstance().getRootDir(),"fingerprints").exists();
    }

    /**
     * @param build
     *      set to non-null if {@link FingerprintExt} to be created (if so)
     *      will have this build as the owner. Otherwise null, to indicate
     *      an owner-less build.
     */
    public FingerprintExt getOrCreate(AbstractBuildExt build, String fileName, byte[] md5sum) throws IOException {
        return getOrCreate(build,fileName, UtilExt.toHexString(md5sum));
    }

    public FingerprintExt getOrCreate(AbstractBuildExt build, String fileName, String md5sum) throws IOException {
        return super.getOrCreate(md5sum, new FingerprintParams(build,fileName));
    }

    public FingerprintExt getOrCreate(RunExt build, String fileName, String md5sum) throws IOException {
        return super.getOrCreate(md5sum, new FingerprintParams(build,fileName));
    }

    @Override
    protected FingerprintExt get(String md5sum, boolean createIfNotExist, FingerprintParams createParams) throws IOException {
        // sanity check
        if(md5sum.length()!=32)
            return null;    // illegal input
        md5sum = md5sum.toLowerCase(Locale.ENGLISH);

        return super.get(md5sum,createIfNotExist,createParams);
    }

    private byte[] toByteArray(String md5sum) {
        byte[] data = new byte[16];
        for( int i=0; i<md5sum.length(); i+=2 )
            data[i/2] = (byte)Integer.parseInt(md5sum.substring(i,i+2),16);
        return data;
    }

    protected FingerprintExt create(String md5sum, FingerprintParams createParams) throws IOException {
        return new FingerprintExt(createParams.build, createParams.fileName, toByteArray(md5sum));
    }

    protected FingerprintExt load(String key) throws IOException {
        return FingerprintExt.load(toByteArray(key));
    }

    private Object readResolve() {
        if (core != null) OldDataMonitorExt.report(HudsonExt.getInstance(), "1.91");
        return this;
    }
}

class FingerprintParams {
    /**
     * Null if the build isn't claiming to be the owner.
     */
    final RunExt build;
    final String fileName;

    public FingerprintParams(RunExt build, String fileName) {
        this.build = build;
        this.fileName = fileName;

        assert fileName!=null;
    }
}

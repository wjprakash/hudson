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
package hudson;

import hudson.model.Hudson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.jar.Manifest;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

/**
 *
 * @author Winston Prakash
 */
public class PluginWrapper extends PluginWrapperExt {

    public PluginWrapper(PluginManagerExt parent, File archive, Manifest manifest, URL baseResourceURL,
            ClassLoader classLoader, File disableFile,
            List<Dependency> dependencies, List<Dependency> optionalDependencies) {
        super(parent, archive, manifest, baseResourceURL, classLoader, disableFile, dependencies, optionalDependencies);
    }

    // Action methods
    
    public HttpResponse doMakeEnabled() throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        enable();
        return HttpResponses.ok();
    }

    public HttpResponse doMakeDisabled() throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        disable();
        return HttpResponses.ok();
    }

    public HttpResponse doPin() throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        new FileOutputStream(pinFile).close();
        return HttpResponses.ok();
    }

    public HttpResponse doUnpin() throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        pinFile.delete();
        return HttpResponses.ok();
    }
}

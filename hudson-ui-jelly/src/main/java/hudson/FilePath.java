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

import hudson.model.AbstractProjectExt;
import hudson.model.ItemExt;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import org.kohsuke.stapler.Stapler; 

/**
 *
 * @author Winston Prakash
 */
public class FilePath extends FilePathExt {

    /**
     * Creates a {@link FilePathExt} that represents a path on the given node.
     *
     * @param channel
     *      To create a path that represents a remote path, pass in a {@link Channel}
     *      that's connected to that machine. If null, that means the local file path.
     */
    public FilePath(VirtualChannel channel, String remote) {
        super(channel, remote);
    }

    /**
     * To create {@link FilePathExt} that represents a "local" path.
     *
     * <p>
     * A "local" path means a file path on the computer where the
     * constructor invocation happened.
     */
    public FilePath(File localPath) {
        super(localPath);
    }

    /**
     * Construct a path starting with a base location.
     * @param base starting point for resolution, and defines channel
     * @param rel a path which if relative will be resolved against base
     */
    public FilePath(FilePathExt base, String rel) {
        super(base, rel);
    }
    
     /**
     * Shortcut for {@link #validateFileMask(String)} in case the left-hand side can be null.
     */
    public static FormValidation validateFileMask(FilePath pathOrNull, String value) throws IOException {
        if (pathOrNull == null) {
            return FormValidation.ok();
        }
        return pathOrNull.validateFileMask(value);
    }

    /**
     * Short for {@code validateFileMask(value,true)} 
     */
    public FormValidation validateFileMask(String value) throws IOException {
        return validateFileMask(value, true);
    }

    /**
     * Checks the GLOB-style file mask. See {@link #validateAntFileMask(String)}.
     * Requires configure permission on ancestor AbstractProjectExt object in request.
     * @since 1.294
     */
    public FormValidation validateFileMask(String value, boolean errorIfNotExist) throws IOException {
        
        AbstractProjectExt subject = Stapler.getCurrentRequest().findAncestorObject(AbstractProjectExt.class);
        subject.checkPermission(ItemExt.CONFIGURE);
        
        value = UtilExt.fixEmpty(value);
        if (value == null) {
            return FormValidation.ok();
        }

        try {
            if (!exists()) // no workspace. can't check
            {
                return FormValidation.ok();
            }

            String msg = validateAntFileMask(value);
            if (errorIfNotExist) {
                return FormValidation.error(msg);
            } else {
                return FormValidation.warning(msg);
            }
        } catch (InterruptedException e) {
            return FormValidation.ok();
        }
    }

    /**
     * Validates a relative file path from this {@link FilePathExt}.
     * Requires configure permission on ancestor AbstractProjectExt object in request.
     *
     * @param value
     *      The relative path being validated.
     * @param errorIfNotExist
     *      If true, report an error if the given relative path doesn't exist. Otherwise it's a warning.
     * @param expectingFile
     *      If true, we expect the relative path to point to a file.
     *      Otherwise, the relative path is expected to be pointing to a directory.
     */
    public static FormValidation validateRelativePath(FilePathExt file, String value, boolean errorIfNotExist, boolean expectingFile) throws IOException {
        AbstractProjectExt subject = Stapler.getCurrentRequest().findAncestorObject(AbstractProjectExt.class);
        subject.checkPermission(ItemExt.CONFIGURE);

        value = UtilExt.fixEmpty(value);

        // none entered yet, or something is seriously wrong
        if (value == null || (AbstractProjectExt<?, ?>) subject == null) {
            return FormValidation.ok();
        }

        // a common mistake is to use wildcard
        if (value.contains("*")) {
            return FormValidation.error(Messages.FilePath_validateRelativePath_wildcardNotAllowed());
        }

        try {
            if (!file.exists()) // no base directory. can't check
            {
                return FormValidation.ok();
            }

            FilePathExt path = file.child(value);
            if (path.exists()) {
                if (expectingFile) {
                    if (!path.isDirectory()) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notFile(value));
                    }
                } else {
                    if (path.isDirectory()) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notDirectory(value));
                    }
                }
            }

            String msg = expectingFile ? Messages.FilePath_validateRelativePath_noSuchFile(value)
                    : Messages.FilePath_validateRelativePath_noSuchDirectory(value);
            if (errorIfNotExist) {
                return FormValidation.error(msg);
            } else {
                return FormValidation.warning(msg);
            }
        } catch (InterruptedException e) {
            return FormValidation.ok();
        }
    }

    /**
     * A convenience method over {@link #validateRelativePath(String, boolean, boolean)}.
     */
    public static FormValidation validateRelativeDirectory(FilePathExt file, String value, boolean errorIfNotExist) throws IOException {
        return validateRelativePath(file, value, errorIfNotExist, false);
    }

    public static FormValidation validateRelativeDirectory(FilePathExt dir, String value) throws IOException {
        return validateRelativeDirectory(dir, value, true);
    }
}

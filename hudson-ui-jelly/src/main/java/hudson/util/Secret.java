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
package hudson.util;

import hudson.model.HudsonExt;
import org.kohsuke.stapler.Stapler;


/**
 * Glorified {@link String} that uses encryption in the persisted form, to avoid accidental exposure of a secret.
 *
 * <p>
 * Note that since the cryptography relies on {@link HudsonExt#getSecretKey()}, this is not meant as a protection
 * against code running in the same VM, nor against an attacker who has local file system access. 
 *
 * <p>
 * {@link Secret}s can correctly read-in plain text password, so this allows the existing
 * String field to be updated to {@link Secret}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Secret extends SecretExt {
     

    private Secret(String value) {
        super(value);
    }

    
    static {
        Stapler.CONVERT_UTILS.register(new org.apache.commons.beanutils.Converter() {
            @Override
            public SecretExt convert(Class type, Object value) {
                return SecretExt.fromString(value.toString());
            }
        }, Secret.class);
    }
}

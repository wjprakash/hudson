/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Oracle Corporation, Kohsuke Kawaguchi, Winston Prakash
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
package hudson.cli;

import hudson.FilePathExt;
import hudson.FilePathExt.FileCallable;
import hudson.UtilExt;
import hudson.model.HudsonExt;
import hudson.model.HudsonExt.MasterComputer;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.util.SecretExt;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.springframework.dao.DataAccessException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Properties;

/**
 * Represents the authentication credential store of the CLI client.
 *
 * <p>
 * This object encapsulates a remote manipulation of the credential store.
 * We store encrypted user names.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 */
public class ClientAuthenticationCache implements Serializable {
    /**
     * Where the store should be placed.
     */
    private final FilePathExt store;

    /**
     * Loaded contents of the store.
     */
    private final Properties props = new Properties();

    public ClientAuthenticationCache(Channel channel) throws IOException, InterruptedException {
        store = (channel==null ? MasterComputer.localChannel :  channel).call(new Callable<FilePathExt, IOException>() {
            public FilePathExt call() throws IOException {
                File home = new File(System.getProperty("user.home"));
                return new FilePathExt(new File(home, ".hudson/cli-credentials"));
            }
        });
        if (store.exists()) {
            props.load(store.read());
        }
    }

    /**
     * Gets the persisted authentication for this HudsonExt.
     *
     * @return {@link HudsonExt#ANONYMOUS} if no such credential is found, or if the stored credential is invalid.
     */
    public Authentication get() {
        HudsonExt h = HudsonExt.getInstance();
        SecretExt userName = SecretExt.decrypt(props.getProperty(getPropertyKey()));
        if (userName==null) return HudsonExt.ANONYMOUS; // failed to decrypt
        try {
            UserDetails u = h.getSecurityRealm().loadUserByUsername(userName.toString());
            return new UsernamePasswordAuthenticationToken(u.getUsername(), u.getPassword(), u.getAuthorities());
        } catch (AuthenticationException e) {
            return HudsonExt.ANONYMOUS;
        } catch (DataAccessException e) {
            return HudsonExt.ANONYMOUS;
        }
    }

    /**
     * Computes the key that identifies this HudsonExt among other Hudsons that the user has a credential for.
     */
    private String getPropertyKey() {
        String url = HudsonExt.getInstance().getRootUrl();
        if (url!=null)  return url;
        return SecretExt.fromString("key").toString();
    }

    /**
     * Persists the specified authentication.
     */
    public void set(Authentication a) throws IOException, InterruptedException {
        HudsonExt h = HudsonExt.getInstance();

        // make sure that this security realm is capable of retrieving the authentication by name,
        // as it's not required.
        UserDetails u = h.getSecurityRealm().loadUserByUsername(a.getName());
        props.setProperty(getPropertyKey(), SecretExt.fromString(u.getUsername()).getEncryptedValue());

        save();
    }

    /**
     * Removes the persisted credential, if there's one.
     */
    public void remove() throws IOException, InterruptedException {
        if (props.remove(getPropertyKey())!=null)
            save();
    }

    private void save() throws IOException, InterruptedException {
        store.act(new FileCallable<Void>() {
            public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                f.getParentFile().mkdirs();

                OutputStream os = new FileOutputStream(f);
                try {
                    props.store(os,"Credential store");
                } finally {
                    os.close();
                }

                // try to protect this file from other users, if we can.
                UtilExt.chmod(f, 0600);
                return null;
            }
        });
    }
}

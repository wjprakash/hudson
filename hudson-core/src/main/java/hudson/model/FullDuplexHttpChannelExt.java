/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.remoting.Channel;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Builds a {@link Channel} on top of two HTTP streams (one used for each direction.)
 *
 * @author Kohsuke Kawaguchi
 */
abstract class FullDuplexHttpChannelExt {
    protected Channel channel;

    protected InputStream upload;

    protected final UUID uuid;
    protected final boolean restricted;

    protected boolean completed;

    public FullDuplexHttpChannelExt(UUID uuid, boolean restricted) throws IOException {
        this.uuid = uuid;
        this.restricted = restricted;
    }

    

    protected abstract void main(Channel channel) throws IOException, InterruptedException;


    public Channel getChannel() {
        return channel;
    }

    protected static final Logger LOGGER = Logger.getLogger(FullDuplexHttpChannelExt.class.getName());

    /**
     * Set to true if the servlet container doesn't support chunked encoding.
     */
    public static boolean DIY_CHUNKING = Boolean.getBoolean("hudson.diyChunking");
}

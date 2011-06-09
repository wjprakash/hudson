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
package hudson;

import hudson.model.AbstractModelObjectExt;
import hudson.model.Hudson;
import hudson.model.ItemExt;
import java.io.IOException;
import javax.servlet.ServletException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Various Stapler utility methods that don't have more proper home.
 *
 * @author Kohsuke Kawaguchi
 */
public class StaplerUtils {

    /**
     * Wraps with the error icon and the CSS class to render error message.
     * @since 1.173
     */
    public static String wrapToErrorSpan(String s) {
        s = "<span class=error><img src='"
                + Stapler.getCurrentRequest().getContextPath() + Hudson.RESOURCE_PATH
                + "/images/none.gif' height=16 width=1>" + s + "</span>";
        return s;
    }
    
    /**
     * Displays the error in a page.
     */
    public static void sendError(AbstractModelObjectExt modelObject, Exception e) throws ServletException, IOException {
        sendError(modelObject, e, Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
    }

    public static void sendError(AbstractModelObjectExt modelObject, Exception e, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        sendError(modelObject, e.getMessage(), req, rsp);
    }

    public static void sendError(AbstractModelObjectExt modelObject, String message) throws ServletException, IOException {
        sendError(modelObject, message, Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
    }

    public static void sendError(AbstractModelObjectExt modelObject, String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message", message);
        rsp.forward(modelObject, "error", req);
    }

    /**
     * @param pre
     *      If true, the message is put in a PRE tag.
     */
    public static void sendError(AbstractModelObjectExt modelObject, String message, StaplerRequest req, StaplerResponse rsp, boolean pre) throws ServletException, IOException {
        req.setAttribute("message", message);
        if (pre) {
            req.setAttribute("pre", true);
        }
        rsp.forward(modelObject, "error", req);
    }
    
     /**
     * Convenience method to verify that the current request is a POST request.
     */
    public static void requirePOST() throws ServletException {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req==null)  return; // invoked outside the context of servlet
        String method = req.getMethod();
        if(!method.equalsIgnoreCase("POST"))
            throw new ServletException("Must be POST, Can't be "+method);
    }
    
    public static String getUrl(ItemExt item) {
        // try to stick to the current view if possible
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req, item);
            if(seed!=null) {
                // trim off the context path portion and leading '/', but add trailing '/'
                return seed.substring(req.getContextPath().length()+1)+'/';
            }
        }

        // otherwise compute the path normally
        return item.getParent().getUrl() + getShortUrl(item);
    }

    public static String getShortUrl(ItemExt item) {
        return item.getParent().getUrlChildPrefix()+'/' + UtilExt.rawEncode(item.getName()) + '/';
    }
    
}

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo! Inc., Stephen Connolly, Tom Huybrechts, Alan Harder, Romain Seguy
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

import hudson.console.ConsoleAnnotatorFactory;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.model.Action;
import hudson.model.DescriptorExt;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.ItemGroup;
import hudson.model.RunExt;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.search.SearchableModelObject;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.csrf.CrumbIssuer;
import hudson.util.Area;
import hudson.util.Iterators;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Utility functions used in views.
 *
 * <p>
 * An instance of this class is created for each request and made accessible
 * from view pages via the variable 'h' (h stands for HudsonExt.)
 *
 * @author Kohsuke Kawaguchi
 */
public class Functions extends FunctionsExt {

    private static final Pattern SCHEME = Pattern.compile("[a-z]+://.+");

    public static RunUrl decompose(StaplerRequest req) {
        List<Ancestor> ancestors = req.getAncestors();

        // find the first and last RunExt instances
        Ancestor f = null, l = null;
        for (Ancestor anc : ancestors) {
            if (anc.getObject() instanceof RunExt) {
                if (f == null) {
                    f = anc;
                }
                l = anc;
            }
        }
        if (l == null) {
            return null;    // there was no RunExt object
        }
        String head = f.getPrev().getUrl() + '/';
        String base = l.getUrl();

        String reqUri = req.getOriginalRequestURI();
        // Find "rest" or URI by removing N path components.
        // Not using reqUri.substring(f.getUrl().length()) to avoid mismatches due to
        // url-encoding or extra slashes.  Former may occur in Tomcat (despite the spec saying
        // this string is not decoded, Tomcat apparently decodes this string. You see ' '
        // instead of '%20', which is what the browser has sent), latter may occur in some
        // proxy or URL-rewriting setups where extra slashes are inadvertently added.
        String furl = f.getUrl();
        int slashCount = 0;
        // Count components in ancestor URL
        for (int i = furl.indexOf('/'); i >= 0; i = furl.indexOf('/', i + 1)) {
            slashCount++;
        }
        // Remove that many from request URL, ignoring extra slashes
        String rest = reqUri.replaceFirst("(?:/+[^/]*){" + slashCount + "}", "");

        return new RunUrl((RunExt) f.getObject(), head, base, rest);
    }

    /**
     * If we know the user's screen resolution, return it. Otherwise null.
     * @since 1.213
     */
    public static Area getScreenResolution() {
        Cookie res = FunctionsExt.getCookie(Stapler.getCurrentRequest(), "screenResolution");
        if (res != null) {
            return Area.parse(res.getValue());
        }
        return null;
    }

    /**
     * Finds the given object in the ancestor list and returns its URL.
     * This is used to determine the "current" URL assigned to the given object,
     * so that one can compute relative URLs from it.
     */
    public static String getNearestAncestorUrl(StaplerRequest req, Object it) {
        List list = req.getAncestors();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ancestor anc = (Ancestor) list.get(i);
            if (anc.getObject() == it) {
                return anc.getUrl();
            }
        }
        return null;
    }

    /**
     * Finds the inner-most {@link SearchableModelObject} in scope.
     */
    public static String getSearchURL() {
        List list = Stapler.getCurrentRequest().getAncestors();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ancestor anc = (Ancestor) list.get(i);
            if (anc.getObject() instanceof SearchableModelObject) {
                return anc.getUrl() + "/search/";
            }
        }
        return null;
    }

    /**
     * This version is so that the 'checkPermission' on <tt>layout.jelly</tt>
     * degrades gracefully if "it" is not an {@link AccessControlled} object.
     * Otherwise it will perform no check and that problem is hard to notice.
     */
    public static void checkPermission(Object object, Permission permission) throws IOException, ServletException {
        if (permission == null) {
            return;
        }

        if (object instanceof AccessControlled) {
            checkPermission((AccessControlled) object, permission);
        } else {
            List<Ancestor> ancs = Stapler.getCurrentRequest().getAncestors();
            for (Ancestor anc : Iterators.reverse(ancs)) {
                Object o = anc.getObject();
                if (o instanceof AccessControlled) {
                    checkPermission((AccessControlled) o, permission);
                    return;
                }
            }
            checkPermission(HudsonExt.getInstance(), permission);
        }
    }

    /**
     * This version is so that the 'hasPermission' can degrade gracefully
     * if "it" is not an {@link AccessControlled} object.
     */
    public static boolean hasPermission(Object object, Permission permission) throws IOException, ServletException {
        if (permission == null) {
            return true;
        }
        if (object instanceof AccessControlled) {
            return ((AccessControlled) object).hasPermission(permission);
        } else {
            List<Ancestor> ancs = Stapler.getCurrentRequest().getAncestors();
            for (Ancestor anc : Iterators.reverse(ancs)) {
                Object o = anc.getObject();
                if (o instanceof AccessControlled) {
                    return ((AccessControlled) o).hasPermission(permission);
                }
            }
            return HudsonExt.getInstance().hasPermission(permission);
        }
    }

    public static void adminCheck(StaplerRequest req, StaplerResponse rsp, Object required, Permission permission) throws IOException, ServletException {
        // this is legacy --- all views should be eventually converted to
        // the permission based model.
        if (required != null && !HudsonExt.adminCheck(req, rsp)) {
            // check failed. commit the FORBIDDEN response, then abort.
            rsp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            rsp.getOutputStream().close();
            throw new ServletException("Unauthorized access");
        }

        // make sure the user owns the necessary permission to access this page.
        if (permission != null) {
            checkPermission(permission);
        }
    }

    /**
     * Infers the hudson installation URL from the given request.
     */
    public static String inferHudsonURL(StaplerRequest req) {
        String rootUrl = HudsonExt.getInstance().getRootUrl();
        if (rootUrl != null) // prefer the one explicitly configured, to work with load-balancer, frontend, etc.
        {
            return rootUrl;
        }
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()).append("://");
        buf.append(req.getServerName());
        if (req.getLocalPort() != 80) {
            buf.append(':').append(req.getLocalPort());
        }
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    /**
     * Returns true if the current user has the given permission.
     *
     * @param permission
     *      If null, returns true. This defaulting is convenient in making the use of this method terse.
     */
    public static boolean hasPermission(Permission permission) throws IOException, ServletException {
        return hasPermission(HudsonExt.getInstance(), permission);
    }

    /**
     * Computes the relative path from the current page to the given item.
     */
    public static String getRelativeLinkTo(ItemExt p) {
        Map<Object, String> ancestors = new HashMap<Object, String>();
        View view = null;

        StaplerRequest request = Stapler.getCurrentRequest();
        for (Ancestor a : request.getAncestors()) {
            ancestors.put(a.getObject(), a.getRelativePath());
            if (a.getObject() instanceof View) {
                view = (View) a.getObject();
            }
        }

        String path = ancestors.get(p);
        if (path != null) {
            return path;
        }

        ItemExt i = p;
        String url = "";
        while (true) {
            ItemGroup ig = i.getParent();
            url = i.getShortUrl() + url;

            if (ig == HudsonExt.getInstance()) {
                assert i instanceof TopLevelItem;
                if (view != null && view.contains((TopLevelItem) i)) {
                    // if p and the current page belongs to the same view, then return a relative path
                    return ancestors.get(view) + '/' + url;
                } else {
                    // otherwise return a path from the root HudsonExt
                    return request.getContextPath() + '/' + p.getUrl();
                }
            }

            path = ancestors.get(ig);
            if (path != null) {
                return path + '/' + url;
            }

            assert ig instanceof ItemExt; // if not, ig must have been the HudsonExt instance
            i = (ItemExt) ig;
        }
    }

    public static String getViewResource(Object it, String path) {
        Class clazz = it.getClass();

        if (it instanceof Class) {
            clazz = (Class) it;
        }
        if (it instanceof DescriptorExt) {
            clazz = ((DescriptorExt) it).clazz;
        }

        StringBuilder buf = new StringBuilder(Stapler.getCurrentRequest().getContextPath());
        buf.append(HudsonExt.VIEW_RESOURCE_PATH).append('/');
        buf.append(clazz.getName().replace('.', '/').replace('$', '/'));
        buf.append('/').append(path);

        return buf.toString();
    }

    public static boolean hasView(Object it, String path) throws IOException {
        if (it == null) {
            return false;
        }
        return Stapler.getCurrentRequest().getView(it, path) != null;
    }

    /**
     * Computes the hyperlink to actions, to handle the situation when the {@link Action#getUrlName()}
     * returns absolute URL.
     */
    public static String getActionUrl(String itUrl, Action action) {
        String urlName = action.getUrlName();
        if (urlName == null) {
            return null;    // to avoid NPE and fail to render the whole page
        }
        if (SCHEME.matcher(urlName).matches()) {
            return urlName; // absolute URL
        }
        if (urlName.startsWith("/")) {
            return Stapler.getCurrentRequest().getContextPath() + urlName;
        } else // relative URL name
        {
            return Stapler.getCurrentRequest().getContextPath() + '/' + itUrl + urlName;
        }
    }

    /**
     * Obtains the host name of the HudsonExt server that clients can use to talk back to.
     * <p>
     * This is primarily used in <tt>slave-agent.jnlp.jelly</tt> to specify the destination
     * that the slaves talk to.
     */
    public String getServerName() {
        // Try to infer this from the configured root URL.
        // This makes it work correctly when HudsonExt runs behind a reverse proxy.
        String url = HudsonExt.getInstance().getRootUrl();
        try {
            if (url != null) {
                String host = new URL(url).getHost();
                if (host != null) {
                    return host;
                }
            }
        } catch (MalformedURLException e) {
            // fall back to HTTP request
        }
        return Stapler.getCurrentRequest().getServerName();
    }

    /**
     * If the given href link is matching the current page, return true.
     *
     * Used in <tt>task.jelly</tt> to decide if the page should be highlighted.
     */
    public boolean hyperlinkMatchesCurrentPage(String href) throws UnsupportedEncodingException {
        String url = Stapler.getCurrentRequest().getRequestURL().toString();
        if (href == null || href.length() <= 1) {
            return ".".equals(href) && url.endsWith("/");
        }
        url = URLDecoder.decode(url, "UTF-8");
        href = URLDecoder.decode(href, "UTF-8");
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (href.endsWith("/")) {
            href = href.substring(0, href.length() - 1);
        }

        return url.endsWith(href);
    }

    public static String getCrumb(StaplerRequest req) {
        HudsonExt h = HudsonExt.getInstance();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.getCrumb(req) : "";
    }

    public static Locale getClientLocale() {
        return Stapler.getCurrentRequest().getLocale();
    }
    
    /**
     * Generate a series of &lt;script> tags to include <tt>script.js</tt>
     * from {@link ConsoleAnnotatorFactory}s and {@link ConsoleAnnotationDescriptor}s.
     */
    public static String generateConsoleAnnotationScriptAndStylesheet() {
        String cp = Stapler.getCurrentRequest().getContextPath();
        StringBuilder buf = new StringBuilder();
        for (ConsoleAnnotatorFactory f : ConsoleAnnotatorFactory.all()) {
            String path = cp + "/extensionList/" + ConsoleAnnotatorFactory.class.getName() + "/" + f.getClass().getName();
            if (f.hasScript())
                buf.append("<script src='"+path+"/script.js'></script>");
            if (f.hasStylesheet())
                buf.append("<link rel='stylesheet' type='text/css' href='"+path+"/style.css' />");
        }
        for (ConsoleAnnotationDescriptor d : ConsoleAnnotationDescriptor.all()) {
            String path = cp+"/descriptor/"+d.clazz.getName();
            if (d.hasScript())
                buf.append("<script src='"+path+"/script.js'></script>");
            if (d.hasStylesheet())
                buf.append("<link rel='stylesheet' type='text/css' href='"+path+"/style.css' />");
        }
        return buf.toString();
    }

}

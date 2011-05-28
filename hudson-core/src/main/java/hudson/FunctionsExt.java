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

import hudson.model.AbstractProjectExt;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.DescriptorExt;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.HudsonExt;
import hudson.model.ItemExt;
import hudson.model.Items;
import hudson.model.JobExt;
import hudson.model.JobPropertyDescriptorExt;
import hudson.model.ModelObject;
import hudson.model.NodeExt;
import hudson.model.PageDecorator;
import hudson.model.ParameterDefinitionExt;
import hudson.model.ParameterDefinitionExt.ParameterDescriptorExt;
import hudson.model.ProjectExt;
import hudson.model.RunExt;
import hudson.model.JDKExt;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategyExt;
import hudson.security.Permission;
import hudson.security.SecurityRealmExt;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.scm.SCMExt;
import hudson.scm.SCMDescriptor;
import hudson.security.captcha.CaptchaSupport;
import hudson.util.Secret;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.apache.commons.jexl.util.Introspector;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;
import org.jvnet.tiger_types.Types;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Date;
import java.util.Locale;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

/**
 * Utility functions used in views.
 *
 * <p>
 * An instance of this class is created for each request and made accessible
 * from view pages via the variable 'h' (h stands for HudsonExt.)
 *
 * @author Kohsuke Kawaguchi
 */
public class FunctionsExt {
    private static volatile int globalIota = 0;
    private int iota;

    public FunctionsExt() {
        iota = globalIota;
        // concurrent requests can use the same ID --- we are just trying to
        // prevent the same user from seeing the same ID repeatedly.
        globalIota+=1000;
    }

    /**
     * Generates an unique ID.
     */
    public String generateId() {
        return "id"+iota++;
    }

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER.format(cal.getTime());
    }

    public static String rfc822Date(Calendar cal) {
        return Util.RFC822_DATETIME_FORMATTER.format(cal.getTime());
    }

    /**
     * Given {@code c=MyList (extends ArrayList<Foo>), base=List}, compute the parameterization of 'base'
     * that's assignable from 'c' (in this case {@code List<Foo>}), and return its n-th type parameter
     * (n=0 would return {@code Foo}).
     *
     * <p>
     * This method is useful for doing type arithmetic.
     *
     * @throws AssertionError
     *      if c' is not parameterized.
     */
    public static <B> Class getTypeParameter(Class<? extends B> c, Class<B> base, int n) {
        Type parameterization = Types.getBaseClass(c,base);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            return Types.erasure(Types.getTypeArgument(pt,n));
        } else {
            throw new AssertionError(c+" doesn't properly parameterize "+base);
        }
    }

    public JDKExt.DescriptorImpl getJDKDescriptor() {
        return HudsonExt.getInstance().getDescriptorByType(JDKExt.DescriptorImpl.class);
    }

    /**
     * Prints the integer as a string that represents difference,
     * like "-5", "+/-0", "+3".
     */
    public static String getDiffString(int i) {
        if(i==0)    return "\u00B10";   // +/-0
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString(int)} that doesn't show anything for +/-0
     */
    public static String getDiffString2(int i) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString2(int)} that puts the result into prefix and suffix
     * if there's something to print
     */
    public static String getDiffString2(String prefix, int i, String suffix) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return prefix+"+"+s+suffix;
        else        return prefix+s+suffix;
    }

    /**
     * Adds the proper suffix.
     */
    public static String addSuffix(int n, String singular, String plural) {
        StringBuilder buf = new StringBuilder();
        buf.append(n).append(' ');
        if(n==1)
            buf.append(singular);
        else
            buf.append(plural);
        return buf.toString();
    }

     

    /**
     * URL decomposed for easier computation of relevant URLs.
     *
     * <p>
     * The decomposed URL will be of the form:
     * <pre>
     * aaaaaa/524/bbbbb/cccc
     * -head-| N |---rest---
     * ----- base -----|
     * </pre>
     *
     * <p>
     * The head portion is the part of the URL from the {@link HudsonExt}
     * object to the first {@link RunExt} subtype. When "next/prev build"
     * is chosen, this part remains intact.
     *
     * <p>
     * The <tt>524</tt> is the path from {@link JobExt} to {@link RunExt}.
     *
     * <p>
     * The <tt>bbb</tt> portion is the path after that till the last
     * {@link RunExt} subtype. The <tt>ccc</tt> portion is the part
     * after that.
     */
    public static final class RunUrl {
        private final String head, base, rest;
        private final RunExt run;


        public RunUrl(RunExt run, String head, String base, String rest) {
            this.run = run;
            this.head = head;
            this.base = base;
            this.rest = rest;
        }

        public String getBaseUrl() {
            return base;
        }

        /**
         * Returns the same page in the next build.
         */
        public String getNextBuildUrl() {
            return getUrl(run.getNextBuild());
        }

        /**
         * Returns the same page in the previous build.
         */
        public String getPreviousBuildUrl() {
            return getUrl(run.getPreviousBuild());
        }

        private String getUrl(RunExt n) {
            if(n ==null)
                return null;
            else {
                return head+n.getNumber()+rest;
            }
        }
    }

    public static NodeExt.ModeExt[] getNodeModes() {
        return NodeExt.ModeExt.values();
    }

    public static String getProjectListString(List<ProjectExt> projects) {
        return Items.toNameList(projects);
    }

    /**
     * @deprecated as of 1.294
     *      JEXL now supports the real ternary operator "x?y:z", so this work around
     *      is no longer necessary.
     */
    public static Object ifThenElse(boolean cond, Object thenValue, Object elseValue) {
        return cond ? thenValue : elseValue;
    }

    public static String appendIfNotNull(String text, String suffix, String nullText) {
        return text == null ? nullText : text + suffix;
    }

    public static Map getSystemProperties() {
        return new TreeMap<Object,Object>(System.getProperties());
    }

    public static Map getEnvVars() {
        return new TreeMap<String,String>(EnvVars.masterEnvVars);
    }

    public static boolean isWindows() {
        return File.pathSeparatorChar==';';
    }

    public static List<LogRecord> getLogRecords() {
        return HudsonExt.logRecords;
    }

    public static String printLogRecord(LogRecord r) {
        return formatter.format(r);
    }

    public static Cookie getCookie(HttpServletRequest req,String name) {
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static String getCookie(HttpServletRequest req,String name, String defaultValue) {
        Cookie c = getCookie(req, name);
        if(c==null || c.getValue()==null) return defaultValue;
        return c.getValue();
    }

    /**
     * Gets the suffix to use for YUI JavaScript.
     */
    public static String getYuiSuffix() {
        return DEBUG_YUI ? "debug" : "min";
    }

    /**
     * Set to true if you need to use the debug version of YUI.
     */
    public static boolean DEBUG_YUI = Boolean.getBoolean("debug.YUI");

    /**
     * Creates a sub map by using the given range (both ends inclusive).
     */
    public static <V> SortedMap<Integer,V> filter(SortedMap<Integer,V> map, String from, String to) {
        if(from==null && to==null)      return map;
        if(to==null)
            return map.headMap(Integer.parseInt(from)-1);
        if(from==null)
            return map.tailMap(Integer.parseInt(to));

        return map.subMap(Integer.parseInt(to),Integer.parseInt(from)-1);
    }

    private static final SimpleFormatter formatter = new SimpleFormatter();

    /**
     * Used by <tt>layout.jelly</tt> to control the auto refresh behavior.
     *
     * @param noAutoRefresh
     *      On certain pages, like a page with forms, will have annoying interference
     *      with auto refresh. On those pages, disable auto-refresh.
     */
    public static void configureAutoRefresh(HttpServletRequest request, HttpServletResponse response, boolean noAutoRefresh) {
        if(noAutoRefresh)
            return;

        String param = request.getParameter("auto_refresh");
        boolean refresh = isAutoRefresh(request);
        if (param != null) {
            refresh = Boolean.parseBoolean(param);
            Cookie c = new Cookie("hudson_auto_refresh", Boolean.toString(refresh));
            // Need to set path or it will not stick from e.g. a project page to the dashboard.
            // Using request.getContextPath() might work but it seems simpler to just use the hudson_ prefix
            // to avoid conflicts with any other web apps that might be on the same machine.
            c.setPath("/");
            c.setMaxAge(60*60*24*30); // persist it roughly for a month
            response.addCookie(c);
        }
        if (refresh) {
            response.addHeader("Refresh", System.getProperty("hudson.Functions.autoRefreshSeconds", "10"));
        }
    }

    public static boolean isAutoRefresh(HttpServletRequest request) {
        String param = request.getParameter("auto_refresh");
        if (param != null) {
            return Boolean.parseBoolean(param);
        }
        Cookie[] cookies = request.getCookies();
        if(cookies==null)
            return false; // when API design messes it up, we all suffer

        for (Cookie c : cookies) {
            if (c.getName().equals("hudson_auto_refresh")) {
                return Boolean.parseBoolean(c.getValue());
            }
        }
        return false;
    }

     
    public static String appendSpaceIfNotNull(String n) {
        if(n==null) return null;
        else        return n+' ';
    }

    /**
     * One nbsp per 10 pixels in given size, which may be a plain number or "NxN"
     * (like an iconSize).  Useful in a sortable table heading.
     */
    public static String nbspIndent(String size) {
        int i = size.indexOf('x');
        i = Integer.parseInt(i > 0 ? size.substring(0, i) : size) / 10;
        StringBuilder buf = new StringBuilder(30);
        for (int j = 0; j < i; j++)
            buf.append("&nbsp;");
        return buf.toString();
    }

    public static String getWin32ErrorMessage(IOException e) {
        return Util.getWin32ErrorMessage(e);
    }

    public static boolean isMultiline(String s) {
        if(s==null)     return false;
        return s.indexOf('\r')>=0 || s.indexOf('\n')>=0;
    }

    public static String encode(String s) {
        return Util.encode(s);
    }

    public static String escape(String s) {
        return Util.escape(s);
    }

    public static String xmlEscape(String s) {
        return Util.xmlEscape(s);
    }

    public static void checkPermission(Permission permission) throws IOException, ServletException {
        checkPermission(HudsonExt.getInstance(),permission);
    }

    public static void checkPermission(AccessControlled object, Permission permission) throws IOException, ServletException {
        if (permission != null) {
            object.checkPermission(permission);
        }
    }


    public static List<JobPropertyDescriptorExt> getJobPropertyDescriptors(Class<? extends JobExt> clazz) {
        return JobPropertyDescriptorExt.getPropertyDescriptors(clazz);
    }

    public static List<DescriptorExt<BuildWrapper>> getBuildWrapperDescriptors(AbstractProjectExt<?,?> project) {
        return BuildWrappers.getFor(project);
    }

    public static List<DescriptorExt<SecurityRealmExt>> getSecurityRealmDescriptors() {
        return SecurityRealmExt.all();
    }

    public static List<DescriptorExt<AuthorizationStrategyExt>> getAuthorizationStrategyDescriptors() {
        return AuthorizationStrategyExt.all();
    }

    public static List<DescriptorExt<Builder>> getBuilderDescriptors(AbstractProjectExt<?,?> project) {
        return BuildStepDescriptor.filter(Builder.all(), project.getClass());
    }

    public static List<DescriptorExt<Publisher>> getPublisherDescriptors(AbstractProjectExt<?,?> project) {
        return BuildStepDescriptor.filter(Publisher.all(), project.getClass());
    }

    public static List<SCMDescriptor<?>> getSCMDescriptors(AbstractProjectExt<?,?> project) {
        return SCMExt._for(project);
    }

    public static List<DescriptorExt<ComputerLauncher>> getComputerLauncherDescriptors() {
        return HudsonExt.getInstance().<ComputerLauncher,DescriptorExt<ComputerLauncher>>getDescriptorList(ComputerLauncher.class);
    }

    public static List<DescriptorExt<RetentionStrategy<?>>> getRetentionStrategyDescriptors() {
        return RetentionStrategy.all();
    }

    public static List<ParameterDescriptorExt> getParameterDescriptors() {
        return ParameterDefinitionExt.all();
    }

    public static List<DescriptorExt<ViewsTabBar>> getViewsTabBarDescriptors() {
        return ViewsTabBar.all();
    }
    
    public static List<DescriptorExt<CaptchaSupport>> getCaptchaSupportDescriptors() {
        return CaptchaSupport.all();
    }

    public static List<DescriptorExt<MyViewsTabBar>> getMyViewsTabBarDescriptors() {
        return MyViewsTabBar.all();
    }

    public static List<NodePropertyDescriptor> getNodePropertyDescriptors(Class<? extends NodeExt> clazz) {
        List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
        Collection<NodePropertyDescriptor> list = (Collection) HudsonExt.getInstance().getDescriptorList(NodeProperty.class);
        for (NodePropertyDescriptor npd : list) {
            if (npd.isApplicable(clazz)) {
                result.add(npd);
            }
        }
        return result;
    }

    /**
     * Gets all the descriptors sorted by their inheritance tree of {@link Describable}
     * so that descriptors of similar types come nearby.
     */
    public static Collection<hudson.model.Descriptor> getSortedDescriptorsForGlobalConfig() {
        Map<String,DescriptorExt> r = new TreeMap<String, DescriptorExt>();
        for (DescriptorExt<?> d : HudsonExt.getInstance().getExtensionList(DescriptorExt.class)) {
            if (d.getGlobalConfigPage()==null)  continue;
            r.put(buildSuperclassHierarchy(d.clazz, new StringBuilder()).toString(),d);
        }
        return r.values();
    }

    private static StringBuilder buildSuperclassHierarchy(Class c, StringBuilder buf) {
        Class sc = c.getSuperclass();
        if (sc!=null)   buildSuperclassHierarchy(sc,buf).append(':');
        return buf.append(c.getName());
    }

    /**
     * Computes the path to the icon of the given action
     * from the context path.
     */
    public static String getIconFilePath(Action a) {
        String name = a.getIconFileName();
        if(name.startsWith("/"))
            return name.substring(1);
        else
            return "images/24x24/"+name;
    }

    /**
     * Works like JSTL build-in size(x) function,
     * but handle null gracefully.
     */
    public static int size2(Object o) throws Exception {
        if(o==null) return 0;
        return ASTSizeFunction.sizeOf(o,Introspector.getUberspect());
    }

    

    public static Map<Thread,StackTraceElement[]> dumpAllThreads() {
        Map<Thread,StackTraceElement[]> sorted = new TreeMap<Thread,StackTraceElement[]>(new ThreadSorter());
        sorted.putAll(Thread.getAllStackTraces());
        return sorted;
    }

    @IgnoreJRERequirement
    public static ThreadInfo[] getThreadInfos() {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        return mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(),mbean.isSynchronizerUsageSupported());
    }

    public static ThreadGroupMap sortThreadsAndGetGroupMap(ThreadInfo[] list) {
        ThreadGroupMap sorter = new ThreadGroupMap();
        Arrays.sort(list, sorter);
        return sorter;
    }

    // Common code for sorting Threads/ThreadInfos by ThreadGroup
    private static class ThreadSorterBase {
        protected Map<Long,String> map = new HashMap<Long,String>();

        private ThreadSorterBase() {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) tg = tg.getParent();
            Thread[] threads = new Thread[tg.activeCount()*2];
            int threadsLen = tg.enumerate(threads, true);
            for (int i = 0; i < threadsLen; i++)
                map.put(threads[i].getId(), threads[i].getThreadGroup().getName());
        }

        protected int compare(long idA, long idB) {
            String tga = map.get(idA), tgb = map.get(idB);
            int result = (tga!=null?-1:0) + (tgb!=null?1:0);  // Will be non-zero if only one is null
            if (result==0 && tga!=null)
                result = tga.compareToIgnoreCase(tgb);
            return result;
        }
    }

    public static class ThreadGroupMap extends ThreadSorterBase implements Comparator<ThreadInfo> {

        /**
         * @return ThreadGroup name or null if unknown
         */
        public String getThreadGroup(ThreadInfo ti) {
            return map.get(ti.getThreadId());
        }

        public int compare(ThreadInfo a, ThreadInfo b) {
            int result = compare(a.getThreadId(), b.getThreadId());
            if (result == 0)
                result = a.getThreadName().compareToIgnoreCase(b.getThreadName());
            return result;
        }
    }

    private static class ThreadSorter extends ThreadSorterBase implements Comparator<Thread> {

        public int compare(Thread a, Thread b) {
            int result = compare(a.getId(), b.getId());
            if (result == 0)
                result = a.getName().compareToIgnoreCase(b.getName());
            return result;
        }
    }

    /**
     * Are we running on JRE6 or above?
     */
    @IgnoreJRERequirement
    public static boolean isMustangOrAbove() {
        try {
            System.console();
            return true;
        } catch(LinkageError e) {
            return false;
        }
    }

    // ThreadInfo.toString() truncates the stack trace by first 8, so needed my own version
    @IgnoreJRERequirement
    public static String dumpThreadInfo(ThreadInfo ti, ThreadGroupMap map) {
        String grp = map.getThreadGroup(ti);
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
                                             " Id=" + ti.getThreadId() + " Group=" +
                                             (grp != null ? grp : "?") + " " +
                                             ti.getThreadState());
        if (ti.getLockName() != null) {
            sb.append(" on " + ti.getLockName());
        }
        if (ti.getLockOwnerName() != null) {
            sb.append(" owned by \"" + ti.getLockOwnerName() +
                      "\" Id=" + ti.getLockOwnerId());
        }
        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        StackTraceElement[] stackTrace = ti.getStackTrace();
        for (int i=0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && ti.getLockInfo() != null) {
                Thread.State ts = ti.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
       }

       LockInfo[] locks = ti.getLockedSynchronizers();
       if (locks.length > 0) {
           sb.append("\n\tNumber of locked synchronizers = " + locks.length);
           sb.append('\n');
           for (LockInfo li : locks) {
               sb.append("\t- " + li);
               sb.append('\n');
           }
       }
       sb.append('\n');
       return sb.toString();
    }

    public static <T> Collection<T> emptyList() {
        return Collections.emptyList();
    }

    public static String jsStringEscape(String s) {
        StringBuilder buf = new StringBuilder();
        for( int i=0; i<s.length(); i++ ) {
            char ch = s.charAt(i);
            switch(ch) {
            case '\'':
                buf.append("\\'");
                break;
            case '\\':
                buf.append("\\\\");
                break;
            case '"':
                buf.append("\\\"");
                break;
            default:
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    /**
     * Converts "abc" to "Abc".
     */
    public static String capitalize(String s) {
        if(s==null || s.length()==0) return s;
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }

    public static String getVersion() {
        return HudsonExt.VERSION;
    }

    /**
     * Resoruce path prefix.
     */
    public static String getResourcePath() {
        return HudsonExt.RESOURCE_PATH;
    }

     

    /**
     * Can be used to check a checkbox by default.
     * Used from views like {@code h.defaultToTrue(scm.useUpdate)}.
     * The expression will evaluate to true if scm is null.
     */
    public static boolean defaultToTrue(Boolean b) {
        if(b==null) return true;
        return b;
    }

    /**
     * If the value exists, return that value. Otherwise return the default value.
     * <p>
     * Starting 1.294, JEXL supports the elvis operator "x?:y" that supercedes this.
     *
     * @since 1.150
     */
    public static <T> T defaulted(T value, T defaultValue) {
        return value!=null ? value : defaultValue;
    }

    public static String printThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Counts the number of rows needed for textarea to fit the content.
     * Minimum 5 rows.
     */
    public static int determineRows(String s) {
        if(s==null)     return 5;
        return Math.max(5,LINE_END.split(s).length);
    }

    /**
     * Converts the HudsonExt build status to CruiseControl build status,
     * which is either Success, Failure, Exception, or Unknown.
     */
    public static String toCCStatus(ItemExt i) {
        if (i instanceof JobExt) {
            JobExt j = (JobExt) i;
            switch (j.getIconColor().noAnime()) {
            case ABORTED:
            case RED:
            case YELLOW:
                return "Failure";
            case BLUE:
                return "Success";
            case DISABLED:
            case GREY:
                return "Unknown";
            }
        }
        return "Unknown";
    }

    private static final Pattern LINE_END = Pattern.compile("\r?\n");

    /**
     * Checks if the current user is anonymous.
     */
    public static boolean isAnonymous() {
        return HudsonExt.getAuthentication() instanceof AnonymousAuthenticationToken;
    }

    /**
     * When called from within JEXL expression evaluation,
     * this method returns the current {@link JellyContext} used
     * to evaluate the script.
     *
     * @since 1.164
     */
    public static JellyContext getCurrentJellyContext() {
        JellyContext context = ExpressionFactory2.CURRENT_CONTEXT.get();
        assert context!=null;
        return context;
    }

    /**
     * Evaluate a Jelly script and return output as a String.
     *
     * @since 1.267
     */
    public static String runScript(Script script) throws JellyTagException {
        StringWriter out = new StringWriter();
        script.run(getCurrentJellyContext(), XMLOutput.createXMLOutput(out));
        return out.toString();
    }

    /**
     * Returns a sub-list if the given list is bigger than the specified 'maxSize'
     */
    public static <T> List<T> subList(List<T> base, int maxSize) {
        if(maxSize<base.size())
            return base.subList(0,maxSize);
        else
            return base;
    }

    /**
     * Escapes the character unsafe for e-mail address.
     * See http://en.wikipedia.org/wiki/E-mail_address for the details,
     * but here the vocabulary is even more restricted.
     */
    public static String toEmailSafeString(String projectName) {
        // TODO: escape non-ASCII characters
        StringBuilder buf = new StringBuilder(projectName.length());
        for( int i=0; i<projectName.length(); i++ ) {
            char ch = projectName.charAt(i);
            if(('a'<=ch && ch<='z')
            || ('z'<=ch && ch<='Z')
            || ('0'<=ch && ch<='9')
            || "-_.".indexOf(ch)>=0)
                buf.append(ch);
            else
                buf.append('_');    // escape
        }
        return projectName;
    }

    public String getSystemProperty(String key) {
        return System.getProperty(key);
    }

    

    /**
     * Determines the form validation check URL. See textbox.jelly
     */
    public String getCheckUrl(String userDefined, Object descriptor, String field) {
        if(userDefined!=null || field==null)   return userDefined;
        if (descriptor instanceof DescriptorExt) {
            DescriptorExt d = (DescriptorExt) descriptor;
            return d.getCheckUrl(field);
        }
        return null;
    }

     

    public <T> List<T> singletonList(T t) {
        return Collections.singletonList(t);
    }

    /**
     * Gets all the {@link PageDecorator}s.
     */
    public static List<PageDecorator> getPageDecorators() {
        // this method may be called to render start up errors, at which point HudsonExt doesn't exist yet. see HUDSON-3608 
        if(HudsonExt.getInstance()==null)  return Collections.emptyList();
        return PageDecorator.all();
    }
    
    public static List<DescriptorExt<Cloud>> getCloudDescriptors() {
        return Cloud.all();
    }

    /**
     * Prepend a prefix only when there's the specified body.
     */
    public String prepend(String prefix, String body) {
        if(body!=null && body.length()>0)
            return prefix+body;
        return body;
    }

    public static List<DescriptorExt<CrumbIssuer>> getCrumbIssuerDescriptors() {
        return CrumbIssuer.all();
    }

    

    public static String getCrumbRequestField() {
        HudsonExt h = HudsonExt.getInstance();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.getDescriptor().getCrumbRequestField() : "";
    }

    public static Date getCurrentTime() {
        return new Date();
    }
    
   
    
    public static Locale getServerLocale(){
        return Locale.getDefault();
    }

     
    /**
     * Work around for bug 6935026.
     */
    public List<String> getLoggerNames() {
        while (true) {
            try {
                List<String> r = new ArrayList<String>();
                Enumeration<String> e = LogManager.getLogManager().getLoggerNames();
                while (e.hasMoreElements())
                    r.add(e.nextElement());
                return r;
            } catch (ConcurrentModificationException e) {
                // retry
            }
        }
    }

    /**
     * Used by &lt;f:password/> so that we send an encrypted value to the client.
     */
    public String getPasswordValue(Object o) {
        if (o==null)    return null;
        if (o instanceof Secret)    return ((Secret)o).getEncryptedValue();
        return o.toString();
    }

    public List filterDescriptors(Object context, Iterable descriptors) {
        return DescriptorVisibilityFilter.apply(context,descriptors);
    }
    
     

    /**
     * Returns true if we are running unit tests.
     */
    public static boolean getIsUnitTest() {
        return Main.isUnitTest;
    }

    /**
     * Returns {@code true} if the {@link RunExt#ARTIFACTS} permission is enabled,
     * {@code false} otherwise.
     *
     * <p>When the {@link RunExt#ARTIFACTS} permission is not turned on using the
     * {@code hudson.security.ArtifactsPermission}, this permission must not be
     * considered to be set to {@code false} for every user. It must rather be
     * like if the permission doesn't exist at all (which means that every user
     * has to have an access to the artifacts but the permission can't be
     * configured in the security screen). Got it?</p>
     */
    public static boolean isArtifactsPermissionEnabled() {
        return Boolean.getBoolean("hudson.security.ArtifactsPermission");
    }

}

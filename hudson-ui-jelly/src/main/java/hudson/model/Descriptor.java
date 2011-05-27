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

import hudson.RelativePath;
import hudson.Util;
import static hudson.Util.singleQuote;
import hudson.util.ReflectionUtils;
import hudson.util.ReflectionUtils.Parameter;
import hudson.views.ListViewColumn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.util.StringUtils;
import org.apache.commons.io.IOUtils;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Metadata about a configurable instance.
 *
 * <p>
 * {@link DescriptorExt} is an object that has metadata about a {@link Describable}
 * object, and also serves as a factory (in a way this relationship is similar
 * to {@link Object}/{@link Class} relationship.
 *
 * A {@link DescriptorExt}/{@link Describable}
 * combination is used throughout in Hudson to implement a
 * configuration/extensibility mechanism.
 *
 * <p>
 * Take the list view support as an example, which is implemented
 * in {@link ListView} class. Whenever a new view is created, a new
 * {@link ListView} instance is created with the configuration
 * information. This instance gets serialized to XML, and this instance
 * will be called to render the view page. This is the job
 * of {@link Describable} &mdash; each instance represents a specific
 * configuration of a view (what projects are in it, regular expression, etc.)
 *
 * <p>
 * For Hudson to create such configured {@link ListView} instance, Hudson
 * needs another object that captures the metadata of {@link ListView},
 * and that is what a {@link DescriptorExt} is for. {@link ListView} class
 * has a singleton descriptor, and this descriptor helps render
 * the configuration form, remember system-wide configuration, and works as a factory.
 *
 * <p>
 * {@link DescriptorExt} also usually have its associated views.
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link DescriptorExt} can persist data just by storing them in fields.
 * However, it is the responsibility of the derived type to properly
 * invoke {@link #save()} and {@link #load()}.
 *
 * <h2>Reflection Enhancement</h2>
 * {@link DescriptorExt} defines addition to the standard Java reflection
 * and provides reflective information about its corresponding {@link Describable}.
 * These are primarily used by tag libraries to
 * keep the Jelly scripts concise. 
 *
 * @author Kohsuke Kawaguchi
 * @see Describable
 */
public abstract class Descriptor<T extends Describable<T>>  extends  DescriptorExt {
     

    /**
     * Gets the URL that this DescriptorExt is bound to, relative to the nearest {@link DescriptorByNameOwner}.
     * Since {@link Hudson} is a {@link DescriptorByNameOwner}, there's always one such ancestor to any request.
     */
    public String getDescriptorUrl() {
        return "descriptorByName/" + getId();
    }

    private String getCurrentDescriptorByNameUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        return a.getUrl();
    }

    /**
     * If the field "xyz" of a {@link Describable} has the corresponding "doCheckXyz" method,
     * return the form-field validation string. Otherwise null.
     * <p>
     * This method is used to hook up the form validation method to the corresponding HTML input element.
     */
    public String getCheckUrl(String fieldName) {
        String method = checkMethods.get(fieldName);
        if(method==null) {
            method = calcCheckUrl(fieldName);
            checkMethods.put(fieldName,method);
        }

        if (method.equals(NONE)) // == would do, but it makes IDE flag a warning
            return null;

        // put this under the right contextual umbrella.
        // a is always non-null because we already have Hudson as the sentinel
        return singleQuote(getCurrentDescriptorByNameUrl()+'/')+'+'+method;
    }

    private String calcCheckUrl(String fieldName) {
        String capitalizedFieldName = StringUtils.capitalize(fieldName);

        Method method = ReflectionUtils.getPublicMethodNamed(getClass(),"doCheck"+ capitalizedFieldName);

        if(method==null)
            return NONE;

        return singleQuote(getDescriptorUrl() +"/check"+capitalizedFieldName) + buildParameterList(method, new StringBuilder()).append(".toString()");
    }

    /**
     * Builds query parameter line by figuring out what should be submitted
     */
    private StringBuilder buildParameterList(Method method, StringBuilder query) {
        for (Parameter p : ReflectionUtils.getParameters(method)) {
            QueryParameter qp = p.annotation(QueryParameter.class);
            if (qp!=null) {
                String name = qp.value();
                if (name.length()==0) name = p.name();
                if (name==null || name.length()==0)
                    continue;   // unknown parameter name. we'll report the error when the form is submitted.

                RelativePath rp = p.annotation(RelativePath.class);
                if (rp!=null)
                    name = rp.value()+'/'+name;

                if (query.length()==0)  query.append("+qs(this)");

                if (name.equals("value")) {
                    // The special 'value' parameter binds to the the current field
                    query.append(".addThis()");
                } else {
                    query.append(".nearBy('"+name+"')");
                }
                continue;
            }

            Method m = ReflectionUtils.getPublicMethodNamed(p.type(), "fromStapler");
            if (m!=null)    buildParameterList(m,query);
        }
        return query;
    }

    /**
     * Computes the list of other form fields that the given field depends on, via the doFillXyzItems method,
     * and sets that as the 'fillDependsOn' attribute. Also computes the URL of the doFillXyzItems and
     * sets that as the 'fillUrl' attribute.
     */
    public void calcFillSettings(String field, Map<String,Object> attributes) {
        String capitalizedFieldName = StringUtils.capitalize(field);
        String methodName = "doFill" + capitalizedFieldName + "Items";
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if(method==null)
            throw new IllegalStateException(String.format("%s doesn't have the %s method for filling a drop-down list", getClass(), methodName));

        // build query parameter line by figuring out what should be submitted
        List<String> depends = buildFillDependencies(method, new ArrayList<String>());

        if (!depends.isEmpty())
            attributes.put("fillDependsOn",Util.join(depends," "));
        attributes.put("fillUrl", String.format("%s/%s/fill%sItems", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    private List<String> buildFillDependencies(Method method, List<String> depends) {
        for (Parameter p : ReflectionUtils.getParameters(method)) {
            QueryParameter qp = p.annotation(QueryParameter.class);
            if (qp!=null) {
                String name = qp.value();
                if (name.length()==0) name = p.name();
                if (name==null || name.length()==0)
                    continue;   // unknown parameter name. we'll report the error when the form is submitted.

                RelativePath rp = p.annotation(RelativePath.class);
                if (rp!=null)
                    name = rp.value()+'/'+name;

                depends.add(name);
                continue;
            }

            Method m = ReflectionUtils.getPublicMethodNamed(p.type(), "fromStapler");
            if (m!=null)
                buildFillDependencies(m,depends);
        }
        return depends;
    }

    /**
     * Computes the auto-completion setting
     */
    public void calcAutoCompleteSettings(String field, Map<String,Object> attributes) {
        String capitalizedFieldName = StringUtils.capitalize(field);
        String methodName = "doAutoComplete" + capitalizedFieldName;
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if(method==null)
            return;    // no auto-completion

        attributes.put("autoCompleteUrl", String.format("%s/%s/autoComplete%s", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    
    /**
     * @deprecated
     *      Implement {@link #newInstance(StaplerRequest, JSONObject)} method instead.
     *      Deprecated as of 1.145. 
     */
    public T newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException(getClass()+" should implement newInstance(StaplerRequest,JSONObject)");
    }

    /**
     * Creates a configured instance from the submitted form.
     *
     * <p>
     * Hudson only invokes this method when the user wants an instance of <tt>T</tt>.
     * So there's no need to check that in the implementation.
     *
     * <p>
     * Starting 1.206, the default implementation of this method does the following:
     * <pre>
     * req.bindJSON(clazz,formData);
     * </pre>
     * <p>
     * ... which performs the databinding on the constructor of {@link #clazz}.
     *
     * <p>
     * For some types of {@link Describable}, such as {@link ListViewColumn}, this method
     * can be invoked with null request object for historical reason. Such design is considered
     * broken, but due to the compatibility reasons we cannot fix it. Because of this, the
     * default implementation gracefully handles null request, but the contract of the method
     * still is "request is always non-null." Extension points that need to define the "default instance"
     * semantics should define a descriptor subtype and add the no-arg newInstance method.
     *
     * @param req
     *      Always non-null (see note above.) This object includes represents the entire submission.
     * @param formData
     *      The JSON object that captures the configuration data for this {@link DescriptorExt}.
     *      See http://wiki.hudson-ci.com/display/HUDSON/Structured+Form+Submission
     *      Always non-null.
     *
     * @throws FormException
     *      Signals a problem in the submitted form.
     * @since 1.145
     */
    public T newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        try {
            Method m = getClass().getMethod("newInstance", StaplerRequest.class);

            if(!Modifier.isAbstract(m.getDeclaringClass().getModifiers())) {
                // this class overrides newInstance(StaplerRequest).
                // maintain the backward compatible behavior
                return verifyNewInstance(newInstance(req));
            } else {
                if (req==null) {
                    // yes, req is supposed to be always non-null, but see the note above
                    return verifyNewInstance(clazz.newInstance());
                }

                // new behavior as of 1.206
                return verifyNewInstance(req.bindJSON(clazz,formData));
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e); // impossible
        } catch (InstantiationException e) {
            throw new Error("Failed to instantiate "+clazz+" from "+formData,e);
        } catch (IllegalAccessException e) {
            throw new Error("Failed to instantiate "+clazz+" from "+formData,e);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to instantiate "+clazz+" from "+formData,e);
        }
    }

   

    /**
     * @deprecated
     *      As of 1.239, use {@link #configure(StaplerRequest, JSONObject)}.
     */
    public boolean configure( StaplerRequest req ) throws FormException {
        return true;
    }

    /**
     * Invoked when the global configuration page is submitted.
     *
     * Can be overriden to store descriptor-specific information.
     *
     * @param json
     *      The JSON object that captures the configuration data for this {@link DescriptorExt}.
     *      See http://wiki.hudson-ci.com/display/HUDSON/Structured+Form+Submission
     * @return false
     *      to keep the client in the same config page.
     */
    public boolean configure( StaplerRequest req, JSONObject json ) throws FormException {
        // compatibility
        return configure(req);
    }

    public String getConfigPage() {
        return getViewPage(clazz, "config.jelly");
    }

    public String getGlobalConfigPage() {
        return getViewPage(clazz, "global.jelly",null);
    }

    private String getViewPage(Class<?> clazz, String pageName, String defaultValue) {
        while(clazz!=Object.class) {
            String name = clazz.getName().replace('.', '/').replace('$', '/') + "/" + pageName;
            if(clazz.getClassLoader().getResource(name)!=null)
                return '/'+name;
            clazz = clazz.getSuperclass();
        }
        return defaultValue;
    }

    protected final String getViewPage(Class<?> clazz, String pageName) {
        // We didn't find the configuration page.
        // Either this is non-fatal, in which case it doesn't matter what string we return so long as
        // it doesn't exist.
        // Or this error is fatal, in which case we want the developer to see what page he's missing.
        // so we put the page name.
        return getViewPage(clazz,pageName,pageName);
    }

    /**
     * Serves <tt>help.html</tt> from the resource of {@link #clazz}.
     */
    public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        if(path.contains("..")) throw new ServletException("Illegal path: "+path);

        path = path.replace('/','-');

        for (Class c=clazz; c!=null; c=c.getSuperclass()) {
            RequestDispatcher rd = Stapler.getCurrentRequest().getView(c, "help"+path);
            if(rd!=null) {// Jelly-generated help page
                rd.forward(req,rsp);
                return;
            }

            InputStream in = getHelpStream(c,path);
            if(in!=null) {
                // TODO: generalize macro expansion and perhaps even support JEXL
                rsp.setContentType("text/html;charset=UTF-8");
                String literal = IOUtils.toString(in,"UTF-8");
                rsp.getWriter().println(Util.replaceMacro(literal, Collections.singletonMap("rootURL",req.getContextPath())));
                in.close();
                return;
            }
        }
        rsp.sendError(SC_NOT_FOUND);
    }


    /**
     * Used to build {@link Describable} instance list from &lt;f:hetero-list> tag.
     *
     * @param req
     *      Request that represents the form submission.
     * @param formData
     *      Structured form data that represents the contains data for the list of describables.
     * @param key
     *      The JSON property name for 'formData' that represents the data for the list of describables.
     * @param descriptors
     *      List of descriptors to create instances from.
     * @return
     *      Can be empty but never null.
     */
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, JSONObject formData, String key,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        return newInstancesFromHeteroList(req,formData.get(key),descriptors);
    }

    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, Object formData,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        List<T> items = new ArrayList<T>();

        if (formData!=null) {
            for (Object o : JSONArray.fromObject(formData)) {
                JSONObject jo = (JSONObject)o;
                String kind = jo.getString("kind");
                items.add(find(descriptors,kind).newInstance(req,jo));
            }
        }

        return items;
    }

   
    public static final class FormException extends Exception implements HttpResponse {
        private final String formField;

        public FormException(String message, String formField) {
            super(message);
            this.formField = formField;
        }

        public FormException(String message, Throwable cause, String formField) {
            super(message, cause);
            this.formField = formField;
        }

        public FormException(Throwable cause, String formField) {
            super(cause);
            this.formField = formField;
        }

        /**
         * Which form field contained an error?
         */
        public String getFormField() {
            return formField;
        }

        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            // for now, we can't really use the field name that caused the problem.
            new FailureExt(getMessage()).generateResponse(req,rsp,node);
        }
    }

}

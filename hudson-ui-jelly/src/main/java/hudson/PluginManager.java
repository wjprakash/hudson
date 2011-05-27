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

import hudson.model.FailureExt;
import hudson.model.HudsonExt;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.util.PersistedList;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Winston Prakash
 */
public abstract class PluginManager extends PluginManagerExt{
    
    public PluginManager(ServletContext context, File rootDir) {
         super(context, rootDir);
    }
    public HttpResponse doUpdateSources(StaplerRequest req) throws IOException {
        HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

        if (req.hasParameter("remove")) {
            UpdateCenter uc = HudsonExt.getInstance().getUpdateCenter();
            BulkChange bc = new BulkChange(uc);
            try {
                for (String id : req.getParameterValues("sources"))
                    uc.getSites().remove(uc.getById(id));
            } finally {
                bc.commit();
            }
        } else
        if (req.hasParameter("add"))
            return new HttpRedirect("addSite");

        return new HttpRedirect("./sites");
    }

    /**
     * Performs the installation of the plugins.
     */
    public void doInstall(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Enumeration<String> en = req.getParameterNames();
        while (en.hasMoreElements()) {
            String n =  en.nextElement();
            if(n.startsWith("plugin.")) {
                n = n.substring(7);
                if (n.indexOf(".") > 0) {
                    String[] pluginInfo = n.split("\\.");
                    UpdateSite.Plugin p = HudsonExt.getInstance().getUpdateCenter().getById(pluginInfo[1]).getPlugin(pluginInfo[0]);
                    if(p==null)
                        throw new FailureExt("No such plugin: "+n);
                    p.deploy();
                }
            }
        }
        rsp.sendRedirect("../updateCenter/");
    }
    

    /**
     * Bare-minimum configuration mechanism to change the update center.
     */
    public HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException {
        HudsonExt hudson = HudsonExt.getInstance();
        hudson.checkPermission(HudsonExt.ADMINISTER);
        UpdateCenter uc = hudson.getUpdateCenter();
        PersistedList<UpdateSite> sites = uc.getSites();
        for (UpdateSite s : sites) {
            if (s.getId().equals("default"))
                sites.remove(s);
        }
        sites.add(new UpdateSite("default",site));
        
        return HttpResponses.redirectToContextRoot();
    }


    public HttpResponse doProxyConfigure(
            @QueryParameter("proxy.server") String server,
            @QueryParameter("proxy.port") String port,
            @QueryParameter("proxy.noProxyFor") String noProxyFor,
            @QueryParameter("proxy.userName") String userName,
            @QueryParameter("proxy.password") String password,
            @QueryParameter("proxy.authNeeded") String authNeeded) throws IOException {
        HudsonExt hudson = HudsonExt.getInstance();
        hudson.checkPermission(HudsonExt.ADMINISTER);
        
        server = Util.fixEmptyAndTrim(server);

        if ((server != null) && !"".equals(server)) {
            // If port is not specified assume it is port 80 (usual default for HTTP port)
            int portNumber = 80;
            if (!"".equals(Util.fixNull(port))){
                portNumber = Integer.parseInt(Util.fixNull(port));
            }
            
            boolean proxyAuthNeeded = "on".equals(Util.fixNull(authNeeded));
            if (!proxyAuthNeeded){
                userName = "";
                password = "";
            }
             
            hudson.proxy = new ProxyConfiguration(server , portNumber, Util.fixEmptyAndTrim(noProxyFor),
                    Util.fixEmptyAndTrim(userName), Util.fixEmptyAndTrim(password), "on".equals(Util.fixNull(authNeeded)));
            hudson.proxy.save();
        } else {
            hudson.proxy = null;
            ProxyConfiguration.getXmlFile().delete();

        }
        return new HttpRedirect("advanced");
    }

    /**
     * Uploads a plugin.
     */
    public HttpResponse doUploadPlugin(StaplerRequest req) throws IOException, ServletException {
        try {
            HudsonExt.getInstance().checkPermission(HudsonExt.ADMINISTER);

            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());

            // Parse the request
            FileItem fileItem = (FileItem) upload.parseRequest(req).get(0);
            String fileName = Util.getFileName(fileItem.getName());
            if("".equals(fileName))
                return new HttpRedirect("advanced");
            if(!fileName.endsWith(".hpi"))
                throw new FailureExt(hudson.model.Messages.Hudson_NotAPlugin(fileName));
            fileItem.write(new File(rootDir, fileName));
            fileItem.delete();

            pluginUploaded = true;

            return new HttpRedirect(".");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {// grrr. fileItem.write throws this
            throw new ServletException(e);
        }
    }
}

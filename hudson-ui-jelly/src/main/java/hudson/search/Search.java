/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.search;

import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.DataWriter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Web-bound object that serves QuickSilver-like search requests.
 *
 * @author Kohsuke Kawaguchi
 */
public class Search extends SearchExt{
    
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        List<Ancestor> l = req.getAncestors();
        for( int i=l.size()-1; i>=0; i-- ) {
            Ancestor a = l.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();

                SearchIndex index = smo.getSearchIndex();
                String query = req.getParameter("q");
                if(query!=null) {
                    SuggestedItem target = find(index, query);
                    if(target!=null) {
                        // found
                        rsp.sendRedirect2(a.getUrl()+target.getUrl());
                        return;
                    }
                }
            }
        }

        // no exact match. show the suggestions
        rsp.setStatus(SC_NOT_FOUND);
        req.getView(this,"search-failed.jelly").forward(req,rsp);
    }

    /**
     * Used by OpenSearch auto-completion. Returns JSON array of the form:
     *
     * <pre>
     * ["queryString",["comp1","comp2",...]]
     * </pre>
     *
     * See http://developer.mozilla.org/en/docs/Supporting_search_suggestions_in_search_plugins
     */
    public void doSuggestOpenSearch(StaplerRequest req, StaplerResponse rsp, @QueryParameter String q) throws IOException, ServletException {
        DataWriter w = Flavor.JSON.createDataWriter(null, rsp);
        w.startArray();
        w.value(q);

        w.startArray();
        for (SuggestedItem item : getSuggestions(req, q))
            w.value(item.getPath());
        w.endArray();
        w.endArray();
    }

    /**
     * Used by search box auto-completion. Returns JSON array.
     */
    public void doSuggest(StaplerRequest req, StaplerResponse rsp, @QueryParameter String query) throws IOException, ServletException {
        Result r = new Result();
        for (SuggestedItem item : getSuggestions(req, query))
            r.suggestions.add(new Item(item.getPath()));

        rsp.serveExposedBean(req,r,Flavor.JSON);
    }

    /**
     * Gets the list of suggestions that match the given query.
     *
     * @return
     *      can be empty but never null. The size of the list is always smaller than
     *      a certain threshold to avoid showing too many options. 
     */
    public List<SuggestedItem> getSuggestions(StaplerRequest req, String query) {
        Set<String> paths = new HashSet<String>();  // paths already added, to control duplicates
        List<SuggestedItem> r = new ArrayList<SuggestedItem>();
        for (SuggestedItem i : suggest(makeSuggestIndex(req), query)) {
            if(r.size()>20) break;
            if(paths.add(i.getPath()))
                r.add(i);
        }
        return r;
    }

    /**
     * Creates merged search index for suggestion.
     */
    private SearchIndex makeSuggestIndex(StaplerRequest req) {
        SearchIndexBuilder builder = new SearchIndexBuilder();
        for (Ancestor a : req.getAncestors()) {
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();
                builder.add(smo.getSearchIndex());
            }
        }
        return builder.make();
    }

    @ExportedBean
    public static class Result {
        @Exported
        public List<Item> suggestions = new ArrayList<Item>();
    }

    @ExportedBean(defaultVisibility=999)
    public static class Item {
        @Exported
        public String name;

        public Item(String name) {
            this.name = name;
        }
    }
}

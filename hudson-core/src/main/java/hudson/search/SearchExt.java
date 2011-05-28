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

import hudson.util.EditDistance;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Web-bound object that serves QuickSilver-like search requests.
 *
 * @author Kohsuke Kawaguchi
 */
public class SearchExt {
     
 
    public static class Result {
        public List<Item> suggestions = new ArrayList<Item>();
    }

    public static class Item {
        public String name;

        public Item(String name) {
            this.name = name;
        }
    }

    private enum Mode {
        FIND {
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.find(token, result);
            }
        },
        SUGGEST {
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.suggest(token, result);
            }
        };

        abstract void find(SearchIndex index, String token, List<SearchItem> result);

    }

    /**
     * Performs a search and returns the match, or null if no match was found.
     */
    public static SuggestedItem find(SearchIndex index, String query) {
        List<SuggestedItem> r = find(Mode.FIND, index, query);
        if(r.isEmpty()) return null;
        else            return r.get(0);
    }

    public static List<SuggestedItem> suggest(SearchIndex index, final String tokenList) {

        class Tag implements Comparable<Tag>{
            final SuggestedItem item;
            final int distance;
            /** If the path to this suggestion starts with the token list, 1. Otherwise 0. */
            final int prefixMatch;

            Tag(SuggestedItem i) {
                item = i;
                distance = EditDistance.editDistance(i.getPath(),tokenList);
                prefixMatch = i.getPath().startsWith(tokenList)?1:0;
            }

            public int compareTo(Tag that) {
                int r = this.prefixMatch -that.prefixMatch;
                if(r!=0)    return -r;  // ones with head match should show up earlier
                return this.distance-that.distance;
            }
        }

        List<Tag> buf = new ArrayList<Tag>();
        List<SuggestedItem> items = find(Mode.SUGGEST, index, tokenList);

        // sort them
        for( SuggestedItem i : items)
            buf.add(new Tag(i));
        Collections.sort(buf);
        items.clear();
        for (Tag t : buf)
            items.add(t.item);

        return items;
    }

    static final class TokenList {
        private final String[] tokens;

        public TokenList(String tokenList) {
            tokens = tokenList.split("(?<=\\s)(?=\\S)");
        }

        public int length() { return tokens.length; }

        /**
         * Returns {@link List} such that its <tt>get(end)</tt>
         * returns the concatanation of [token_start,...,token_end]
         * (both end inclusive.)
         */
        public List<String> subSequence(final int start) {
            return new AbstractList<String>() {
                public String get(int index) {
                    StringBuilder buf = new StringBuilder();
                    for(int i=start; i<=start+index; i++ )
                        buf.append(tokens[i]);
                    return buf.toString().trim();
                }

                public int size() {
                    return tokens.length-start;
                }
            };
        }
    }

    private static List<SuggestedItem> find(Mode m, SearchIndex index, String tokenList) {
        TokenList tokens = new TokenList(tokenList);
        if(tokens.length()==0) return Collections.emptyList();   // no tokens given

        List<SuggestedItem>[] paths = new List[tokens.length()+1]; // we won't use [0].
        for(int i=1;i<=tokens.length();i++)
            paths[i] = new ArrayList<SuggestedItem>();

        List<SearchItem> items = new ArrayList<SearchItem>(); // items found in 1 step


        // first token
        int w=1;    // width of token
        for (String token : tokens.subSequence(0)) {
            items.clear();
            m.find(index,token,items);
            for (SearchItem si : items)
                paths[w].add(new SuggestedItem(si));
            w++;
        }

        // successive tokens
        for (int j=1; j<tokens.length(); j++) {
            // for each length
            w=1;
            for (String token : tokens.subSequence(j)) {
                // for each candidate
                for (SuggestedItem r : paths[j]) {
                    items.clear();
                    m.find(r.item.getSearchIndex(),token,items);
                    for (SearchItem i : items)
                        paths[j+w].add(new SuggestedItem(r,i));
                }
                w++;
            }
        }

        return paths[tokens.length()];
    }
}

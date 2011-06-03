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
package hudson.matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Winston Prakash
 */
public class Axis extends AxisExt{
     public Axis(String name, List<String> values) {
        super(name, values);
    }

    public Axis(String name, String... values) {
        this(name,Arrays.asList(values));        
    }
    
    /**
     * Used to build {@link AxisExt} from form.
     *
     * AxisExt with empty values need to be removed later.
     */
    @DataBoundConstructor
    public Axis(String name, String valueString) {
         super(name, valueString);
    }
    
    /**
     * Parses the submitted form (where possible values are
     * presented as a list of checkboxes) and creates an axis
     */
    public static AxisExt parsePrefixed(StaplerRequest req, String name) {
        List<String> values = new ArrayList<String>();
        String prefix = name+'.';

        Enumeration e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String paramName = (String) e.nextElement();
            if(paramName.startsWith(prefix))
                values.add(paramName.substring(prefix.length()));
        }
        if(values.isEmpty())
            return null;
        return new AxisExt(name,values);
    }
}

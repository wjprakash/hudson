/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;


/**
 * Represents health of something (typically project).
 * A number between 0-100.
 *
 * @author connollys
 * @since 1.115
 */
@ExportedBean(defaultVisibility = 2)
// this is always exported as a part of Job and never on its own, so start with 2.
public class HealthReport extends HealthReportExt {

    /**
     * Create a new HealthReportExt.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param iconUrl     The path to the icon corresponding to this {@link Action}'s health or <code>null</code> to
     *                    display the default icon corresponding to the current health score.
     *                    <p/>
     *                    If the path begins with a '/' then it will be the absolute path, otherwise the image is
     *                    assumed to be in one of <code>/images/16x16/</code>, <code>/images/24x24/</code> or
     *                    <code>/images/32x32/</code> depending on the icon size selected by the user.
     *                    When calculating the url to display for absolute paths, the getIconUrl(String) method
     *                    will replace /32x32/ in the path with the appropriate size.
     * @param description The health icon's tool-tip.
     * @deprecated since 2008-10-18.
     *     Use {@link #HealthReportExt(int, String, org.jvnet.localizer.Localizable)}
     */
    @Deprecated
    public HealthReport(int score, String iconUrl, String description) {
        this(score, iconUrl, new NonLocalizable(description));
    }

    /**
     * Create a new HealthReportExt.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param iconUrl     The path to the icon corresponding to this {@link Action}'s health or <code>null</code> to
     *                    display the default icon corresponding to the current health score.
     *                    <p/>
     *                    If the path begins with a '/' then it will be the absolute path, otherwise the image is
     *                    assumed to be in one of <code>/images/16x16/</code>, <code>/images/24x24/</code> or
     *                    <code>/images/32x32/</code> depending on the icon size selected by the user.
     *                    When calculating the url to display for absolute paths, the getIconUrl(String) method
     *                    will replace /32x32/ in the path with the appropriate size.
     * @param description The health icon's tool-tip.
     */
    public HealthReport(int score, String iconUrl, Localizable description) {
         super(score, iconUrl, description);
    }

    /**
     * Create a new HealthReportExt.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param description The health icon's tool-tip.
     * @deprecated since 2008-10-18.
     *     Use {@link #HealthReportExt(int, org.jvnet.localizer.Localizable)}
     */
    @Deprecated
    public HealthReport(int score, String description) {
        this(score, null, description);
    }

    /**
     * Create a new HealthReportExt.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param description The health icon's tool-tip.
     */
    public HealthReport(int score, Localizable description) {
        this(score, null, description);
    }

    /**
     * Create a new HealthReportExt.
     */
    public HealthReport() {
        this(100, HEALTH_UNKNOWN, Messages._HealthReport_EmptyString());
    }

    /**
     * Getter for property 'score'.
     *
     * @return The percentage health score (from 0 to 100 inclusive).
     */
    @Exported
    @Override
    public int getScore() {
        return super.getScore();
    }

     

    /**
     * Getter for property 'iconUrl'.
     *
     * @return Value for property 'iconUrl'.
     */
    @Exported
    @Override
    public String getIconUrl() {
        return super.getIconUrl();
    }

    /**
     * Getter for property 'description'.
     *
     * @return Value for property 'description'.
     */
    @Exported
    @Override
    public String getDescription() {
        return super.getDescription();
    }

     
}

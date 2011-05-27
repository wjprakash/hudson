package hudson.model;

import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.Stapler;

/**
 * {@link StatusIcon} for stock icon in HudsonExt.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.390.
 */
public final class StockStatusIcon extends AbstractStatusIcon {
    private final Localizable description;
    private final String image;

    /**
     * @param image
     *      Short file name like "folder.gif" that points to a stock icon in HudsonExt.
     * @param description
     *      Used as {@link #getDescription()}.
     */
    public StockStatusIcon(String image, Localizable description) {
        this.image = image;
        this.description = description;
    }

    public String getImageOf(String size) {
        return Stapler.getCurrentRequest().getContextPath()+HudsonExt.RESOURCE_PATH+"/images/"+size+'/'+image;
    }

    public String getDescription() {
        return description.toString(LocaleProvider.getLocale());
    }
}

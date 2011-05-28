package hudson.model;

import org.jvnet.localizer.LocaleProvider;
import org.jvnet.localizer.Localizable;

/**
 * {@link StatusIcon} for stock icon in HudsonExt.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.390.
 */
public class StockStatusIconExt extends AbstractStatusIcon {
    private final Localizable description;
    protected final String image;

    /**
     * @param image
     *      Short file name like "folder.gif" that points to a stock icon in HudsonExt.
     * @param description
     *      Used as {@link #getDescription()}.
     */
    public StockStatusIconExt(String image, Localizable description) {
        this.image = image;
        this.description = description;
    }

    public String getImageOf(String size) {
        return null;
    }

    public String getDescription() {
        return description.toString(LocaleProvider.getLocale());
    }
}

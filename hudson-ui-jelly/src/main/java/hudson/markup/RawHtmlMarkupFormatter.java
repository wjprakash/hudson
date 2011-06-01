package hudson.markup;

import org.kohsuke.stapler.DataBoundConstructor;


/**
 * {@link MarkupFormatter} that treats the input as the raw html.
 * This is the backward compatible behaviour.
 *
 * @author Kohsuke Kawaguchi
 */
public class RawHtmlMarkupFormatter extends RawHtmlMarkupFormatterExt {
    @DataBoundConstructor
    public RawHtmlMarkupFormatter() {
    }
}

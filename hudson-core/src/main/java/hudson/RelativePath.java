package hudson;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used in conjunction with {@link QueryParameter} to refer to
 * nearby parameters that belong to different parents.
 *
 * <p>
 * Currently, "..", "../..", etc. are supported to indicate
 * parameters that belong to the ancestors.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.376
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface RelativePath {
    String value();
}

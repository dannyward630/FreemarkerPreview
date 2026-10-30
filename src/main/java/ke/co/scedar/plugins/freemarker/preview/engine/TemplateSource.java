package ke.co.scedar.plugins.freemarker.preview.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Supplies template text for root-relative, normalized template names (no leading slash, no {@code ..} segments).
 * <p>
 * Implementations decide which files are reachable. The IDE implementation only serves files below the template root
 * directory; tests use an in-memory map.
 */
@FunctionalInterface
public interface TemplateSource {

    TemplateSource NONE = name -> null;

    static TemplateSource of(Map<String, String> templates) {
        return templates::get;
    }

    /**
     * Returns the template text, or {@code null} when the template does not exist or may not be read.
     */
    @Nullable String read(@NotNull String name) throws IOException;

    /**
     * A source that serves {@code text} for {@code name} (e.g. the unsaved editor content) and delegates otherwise.
     */
    default TemplateSource overlay(String name, String text) {
        TemplateSource delegate = this;
        return n -> n.equals(name) ? text : delegate.read(n);
    }
}

package ke.co.scedar.plugins.freemarker.preview.engine;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/** Resolves {@code #include}/{@code #import} paths the way FreeMarker does, but refuses anything outside the root. */
public final class TemplatePaths {

    private TemplatePaths() {
    }

    /**
     * Resolves {@code target} relative to the template {@code currentName}.
     *
     * @return the normalized root-relative name, or {@code null} when the path is unsupported or escapes the root
     * (schemes such as {@code classpath:}, backslashes, FreeMarker acquisition {@code *}, or too many {@code ..}).
     */
    public static @Nullable String resolve(String currentName, String target) {
        if (target.isEmpty() || target.indexOf(':') >= 0 || target.indexOf('\\') >= 0 || target.indexOf('*') >= 0
                || target.indexOf('\0') >= 0) {
            return null;
        }
        String combined;
        if (target.startsWith("/")) {
            combined = target;
        } else {
            int slash = currentName.lastIndexOf('/');
            combined = (slash < 0 ? "" : currentName.substring(0, slash + 1)) + target;
        }
        return normalize(combined);
    }

    /** Normalizes a path, returning {@code null} if a {@code ..} segment would leave the root. */
    public static @Nullable String normalize(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }
}

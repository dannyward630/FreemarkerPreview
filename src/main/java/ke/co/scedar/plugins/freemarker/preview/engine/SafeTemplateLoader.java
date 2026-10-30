package ke.co.scedar.plugins.freemarker.preview.engine;

import freemarker.cache.TemplateLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FreeMarker {@link TemplateLoader} backed by a {@link TemplateSource}. It never touches the file system or class path
 * by itself, re-validates every name, caps template size and limits how many templates one render may load.
 * <p>
 * When a template is not found at its exact path, ancestor directories are searched for the same trailing path
 * (like FreeMarker's {@code *} acquisition): {@code reports/fragments/layout.ftl} falls back to
 * {@code fragments/layout.ftl}. Applications often configure a loader base that makes such paths work, and the
 * preview cannot know it. Every fallback is recorded so it can be reported.
 */
final class SafeTemplateLoader implements TemplateLoader {

    static final int MAX_TEMPLATE_CHARS = 1_000_000;
    static final int MAX_LOADS = 200;

    private final TemplateSource source;
    private final AtomicInteger loads = new AtomicInteger();
    private final Map<String, String> fallbacks = new ConcurrentHashMap<>();

    SafeTemplateLoader(TemplateSource source) {
        this.source = source;
    }

    @Override
    public Object findTemplateSource(String name) throws IOException {
        String normalized = TemplatePaths.normalize(name);
        if (normalized == null || !normalized.equals(name) || TemplatePaths.resolve("", name) == null) {
            return null;
        }
        if (loads.incrementAndGet() > MAX_LOADS) {
            throw new IOException("Too many template loads (more than " + MAX_LOADS + ") in a single preview");
        }
        String text = source.read(normalized);
        if (text == null) {
            for (String candidate : ancestorCandidates(normalized)) {
                text = source.read(candidate);
                if (text != null) {
                    fallbacks.put(normalized, candidate);
                    break;
                }
            }
        }
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_TEMPLATE_CHARS) {
            throw new IOException("Template \"" + name + "\" is larger than " + MAX_TEMPLATE_CHARS + " characters");
        }
        return text;
    }

    /** Requested name to the name actually loaded, for templates found by the ancestor fallback. */
    Map<String, String> fallbacks() {
        return Map.copyOf(fallbacks);
    }

    /**
     * For {@code a/b/f/x.ftl}: {@code a/f/x.ftl}, {@code f/x.ftl}, {@code a/x.ftl}, {@code x.ftl}. Longer trailing paths
     * come first, then the nearest ancestor.
     */
    static List<String> ancestorCandidates(String name) {
        String[] segments = name.split("/");
        List<String> candidates = new ArrayList<>();
        for (int tailStart = 1; tailStart < segments.length; tailStart++) {
            String tail = String.join("/", java.util.Arrays.copyOfRange(segments, tailStart, segments.length));
            for (int prefixEnd = tailStart - 1; prefixEnd >= 0; prefixEnd--) {
                String prefix = String.join("/", java.util.Arrays.copyOfRange(segments, 0, prefixEnd));
                String candidate = prefix.isEmpty() ? tail : prefix + "/" + tail;
                if (!candidate.equals(name) && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    @Override
    public long getLastModified(Object templateSource) {
        return -1;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) {
        return new StringReader((String) templateSource);
    }

    @Override
    public void closeTemplateSource(Object templateSource) {
    }
}

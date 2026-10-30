package ke.co.scedar.plugins.freemarker.preview.render;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes project-local images and stylesheets visible in the sandboxed preview, which cannot read files itself:
 * {@code <img src="/images/logo.png">} gets a {@code data:} URI, {@code <link rel="stylesheet" href="css/app.css">}
 * becomes an inline {@code <style>}. Remote ({@code http:}, {@code https:}, {@code //}) and {@code data:} references
 * are left alone. What a path resolves to, and whether it may be read at all, is decided by the {@link Resolver}.
 */
public final class ResourceInliner {

    private static final Pattern IMG = Pattern.compile(
            "(<img\\b[^>]*?\\bsrc\\s*=\\s*)([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLESHEET_REL = Pattern.compile("\\brel\\s*=\\s*([\"'])?\\s*stylesheet",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_REFERENCES = 200;
    private ResourceInliner() {
    }

    public static String inline(String html, Resolver resolver) {
        String withImages = replaceImages(html, resolver);
        return replaceStylesheets(withImages, resolver);
    }

    /**
     * Whether a reference points at a local file rather than the network or inline data.
     */
    public static boolean isLocal(String reference) {
        String r = reference.trim().toLowerCase(Locale.ROOT);
        return !r.isEmpty() && !r.startsWith("http:") && !r.startsWith("https:") && !r.startsWith("//")
                && !r.startsWith("data:") && !r.startsWith("#") && !r.startsWith("javascript:")
                && !r.startsWith("mailto:") && !r.startsWith("about:") && !r.startsWith("blob:");
    }

    /**
     * Strips query strings, fragments and HTML entity escaping that the template engine may have added.
     */
    static String cleanPath(String reference) {
        String path = reference.trim().replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
        int cut = path.indexOf('?');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        if (path.regionMatches(true, 0, "file:", 0, 5)) {
            path = path.substring(5).replaceFirst("^/+", "/");
        }
        return path;
    }

    private static String replaceImages(String html, Resolver resolver) {
        Matcher m = IMG.matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        int count = 0;
        while (m.find()) {
            String reference = m.group(3);
            String replacement = null;
            if (count++ < MAX_REFERENCES && isLocal(reference)) {
                replacement = resolver.imageDataUri(cleanPath(reference));
            }
            String value = replacement != null ? replacement : reference;
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + m.group(2) + value + m.group(2)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String replaceStylesheets(String html, Resolver resolver) {
        Matcher m = LINK.matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        int count = 0;
        while (m.find()) {
            String tag = m.group();
            String replacement = tag;
            Matcher href = HREF.matcher(tag);
            if (count++ < MAX_REFERENCES && STYLESHEET_REL.matcher(tag).find() && href.find() && isLocal(href.group(2))) {
                String css = resolver.stylesheet(cleanPath(href.group(2)));
                if (css != null) {
                    replacement = "<style>" + css.replace("</style", "<\\/style") + "</style>";
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Resolves a local reference from the rendered page.
     */
    public interface Resolver {
        /**
         * @return a {@code data:} URI for the image, or {@code null} to leave the reference unchanged
         */
        @Nullable String imageDataUri(String path);

        /**
         * @return the stylesheet text, or {@code null} to leave the reference unchanged
         */
        @Nullable String stylesheet(String path);
    }
}

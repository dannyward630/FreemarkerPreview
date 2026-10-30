package ke.co.scedar.plugins.freemarker.preview.render;

import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the pages shown in the preview browser.
 * <p>
 * Rendered output is treated as untrusted: a Content-Security-Policy is injected that disables the template's own
 * scripts, frames, forms and plugins. Images, stylesheets and fonts may load from {@code http(s)} when remote
 * resources are allowed; otherwise only inline styles and {@code data:} resources load. Local files are never
 * reachable from the page (project images are inlined by {@link ResourceInliner} beforehand). The only script that
 * runs is a nonce-tagged helper that keeps the scroll position across refreshes.
 */
public final class PreviewHtml {

    /** Colors used for the plugin's own pages (diagnostics, empty state); rendered output keeps its own styling. */
    public record Theme(String background, String foreground, String muted, String border, String codeBackground,
                        String highlight, String error, String accent) {
        public static Theme light() {
            return new Theme("#ffffff", "#1f2328", "#6a737d", "#d0d7de", "#f6f8fa", "#ffebe9", "#cf222e", "#0969da");
        }

        public static Theme dark() {
            return new Theme("#1e1f22", "#dfe1e5", "#8c8f94", "#393b40", "#2b2d30", "#4a2b2b", "#f75464", "#548af7");
        }
    }

    private static final Pattern HEAD = Pattern.compile("<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML = Pattern.compile("<html(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTYPE = Pattern.compile("<!doctype[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();

    private PreviewHtml() {
    }

    /** The page for a successful render. */
    public static String forResult(RenderResult.Success success) {
        return forResult(success, false);
    }

    /** @param allowRemote whether images, stylesheets and fonts may load from {@code http(s)} URLs */
    public static String forResult(RenderResult.Success success, boolean allowRemote) {
        return switch (success.kind()) {
            case HTML -> sandbox(success.output(), allowRemote);
            case XML, TEXT -> sandbox("<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head>"
                    + "<body style=\"margin:0;padding:12px;\"><pre style=\"white-space:pre-wrap;word-wrap:break-word;"
                    + "font:13px/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;margin:0;\">"
                    + escape(success.output()) + "</pre></body></html>", false);
        };
    }

    /** Injects a Content-Security-Policy without remote resources, and the scroll keeper. */
    public static String sandbox(String html) {
        return sandbox(html, false);
    }

    /** Injects the Content-Security-Policy and the scroll keeper into an HTML document or fragment. */
    public static String sandbox(String html, boolean allowRemote) {
        String nonce = nonce();
        String remote = allowRemote ? " https: http:" : "";
        String inject = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
                + "img-src data:" + remote + "; style-src 'unsafe-inline' data:" + remote + "; font-src data:" + remote
                + "; media-src data:" + remote + "; script-src 'nonce-" + nonce + "'; "
                + "base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'\">"
                + "<meta name=\"referrer\" content=\"no-referrer\">"
                + "<script nonce=\"" + nonce + "\">" + SCROLL_KEEPER + "</script>";
        Matcher head = HEAD.matcher(html);
        if (head.find()) {
            return html.substring(0, head.end()) + inject + html.substring(head.end());
        }
        Matcher root = HTML.matcher(html);
        if (root.find()) {
            return html.substring(0, root.end()) + "<head>" + inject + "</head>" + html.substring(root.end());
        }
        Matcher doctype = DOCTYPE.matcher(html);
        if (doctype.lookingAt()) {
            return html.substring(0, doctype.end()) + inject + html.substring(doctype.end());
        }
        return inject + html;
    }

    private static final String SCROLL_KEEPER = "(function(){var k='fmp-scroll:';try{if(window.name.indexOf(k)===0){"
            + "var p=window.name.substring(k.length).split(',');var go=function(){window.scrollTo(+p[0],+p[1]);};"
            + "document.addEventListener('DOMContentLoaded',go);window.addEventListener('load',go);}}catch(e){}"
            + "window.addEventListener('scroll',function(){window.name=k+window.scrollX+','+window.scrollY;},"
            + "{passive:true});})();";

    public static String forDiagnostic(RenderDiagnostic d, Theme theme) {
        StringBuilder body = new StringBuilder();
        body.append("<div class=\"badge\">Preview not updated</div>");
        body.append("<h1>").append(escape(d.title())).append("</h1>");
        if (!d.location().isEmpty()) {
            body.append("<div class=\"location\">").append(escape(d.location())).append("</div>");
        }
        body.append("<pre class=\"message\">").append(escape(d.message())).append("</pre>");
        if (!d.excerpt().isEmpty()) {
            body.append("<table class=\"excerpt\">");
            for (RenderDiagnostic.SourceLine line : d.excerpt()) {
                body.append("<tr").append(line.highlighted() ? " class=\"hl\"" : "").append("><td class=\"n\">")
                        .append(line.number()).append("</td><td><pre>").append(escape(line.text()))
                        .append("</pre></td></tr>");
            }
            body.append("</table>");
        }
        if (d.hint() != null) {
            body.append("<p class=\"hint\">").append(escape(d.hint())).append("</p>");
        }
        if (d.ftlStack() != null && !d.ftlStack().isBlank()) {
            body.append("<details><summary>FreeMarker instruction stack</summary><pre class=\"stack\">")
                    .append(escape(d.ftlStack().strip())).append("</pre></details>");
        }
        body.append("<p class=\"note\">The last successful render is hidden so that it is not mistaken for current output.</p>");
        return page(theme, body.toString());
    }

    /** A neutral informational page (empty state, unsupported environment, ...). */
    public static String message(String title, String text, @Nullable List<String> details, Theme theme) {
        StringBuilder body = new StringBuilder("<div class=\"empty\"><h1>").append(escape(title)).append("</h1><p>")
                .append(escape(text)).append("</p>");
        if (details != null && !details.isEmpty()) {
            body.append("<ul>");
            details.forEach(item -> body.append("<li>").append(escape(item)).append("</li>"));
            body.append("</ul>");
        }
        return page(theme, body.append("</div>").toString());
    }

    private static String page(Theme t, String body) {
        String css = "html,body{background:" + t.background() + ";color:" + t.foreground() + ";}"
                + "body{margin:0;padding:20px 24px;font:13px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "'Inter',Roboto,sans-serif;}"
                + "h1{font-size:16px;font-weight:600;margin:6px 0 4px;color:" + t.error() + ";}"
                + ".empty h1{color:" + t.foreground() + ";}"
                + ".badge{display:inline-block;font-size:11px;letter-spacing:.02em;text-transform:uppercase;"
                + "color:" + t.muted() + ";border:1px solid " + t.border() + ";border-radius:10px;padding:1px 8px;}"
                + ".location{color:" + t.muted() + ";margin-bottom:12px;}"
                + "pre{font:12px/1.45 ui-monospace,'JetBrains Mono',SFMono-Regular,Menlo,Consolas,monospace;margin:0;"
                + "white-space:pre-wrap;word-wrap:break-word;}"
                + ".message{background:" + t.codeBackground() + ";border:1px solid " + t.border() + ";"
                + "border-radius:6px;padding:10px 12px;}"
                + ".excerpt{border-collapse:collapse;margin:12px 0;width:100%;background:" + t.codeBackground() + ";"
                + "border:1px solid " + t.border() + ";border-radius:6px;}"
                + ".excerpt td{padding:1px 8px;vertical-align:top;}"
                + ".excerpt .n{color:" + t.muted() + ";text-align:right;width:1%;user-select:none;}"
                + ".excerpt .hl{background:" + t.highlight() + ";}"
                + ".hint{border-left:3px solid " + t.accent() + ";padding:2px 10px;margin:12px 0;}"
                + "details{margin-top:12px;}summary{cursor:pointer;color:" + t.muted() + ";}"
                + ".stack{margin-top:6px;color:" + t.muted() + ";}"
                + ".note{color:" + t.muted() + ";font-size:12px;margin-top:16px;}"
                + ".empty{max-width:520px;margin:48px auto;text-align:center;color:" + t.muted() + ";}"
                + ".empty ul{text-align:left;}";
        return sandbox("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + css + "</style></head><body>"
                + body + "</body></html>");
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String nonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}

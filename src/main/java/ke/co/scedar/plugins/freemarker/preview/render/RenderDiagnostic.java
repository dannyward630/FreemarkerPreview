package ke.co.scedar.plugins.freemarker.preview.render;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A readable description of why a preview could not be produced.
 *
 * @param templateName template the problem is in (may be an included template), if known
 * @param line         1-based line, or 0 when unknown
 * @param ftlStack     FreeMarker's instruction stack, for rendering errors
 * @param excerpt      source lines around {@code line}
 * @param hint         a suggestion on how to fix it, if one applies
 */
public record RenderDiagnostic(Kind kind, String title, String message, @Nullable String templateName, int line,
                               int column, @Nullable String ftlStack, List<SourceLine> excerpt, @Nullable String hint) {

    public static RenderDiagnostic simple(Kind kind, String title, String message, @Nullable String hint) {
        return new RenderDiagnostic(kind, title, message, null, 0, 0, null, List.of(), hint);
    }

    public String location() {
        StringBuilder sb = new StringBuilder();
        if (templateName != null) {
            sb.append(templateName);
        }
        if (line > 0) {
            sb.append(sb.isEmpty() ? "" : " · ").append("line ").append(line);
            if (column > 0) {
                sb.append(", column ").append(column);
            }
        }
        return sb.toString();
    }

    public enum Kind {TEMPLATE_SYNTAX, RENDERING, MISSING_TEMPLATE, SAMPLE_DATA, TIMEOUT, OUTPUT_TOO_LARGE, CANCELLED, INTERNAL}

    public record SourceLine(int number, String text, boolean highlighted) {
    }
}

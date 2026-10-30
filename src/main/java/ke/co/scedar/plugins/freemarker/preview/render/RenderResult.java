package ke.co.scedar.plugins.freemarker.preview.render;

/**
 * Either the rendered output or a diagnostic. Never both, so a failure can never show stale output.
 */
public sealed interface RenderResult {

    enum OutputKind {HTML, XML, TEXT}

    record Success(String output, OutputKind kind, long millis) implements RenderResult {
    }

    record Failure(RenderDiagnostic diagnostic) implements RenderResult {
    }
}

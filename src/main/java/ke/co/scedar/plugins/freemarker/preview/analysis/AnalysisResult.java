package ke.co.scedar.plugins.freemarker.preview.analysis;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The outcome of analyzing a template.
 *
 * @param roots        variables that must come from the data model, in order of first use
 * @param declarations {@code @ftlvariable} declarations (variable name to Java type text)
 * @param warnings     constructs that could not be analyzed (dynamic includes, missing includes, ...)
 * @param templates    names of all templates that were analyzed (the main template first, then includes)
 * @param parseError   set when the main template could not be parsed; the other fields are then empty
 */
public record AnalysisResult(Map<String, VariableShape> roots,
                             Map<String, String> declarations,
                             List<String> warnings,
                             List<String> templates,
                             @Nullable ParseFailure parseError) {

    public record ParseFailure(String message, int line, int column) {
    }

    public boolean isSuccessful() {
        return parseError == null;
    }
}

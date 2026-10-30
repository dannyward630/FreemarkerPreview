package ke.co.scedar.plugins.freemarker.preview.render;

import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;

import java.time.Duration;
import java.util.Map;

/**
 * @param templateName   root-relative name of the template (its extension selects the output format)
 * @param templateText   current, possibly unsaved, template text
 * @param source         where included and imported templates are read from
 * @param sampleData     JSON-compatible sample data (see {@link ModelConverter})
 * @param timeout        rendering is interrupted after this long
 * @param maxOutputChars rendering stops once the output exceeds this size
 */
public record RenderRequest(String templateName, String templateText, TemplateSource source,
                            Map<String, Object> sampleData, Duration timeout, int maxOutputChars) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 5_000_000;

    public RenderRequest(String templateName, String templateText, TemplateSource source,
                         Map<String, Object> sampleData) {
        this(templateName, templateText, source, sampleData, DEFAULT_TIMEOUT, DEFAULT_MAX_OUTPUT_CHARS);
    }
}

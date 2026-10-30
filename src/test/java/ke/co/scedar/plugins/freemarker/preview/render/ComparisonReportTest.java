package ke.co.scedar.plugins.freemarker.preview.render;

import ke.co.scedar.plugins.freemarker.preview.analysis.AnalysisResult;
import ke.co.scedar.plugins.freemarker.preview.analysis.TemplateAnalyzer;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import ke.co.scedar.plugins.freemarker.preview.model.SampleModelGenerator;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A report shaped like real-world ones (the fixture is made up): the main template lives in {@code reports/} and
 * imports layout/header/footer fragments from a sibling {@code fragments/} folder; the fragments' macros read their own
 * data-model variables, and the report uses BigDecimal methods, the {@code ?string["..."]} format syntax, dynamic map
 * keys and fraction thresholds.
 */
public class ComparisonReportTest {

    private static final String REPORT = "reports/comparison-report.ftl";
    private static final List<String> FILES = List.of(REPORT, "fragments/layout.ftl", "fragments/header.ftl",
            "fragments/footer.ftl");

    private TemplateSource source;
    private String report;
    private AnalysisResult analysis;
    private Map<String, Object> model;

    @SuppressWarnings("unchecked")
    private static <T> T at(Object value, Object... path) {
        Object current = value;
        for (Object step : path) {
            current = step instanceof Integer i ? ((List<Object>) current).get(i) : ((Map<String, Object>) current).get(step);
        }
        return (T) current;
    }

    @Before
    public void setUp() throws IOException {
        Map<String, String> files = new HashMap<>();
        for (String name : FILES) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/comparison/" + name)) {
                assertNotNull(in);
                files.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        source = TemplateSource.of(files);
        report = files.get(REPORT);
        analysis = new TemplateAnalyzer().analyze(REPORT, report, source);
        assertNull(String.valueOf(analysis.parseError()), analysis.parseError());
        model = new SampleModelGenerator().generate(analysis, TypeResolver.NONE).model();
    }

    @Test
    public void importedFragmentsAreAnalyzed() {
        assertTrue(analysis.roots().keySet().containsAll(List.of("model", "date", "generatedAt")));
        for (String fragment : List.of("fragments/layout.ftl", "fragments/header.ftl", "fragments/footer.ftl")) {
            assertTrue(fragment + " in " + analysis.templates(), analysis.templates().stream().anyMatch(t -> t.endsWith(fragment)));
        }
        assertTrue("fragments found next to reports/ are reported", analysis.warnings().stream()
                .anyMatch(w -> w.contains("reports/fragments/layout.ftl") && w.contains("fragments/layout.ftl")));
        assertTrue(((String) at(model, "model", "clientConfig", "logoPath")).startsWith("data:image/svg+xml"));
        assertEquals("jane.doe@example.com", at(model, "model", "clientConfig", "contactEmail"));
        assertEquals("Acme Corp", at(model, "model", "clientConfig", "name"));
    }

    @Test
    public void sampleValuesMatchHowTheTemplateUsesThem() {
        assertEquals("names beat a \"N/A\" default", "2026-01-15", model.get("reportDate"));
        assertTrue(model.get("institutionCount") instanceof Number);
        assertTrue(((String) model.get("currentTerm")).startsWith("Term "));
        assertTrue("BigDecimal methods mean a number", model.get("expectedChange") instanceof Number);
        assertTrue(at(model, "previousTotals", "balance") instanceof Number);
        assertTrue("?string[\"#,##0.00\"] means a number", at(model, "institutionMetrics", 0, "collectedFees") instanceof Number);
        assertTrue(at(model, "institutionComparisons", 0, "students") instanceof Number);
        assertEquals("compared with 0.9 -> a ratio", new BigDecimal("0.92"), model.get("currentCollectionRate"));
        assertEquals(new BigDecimal("0.84"), at(model, "institutionMetrics", 1, "collectionRate"));
        assertEquals("Riverside Academy", at(model, "Q", "getInstitutionName()"));
        Map<String, Object> classBreakdown = at(model, "classBreakdown");
        Map<String, Object> previous = at(model, "previousDataByInstitution");
        assertEquals("maps keyed by the same variable share keys", classBreakdown.keySet(), previous.keySet());
        assertTrue(classBreakdown.containsKey("INS-1001"));
    }

    @Test
    public void reportRendersWithItsFragments() {
        RenderResult result = new TemplateRenderer().render(new RenderRequest(REPORT, report, source, model));
        if (result instanceof RenderResult.Failure(RenderDiagnostic diagnostic)) {
            throw new AssertionError(diagnostic.title() + " at " + diagnostic.location() + ": "
                    + diagnostic.message());
        }
        String out = ((RenderResult.Success) result).output();
        assertTrue(out.contains("Branch Comparison Report"));
        assertTrue("header fragment", out.contains("jane.doe@example.com") && out.contains("Date: <strong>"));
        assertTrue("footer fragment", out.contains("<footer"));
        assertTrue("institution names via the method stub", out.contains("Riverside Academy"));
        assertTrue("ratings from fraction thresholds", out.contains("Excellent"));
        assertTrue("class breakdown keyed by institution code", out.contains("INS-1001"));
        assertFalse(out.contains("Unknown Institution"));
    }
}

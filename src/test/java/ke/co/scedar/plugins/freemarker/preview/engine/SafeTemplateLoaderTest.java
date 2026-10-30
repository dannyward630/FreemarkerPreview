package ke.co.scedar.plugins.freemarker.preview.engine;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SafeTemplateLoaderTest {

    @Test
    public void ancestorCandidatesPreferLongerTrailingPaths() {
        assertEquals(List.of("fragments/layout.ftl", "reports/layout.ftl", "layout.ftl"),
                SafeTemplateLoader.ancestorCandidates("reports/fragments/layout.ftl"));
    }

    @Test
    public void exactPathsWinAndFallbacksAreRecorded() throws Exception {
        SafeTemplateLoader loader = new SafeTemplateLoader(TemplateSource.of(Map.of(
                "fragments/layout.ftl", "root", "reports/own.ftl", "own")));
        assertEquals("own", loader.findTemplateSource("reports/own.ftl"));
        assertTrue(loader.fallbacks().isEmpty());
        assertEquals("root", loader.findTemplateSource("reports/fragments/layout.ftl"));
        assertEquals(Map.of("reports/fragments/layout.ftl", "fragments/layout.ftl"), loader.fallbacks());
        assertNull(loader.findTemplateSource("reports/missing.ftl"));
    }
}

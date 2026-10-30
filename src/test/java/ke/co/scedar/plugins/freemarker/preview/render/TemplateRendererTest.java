package ke.co.scedar.plugins.freemarker.preview.render;

import ke.co.scedar.plugins.freemarker.preview.analysis.TemplateAnalyzer;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplatePaths;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import ke.co.scedar.plugins.freemarker.preview.model.Json;
import ke.co.scedar.plugins.freemarker.preview.model.ModelMerger;
import ke.co.scedar.plugins.freemarker.preview.model.SampleModelGenerator;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic.Kind;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.*;

public class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    private RenderResult render(String name, String template, Map<String, Object> data, TemplateSource source) {
        return renderer.render(new RenderRequest(name, template, source, data));
    }

    private String renderOk(String template, String json) throws Exception {
        RenderResult result = render("page.ftl", template, Json.parseObject(json), TemplateSource.NONE);
        if (result instanceof RenderResult.Failure(RenderDiagnostic diagnostic)) {
            throw new AssertionError(diagnostic.title() + ": " + diagnostic.message());
        }
        return ((RenderResult.Success) result).output();
    }

    private RenderDiagnostic renderFail(String name, String template, String json, TemplateSource source) throws Exception {
        RenderResult result = render(name, template, Json.parseObject(json), source);
        assertTrue("expected failure but got: " + result, result instanceof RenderResult.Failure);
        return ((RenderResult.Failure) result).diagnostic();
    }

    @Test
    public void rendersWithGeneratedModelEndToEnd() {
        String template = """
                <h1>Hello ${customer.name}!</h1>
                <#list orders as order><p>${order.productName}: ${order.total}</p></#list>
                <#if customer.vip>VIP</#if> ${customer.since?date?string("yyyy")} ${(customer.nickname)!"-"}
                """;
        var analysis = new TemplateAnalyzer().analyze("page.ftl", template, TemplateSource.NONE);
        var model = new SampleModelGenerator().generate(analysis, TypeResolver.NONE).model();
        RenderResult result = render("page.ftl", template, model, TemplateSource.NONE);
        assertTrue(String.valueOf(result), result instanceof RenderResult.Success);
        String out = ((RenderResult.Success) result).output();
        assertTrue(out, out.contains("Hello Jane Doe!"));
        assertTrue(out, out.contains("<p>Laptop: ") && out.contains("<p>Monitor: "));
        assertTrue(out, out.contains("VIP") && out.contains("2026"));
        assertEquals(RenderResult.OutputKind.HTML, ((RenderResult.Success) result).kind());
    }

    @Test
    public void syntaxErrorsReportLocationAndExcerpt() throws Exception {
        RenderDiagnostic d = renderFail("page.ftl", "<p>ok</p>\n<#if x>\n<p>never closed</p>\n", "{}", TemplateSource.NONE);
        assertEquals(Kind.TEMPLATE_SYNTAX, d.kind());
        assertTrue(d.line() >= 2);
        assertFalse(d.excerpt().isEmpty());
        assertEquals("page.ftl", d.templateName());
    }

    @Test
    public void missingValuesAreExplained() throws Exception {
        RenderDiagnostic d = renderFail("page.ftl", "line 1\nHello ${user.name}", "{\"user\": {}}", TemplateSource.NONE);
        assertEquals(Kind.RENDERING, d.kind());
        assertEquals("Missing value", d.title());
        assertEquals(2, d.line());
        assertTrue(d.message().contains("user.name"));
        assertTrue(d.excerpt().stream().anyMatch(l -> l.highlighted() && l.text().contains("${user.name}")));
        assertTrue(d.hint() != null && d.hint().contains("default"));
    }

    @Test
    public void defaultsAndExistenceChecksWorkWithNullOverrides() throws Exception {
        String template = "${user.nickname!\"anonymous\"} <#if user.coupon??>coupon<#else>no coupon</#if>";
        var generated = Json.parseObject("{\"user\": {\"nickname\": \"Jane\", \"coupon\": \"SAVE10\"}}");
        var overrides = Json.parseObject("{\"user\": {\"nickname\": null, \"coupon\": null}}");
        RenderResult result = render("page.ftl", template, ModelMerger.merge(generated, overrides), TemplateSource.NONE);
        assertEquals("anonymous no coupon", ((RenderResult.Success) result).output());
        assertEquals("Jane coupon", renderOk(template, Json.write(generated)));
    }

    @Test
    public void manualOverridesChangeOutput() throws Exception {
        var generated = Json.parseObject("{\"customer\": {\"name\": \"Jane Doe\", \"city\": \"Nairobi\"}}");
        var overrides = Json.parseObject("{\"customer\": {\"name\": \"Ada Lovelace\"}}");
        RenderResult result = render("page.ftl", "${customer.name} / ${customer.city}",
                ModelMerger.merge(generated, overrides), TemplateSource.NONE);
        assertEquals("Ada Lovelace / Nairobi", ((RenderResult.Success) result).output());
    }

    @Test
    public void isoStringsBehaveAsDatesAndStrings() throws Exception {
        // Like a Java date, string built-ins see the formatted date; ?is_string shows it is also a plain string.
        assertEquals("2026|15.01.2026|true|true|09:30",
                renderOk("${d?string(\"yyyy\")}|${d?string(\"dd.MM.yyyy\")}|${d?is_date?c}|${d?is_string?c}|${t?string(\"HH:mm\")}",
                        "{\"d\": \"2026-01-15\", \"t\": \"2026-01-15T09:30:00\"}"));
    }

    @Test
    public void methodAndDirectiveStubs() throws Exception {
        // The directive stub prints its sample text ("[") and then the nested content.
        assertEquals("$1.00 [<b>body</b>", renderOk("${fmt.money(1, \"USD\")} <@box><b>body</b></@box>",
                "{\"fmt\": {\"money()\": \"$1.00\"}, \"@box\": \"[\"}"));
    }

    @Test
    public void numbersAnswerBigDecimalMethods() throws Exception {
        assertEquals("0.2500|1|75.00|0.25|2.50|N",
                renderOk("${a.divide(b, 4, \"roundHalfUp\")?string[\"0.0000\"]}|${a.compareTo(0)}"
                                + "|${b.subtract(a)?string[\"0.00\"]}|${a.divide(b, 4, \"HALF_UP\").doubleValue()?c}"
                                + "|${a.multiply(0.1)?string(\"0.00\")}|${(a.compareTo(b) gt 0)?then(\"Y\", \"N\")}",
                        "{\"a\": 25, \"b\": 100}"));
        RenderDiagnostic d = renderFail("a.ftl", "${a.divide(0)}", "{\"a\": 1}", TemplateSource.NONE);
        assertTrue(d.message(), d.message().contains("divide"));
    }

    @Test
    public void localImagesAndStylesheetsAreInlined() {
        String html = ResourceInliner.inline("<img src=\"/images/logo.png?v=2\"><img src='https://cdn.example.com/x.png'>"
                + "<img src=\"data:image/gif;base64,AA\"><link rel=\"stylesheet\" href=\"css/app.css\">"
                + "<link rel=\"icon\" href=\"favicon.ico\">", new ResourceInliner.Resolver() {
            @Override
            public String imageDataUri(String path) {
                return path.equals("/images/logo.png") ? "data:image/png;base64,LOGO" : null;
            }

            @Override
            public String stylesheet(String path) {
                return path.equals("css/app.css") ? "h1{color:red}" : null;
            }
        });
        assertTrue(html, html.contains("<img src=\"data:image/png;base64,LOGO\">"));
        assertTrue("remote images are left to the browser", html.contains("https://cdn.example.com/x.png"));
        assertTrue(html.contains("data:image/gif;base64,AA"));
        assertTrue(html.contains("<style>h1{color:red}</style>"));
        assertTrue("non-stylesheet links are untouched", html.contains("<link rel=\"icon\" href=\"favicon.ico\">"));
    }

    @Test
    public void remoteResourcesAreAllowedOnlyWhenRequested() {
        String strict = PreviewHtml.sandbox("<p>x</p>", false);
        String remote = PreviewHtml.sandbox("<p>x</p>", true);
        assertFalse(strict.contains("https:"));
        assertTrue(remote.contains("img-src data: https: http:"));
        assertTrue("scripts stay blocked", remote.matches("(?s).*script-src 'nonce-[^']+';.*"));
    }

    @Test
    public void htmlTemplatesAutoEscape() {
        RenderResult result = render("page.ftlh", "${s} ${s?no_esc}", Map.of("s", "<b>x</b>"), TemplateSource.NONE);
        assertEquals("&lt;b&gt;x&lt;/b&gt; <b>x</b>", ((RenderResult.Success) result).output());
    }

    @Test
    public void xmlTemplatesAreReportedAsXml() {
        RenderResult result = render("feed.ftlx", "<feed>${t}</feed>", Map.of("t", "a & b"), TemplateSource.NONE);
        assertEquals("<feed>a &amp; b</feed>", ((RenderResult.Success) result).output());
        assertEquals(RenderResult.OutputKind.XML, ((RenderResult.Success) result).kind());
    }

    @Test
    public void plainTextOutputIsDetected() throws Exception {
        RenderResult result = render("mail.ftl", "Dear ${n},\nThanks!", Map.of("n", "Jane"), TemplateSource.NONE);
        assertEquals(RenderResult.OutputKind.TEXT, ((RenderResult.Success) result).kind());
    }

    @Test
    public void relativeIncludesAndImportsResolveInsideTheRoot() {
        TemplateSource source = TemplateSource.of(Map.of(
                "mail/parts/footer.ftl", "-- ${company}",
                "lib/macros.ftl", "<#macro shout text>${text?upper_case}!</#macro>"));
        RenderResult result = render("mail/welcome.ftl",
                "<#import \"/lib/macros.ftl\" as m><@m.shout text=\"hi\"/> <#include \"parts/footer.ftl\">",
                Map.of("company", "Acme"), source);
        assertEquals("HI! -- Acme", ((RenderResult.Success) result).output());
    }

    @Test
    public void includesOutsideTheRootOrMissingAreErrors() throws Exception {
        RenderDiagnostic outside = renderFail("mail/a.ftl", "<#include \"../../secret.ftl\">", "{}",
                TemplateSource.of(Map.of("secret.ftl", "leak")));
        assertTrue(outside.kind() == Kind.MISSING_TEMPLATE || outside.kind() == Kind.RENDERING);
        RenderDiagnostic missing = renderFail("a.ftl", "<#include \"nope.ftl\">", "{}", TemplateSource.NONE);
        assertEquals(Kind.MISSING_TEMPLATE, missing.kind());
        assertEquals("ok", renderOk("<#include \"nope.ftl\" ignore_missing=true>ok", "{}"));
    }

    @Test
    public void javaAccessIsBlocked() throws Exception {
        RenderDiagnostic newBuiltIn = renderFail("a.ftl",
                "<#assign ex = \"freemarker.template.utility.Execute\"?new()>${ex(\"id\")}", "{}", TemplateSource.NONE);
        assertEquals(Kind.RENDERING, newBuiltIn.kind());
        RenderDiagnostic api = renderFail("a.ftl", "${m?api.size()}", "{\"m\": {\"a\": 1}}", TemplateSource.NONE);
        assertEquals(Kind.RENDERING, api.kind());
    }

    @Test(timeout = 10_000)
    public void runawayLoopsTimeOut() {
        RenderResult result = renderer.render(new RenderRequest("a.ftl", "<#list 1.. as i><#assign x = i></#list>",
                TemplateSource.NONE, Map.of(), Duration.ofMillis(300), 1000));
        assertEquals(Kind.TIMEOUT, ((RenderResult.Failure) result).diagnostic().kind());
        assertFalse("interrupt flag must not leak", Thread.currentThread().isInterrupted());
    }

    @Test(timeout = 10_000)
    public void outputIsCapped() {
        RenderResult result = renderer.render(new RenderRequest("a.ftl", "<#list 1.. as i>0123456789</#list>",
                TemplateSource.NONE, Map.of(), Duration.ofSeconds(5), 1000));
        assertEquals(Kind.OUTPUT_TOO_LARGE, ((RenderResult.Failure) result).diagnostic().kind());
    }

    @Test(timeout = 10_000)
    public void infiniteRecursionIsReported() throws Exception {
        RenderDiagnostic d = renderFail("a.ftl", "<#macro r>x<@r/></#macro><@r/>", "{}", TemplateSource.NONE);
        assertTrue(d.kind() == Kind.RENDERING || d.kind() == Kind.OUTPUT_TOO_LARGE);
    }

    @Test
    public void sandboxInjectsContentSecurityPolicy() {
        String page = PreviewHtml.sandbox("<!DOCTYPE html><html><head><title>t</title></head><body>x</body></html>");
        int csp = page.indexOf("Content-Security-Policy");
        assertTrue(csp > page.indexOf("<head>") && csp < page.indexOf("<title>"));
        assertTrue(page.contains("default-src 'none'"));
        assertTrue(PreviewHtml.sandbox("<p>fragment</p>").startsWith("<meta http-equiv=\"Content-Security-Policy\""));
        String text = PreviewHtml.forResult(new RenderResult.Success("<script>alert(1)</script>", RenderResult.OutputKind.TEXT, 1));
        assertTrue(text.contains("&lt;script&gt;"));
    }

    @Test
    public void templatePathsStayInsideTheRoot() {
        assertEquals("a/c.ftl", TemplatePaths.resolve("a/b.ftl", "c.ftl"));
        assertEquals("c.ftl", TemplatePaths.resolve("a/b.ftl", "/c.ftl"));
        assertEquals("c.ftl", TemplatePaths.resolve("a/b.ftl", "../c.ftl"));
        assertNull(TemplatePaths.resolve("a/b.ftl", "../../c.ftl"));
        assertNull(TemplatePaths.resolve("a.ftl", "classpath:/x.ftl"));
        assertNull(TemplatePaths.resolve("a.ftl", "..\\x.ftl"));
        assertNull(TemplatePaths.resolve("a.ftl", "*/x.ftl"));
    }
}

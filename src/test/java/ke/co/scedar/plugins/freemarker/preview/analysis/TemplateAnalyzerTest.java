package ke.co.scedar.plugins.freemarker.preview.analysis;

import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape.Hint;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TemplateAnalyzerTest {

    private static AnalysisResult analyze(String template) {
        return analyze(template, TemplateSource.NONE);
    }

    private static AnalysisResult analyze(String template, TemplateSource source) {
        AnalysisResult result = new TemplateAnalyzer().analyze("page.ftl", template, source);
        assertNull("unexpected parse error: " + result.parseError(), result.parseError());
        return result;
    }

    private static List<String> rootNames(AnalysisResult result) {
        return List.copyOf(result.roots().keySet());
    }

    @Test
    public void simpleInterpolation() {
        AnalysisResult result = analyze("Hello ${name}!");
        assertEquals(List.of("name"), rootNames(result));
        assertTrue(result.roots().get("name").has(Hint.PRINTED));
    }

    @Test
    public void nestedProperties() {
        VariableShape customer = analyze("${customer.address.city} ${customer['email']}").roots().get("customer");
        assertTrue(customer.has(Hint.HASH));
        assertEquals(List.of("address", "email"), List.copyOf(customer.properties().keySet()));
        assertTrue(customer.properties().get("address").properties().containsKey("city"));
    }

    @Test
    public void loopVariablesAreNotRootVariables() {
        AnalysisResult result = analyze("""
                <h1>Hello ${customer.name}!</h1>
                <#list orders as order>
                    <p>${order.productName}: ${order.total}</p>
                </#list>
                """);
        assertEquals(List.of("customer", "orders"), rootNames(result));
        VariableShape orders = result.roots().get("orders");
        assertTrue(orders.has(Hint.SEQUENCE));
        assertEquals(List.of("productName", "total"), List.copyOf(orders.element().properties().keySet()));
    }

    @Test
    public void nestedListsAndItemsDirective() {
        AnalysisResult result = analyze("""
                <#list orders as order><#list order.lines as line>${line.sku}</#list></#list>
                <#list tags><ul><#items as tag><li>${tag}</li></#items></ul><#else>none</#list>
                <#list prices as product, price>${product}=${price}</#list>
                """);
        assertEquals(List.of("orders", "tags", "prices"), rootNames(result));
        VariableShape lines = result.roots().get("orders").element().properties().get("lines");
        assertTrue(lines.has(Hint.SEQUENCE));
        assertTrue(lines.element().properties().containsKey("sku"));
        assertTrue(result.roots().get("tags").has(Hint.SEQUENCE));
        assertTrue(result.roots().get("tags").element().has(Hint.PRINTED));
        assertTrue(result.roots().get("prices").has(Hint.KEY_VALUE_LISTED));
    }

    @Test
    public void conditionalDirectivesInferTypes() {
        Map<String, VariableShape> roots = analyze("""
                <#if user.active && count gt 3>a<#elseif plan == "pro">b<#elseif ratio < 0.5>c<#else>d</#if>
                <#switch level><#case 1>one<#break><#default>other</#switch>
                """).roots();
        assertTrue(roots.get("user").properties().get("active").has(Hint.BOOLEAN));
        assertTrue(roots.get("count").has(Hint.NUMBER));
        assertTrue(roots.get("plan").has(Hint.STRING));
        assertTrue(roots.get("ratio").has(Hint.NUMBER));
        assertTrue(roots.get("level").has(Hint.NUMBER));
    }

    @Test
    public void defaultsAndMissingValueChecks() {
        Map<String, VariableShape> roots = analyze("""
                ${nickname!"Guest"} ${(account.plan.name)!} ${retries!3}
                <#if coupon??>has coupon</#if> <#if notes?has_content>${notes}</#if>
                """).roots();
        assertTrue(roots.get("nickname").has(Hint.OPTIONAL));
        assertTrue("defaults are weak hints", roots.get("nickname").has(Hint.DEFAULT_STRING));
        assertFalse(roots.get("nickname").has(Hint.STRING));
        assertTrue(roots.get("retries").has(Hint.DEFAULT_NUMBER));
        assertTrue(roots.get("account").properties().get("plan").properties().get("name").has(Hint.OPTIONAL));
        assertTrue(roots.get("coupon").has(Hint.OPTIONAL));
        assertTrue(roots.get("notes").has(Hint.OPTIONAL));
    }

    @Test
    public void commentsAndStringsWithVariableLikeSyntaxAreIgnored() {
        AnalysisResult result = analyze("""
                <#-- ${secret.token} is not used -->
                ${r"${rawNotAVariable}"}
                <#noparse>${hidden.value}</#noparse>
                ${"interpolated ${real.value} inside a string"}
                <script>var x = "${js.value}";</script>
                """);
        assertEquals(List.of("real", "js"), rootNames(result));
    }

    @Test
    public void nestedExpressionsAndRepeatedReferences() {
        Map<String, VariableShape> roots = analyze("""
                ${(order.total * taxRate)?string("0.00")} ${order.total} ${order.customer.name}
                ${products[selected].title} ${products[0].title?upper_case} ${order.createdAt?date}
                ${people?filter(p -> p.age > 18)?map(p -> p.name)?join(", ")}
                """).roots();
        VariableShape order = roots.get("order");
        assertEquals(List.of("total", "customer", "createdAt"), List.copyOf(order.properties().keySet()));
        assertTrue(order.properties().get("total").has(Hint.NUMBER));
        assertTrue(order.properties().get("createdAt").has(Hint.DATE));
        assertTrue(roots.get("taxRate").has(Hint.NUMBER));
        assertTrue(roots.containsKey("selected"));
        assertTrue(roots.get("products").has(Hint.SEQUENCE));
        assertTrue(roots.get("products").element().properties().get("title").has(Hint.STRING));
        VariableShape person = roots.get("people").element();
        assertTrue(person.properties().get("age").has(Hint.NUMBER));
        assertTrue(person.properties().containsKey("name"));
        assertFalse("lambda parameters are local", roots.containsKey("p"));
    }

    @Test
    public void assignmentsMacrosAndImportsAreLocal() {
        AnalysisResult result = analyze("""
                <#import "/lib/utils.ftl" as utils>
                <#assign c = customer>
                <#assign title = pageTitle!"Home">
                <#macro card item heading="x">${item.label} ${heading} ${siteName}<#local tmp = 1>${tmp}<#nested></#macro>
                <@card item=featured/> <@utils.badge/> ${c.name} ${title}
                <#function double n><#return n * 2></#function>${double(2)}
                """);
        assertEquals(List.of("customer", "pageTitle", "siteName", "featured"), rootNames(result));
        assertTrue("alias propagates properties", result.roots().get("customer").properties().containsKey("name"));
    }

    @Test
    public void assignedVariablesUsedInMacrosDefinedEarlier() {
        AnalysisResult result = analyze("""
                <#macro header>${title}</#macro>
                <#assign title = "Welcome">
                <@header/>
                """);
        assertTrue(result.roots().isEmpty());
    }

    @Test
    public void methodCallsAndUserDirectives() {
        Map<String, VariableShape> roots = analyze("${fmt.money(order.total)} <@layout title=\"x\">body</@layout>")
                .roots();
        assertTrue(roots.get("fmt").properties().get("money").has(Hint.METHOD));
        assertTrue(roots.get("layout").has(Hint.DIRECTIVE));
        assertTrue(roots.containsKey("order"));
    }

    @Test
    public void includesAreAnalyzedAndResolvedRelatively() {
        TemplateSource source = TemplateSource.of(Map.of(
                "emails/parts/footer.ftl", "${company.name} <#include \"../../shared/legal.ftl\">",
                "shared/legal.ftl", "${legal.text}"));
        AnalysisResult result = new TemplateAnalyzer().analyze("emails/welcome.ftl",
                "${user.name}<#include \"parts/footer.ftl\"><#include \"missing.ftl\"><#include \"../../etc/passwd\">",
                source);
        assertEquals(List.of("user", "company", "legal"), rootNames(result));
        assertEquals(List.of("emails/welcome.ftl", "emails/parts/footer.ftl", "shared/legal.ftl"), result.templates());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("missing.ftl")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("outside the template root")));
    }

    @Test
    public void recursiveIncludesTerminate() {
        TemplateSource source = TemplateSource.of(Map.of("a.ftl", "${a}<#include \"b.ftl\">", "b.ftl", "${b}<#include \"a.ftl\">"));
        AnalysisResult result = new TemplateAnalyzer().analyze("a.ftl", "${a}<#include \"b.ftl\">", source);
        assertEquals(List.of("a", "b"), rootNames(result));
    }

    @Test
    public void importedLibrariesAreAnalyzedInTheirOwnNamespace() {
        TemplateSource source = TemplateSource.of(Map.of("lib/ui.ftl", """
                <#assign libLocal = "x">
                <#macro card item>${item.title} ${site.name} ${libLocal}</#macro>
                """));
        AnalysisResult result = analyze("<#import \"/lib/ui.ftl\" as ui><@ui.card item=featured/>", source);
        assertEquals(List.of("site", "featured"), rootNames(result));
    }

    @Test
    public void dynamicKeysJavaNumberMethodsAndLegacyFormatCalls() {
        Map<String, VariableShape> roots = analyze("""
                <#list groups as code, members>${byCode[code].label}</#list>
                ${total.divide(count, 2, "roundHalfUp")} ${amount?string["#,##0.00"]} ${xs[i + 1]}
                <#list 0..3 as n>${ys[n]}</#list>
                """).roots();
        assertEquals("code", roots.get("groups").keyName());
        assertTrue(roots.get("byCode").has(Hint.KEY_VALUE_LISTED));
        assertEquals("code", roots.get("byCode").keyName());
        assertTrue(roots.get("byCode").element().properties().containsKey("label"));
        assertTrue(roots.get("total").has(Hint.NUMBER));
        assertTrue(roots.get("total").properties().isEmpty());
        assertTrue(roots.get("count").has(Hint.NUMBER));
        assertTrue(roots.get("amount").has(Hint.NUMBER));
        assertTrue(roots.get("xs").has(Hint.SEQUENCE));
        assertTrue(roots.get("ys").has(Hint.SEQUENCE));
        assertFalse(roots.containsKey("n"));
    }

    @Test
    public void ftlVariableDeclarationsAreCollected() {
        AnalysisResult result = analyze("<#-- @ftlvariable name=\"customer\" type=\"com.acme.Customer\" -->${customer.name}");
        assertEquals("com.acme.Customer", result.roots().get("customer").declaredType());
    }

    @Test
    public void malformedTemplateReportsParseError() {
        AnalysisResult result = new TemplateAnalyzer().analyze("page.ftl", "line one\n<#if user.active>\nnever closed",
                TemplateSource.NONE);
        assertNotNull(result.parseError());
        assertFalse(result.isSuccessful());
        assertTrue(result.parseError().line() >= 2);
    }

    @Test
    public void unclosedInterpolationIsAParseError() {
        assertNotNull(new TemplateAnalyzer().analyze("page.ftl", "Hello ${user.name", TemplateSource.NONE).parseError());
    }
}

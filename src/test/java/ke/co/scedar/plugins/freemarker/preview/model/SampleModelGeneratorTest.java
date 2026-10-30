package ke.co.scedar.plugins.freemarker.preview.model;

import ke.co.scedar.plugins.freemarker.preview.analysis.AnalysisResult;
import ke.co.scedar.plugins.freemarker.preview.analysis.TemplateAnalyzer;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SampleModelGeneratorTest {

    private static Map<String, Object> generate(String template) {
        return generate(template, TypeResolver.NONE);
    }

    private static Map<String, Object> generate(String template, TypeResolver resolver) {
        AnalysisResult analysis = new TemplateAnalyzer().analyze("page.ftl", template, TemplateSource.NONE);
        return new SampleModelGenerator().generate(analysis, resolver).model();
    }

    @SuppressWarnings("unchecked")
    private static <T> T at(Object value, Object... path) {
        Object current = value;
        for (Object step : path) {
            current = step instanceof Integer i ? ((List<Object>) current).get(i) : ((Map<String, Object>) current).get(step);
        }
        return (T) current;
    }

    @Test
    public void generatesTheExampleModel() {
        Map<String, Object> model = generate("""
                <h1>Hello ${customer.name}!</h1>
                <#list orders as order>
                    <p>${order.productName}: ${order.total}</p>
                </#list>
                """);
        assertEquals("Jane Doe", at(model, "customer", "name"));
        List<Object> orders = at(model, "orders");
        assertEquals(2, orders.size());
        assertEquals("Laptop", at(orders, 0, "productName"));
        assertEquals(85000, (int) at(orders, 0, "total"));
        assertEquals("Monitor", at(orders, 1, "productName"));
        assertEquals(25000, (int) at(orders, 1, "total"));
    }

    @Test
    public void textParsedAsNumberAfterCleanUpIsANumericString() {
        // Real-world reports sum amounts the application sends as formatted text.
        Map<String, Object> model = generate("""
                <#list transactions as transaction>
                    <#assign total = total!0 + (transaction.amount?replace(",", "")?number)>
                </#list>
                <#list classes as class>${(class.balance?string)?replace(",", "")?trim?number}</#list>
                ${flag?trim?boolean?then("yes", "no")}
                """);
        assertEquals("85000", at(model, "transactions", 0, "amount"));
        assertEquals("85000", at(model, "classes", 0, "balance"));
        assertEquals("true", model.get("flag"));
    }

    @Test
    public void infersScalarTypesFromUsage() {
        Map<String, Object> model = generate("""
                <#if subscribed>yes</#if> ${visits + 1} ${joined?date} ${meeting?time} ${updated?datetime}
                ${label?upper_case} ${flag?string("on", "off")} ${note!"none"} ${createdAt}
                """);
        assertEquals(Boolean.TRUE, model.get("subscribed"));
        assertTrue(model.get("visits") instanceof Number);
        assertEquals("2026-01-15", model.get("joined"));
        assertEquals("09:30:00", model.get("meeting"));
        assertEquals("2026-01-15T09:30:00", model.get("updated"));
        assertTrue(model.get("label") instanceof String);
        assertEquals(Boolean.TRUE, model.get("flag"));
        assertTrue(model.get("note") instanceof String);
        assertEquals("name heuristic", "2026-01-15T09:30:00", model.get("createdAt"));
    }

    @Test
    public void namesPickRealisticValues() {
        Map<String, Object> model = generate("${email} ${user.firstName} ${user.phone} ${shop.companyName} ${avatarUrl} ${logo}");
        assertEquals("jane.doe@example.com", model.get("email"));
        assertEquals("Jane", at(model, "user", "firstName"));
        assertTrue(((String) at(model, "user", "phone")).startsWith("+1 555"));
        assertEquals("Acme Corp", at(model, "shop", "companyName"));
        assertTrue("image URLs get a placeholder image", ((String) model.get("avatarUrl")).startsWith("data:image/svg+xml"));
        assertTrue(((String) generate("${profileUrl}").get("profileUrl")).startsWith("https://example.com/"));
        assertTrue("images are inline data URIs", ((String) model.get("logo")).startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    public void propertyValuesUseTheirParentAsContext() {
        Map<String, Object> model = generate("${customer.name} ${company.name} ${product.name} ${city.name} ${order.id} ${author.firstName}");
        assertEquals("Jane Doe", at(model, "customer", "name"));
        assertEquals("Acme Corp", at(model, "company", "name"));
        assertEquals("Laptop", at(model, "product", "name"));
        assertEquals("Nairobi", at(model, "city", "name"));
        assertEquals(1001, (int) at(model, "order", "id"));
        assertEquals("Jane", at(model, "author", "firstName"));
        Map<String, Object> users = generate("${user.name} ${user.username}");
        assertEquals("Jane Doe", at(users, "user", "name"));
        assertEquals("jdoe", at(users, "user", "username"));
    }

    @Test
    public void collectionsAreNonEmpty() {
        Map<String, Object> model = generate("<#list tags as tag>${tag}</#list> ${items?size} <#list stock as name, qty>${qty}</#list>");
        assertEquals(List.of("Tag 1", "Tag 2"), model.get("tags"));
        assertEquals(2, ((List<?>) model.get("items")).size());
        assertEquals(2, ((Map<?, ?>) model.get("stock")).size());
    }

    @Test
    public void methodsAndDirectivesUseStubKeys() {
        Map<String, Object> model = generate("${fmt.money(order.total)} <@layout>x</@layout> ${translate(\"key\")}");
        assertTrue(((Map<?, ?>) model.get("fmt")).containsKey("money()"));
        assertTrue(model.containsKey("@layout"));
        assertTrue(model.containsKey("translate()"));
    }

    @Test
    public void generationIsDeterministic() {
        String template = "${a.b} <#list xs as x>${x.y} ${x.z?date}</#list>";
        assertEquals(generate(template), generate(template));
    }

    @Test
    public void usesResolvedJavaTypes() {
        TypeInfo status = TypeInfo.enumOf("com.acme.Status", List.of("NEW", "PAID"));
        TypeInfo order = TypeInfo.bean("com.acme.Order", property -> switch (property) {
            case "status" -> status;
            case "placedOn" -> TypeInfo.of(TypeInfo.Kind.DATETIME, "java.time.Instant");
            case "code" -> TypeInfo.of(TypeInfo.Kind.INTEGER, "int");
            default -> null;
        });
        TypeResolver resolver = (name, shape) -> name.equals("orders")
                ? new TypeResolver.Resolution(TypeInfo.sequenceOf(order), "test") : null;
        Map<String, Object> model = generate("<#list orders as o>${o.status} ${o.placedOn} ${o.code} ${o.unknown}</#list>", resolver);
        assertEquals("NEW", at(model, "orders", 0, "status"));
        assertEquals("PAID", at(model, "orders", 1, "status"));
        assertEquals("2026-01-15T09:30:00", at(model, "orders", 0, "placedOn"));
        assertTrue(at(model, "orders", 0, "code") instanceof Number);
        assertTrue("unresolved properties fall back", at(model, "orders", 0, "unknown") instanceof String);
    }

    @Test
    public void reportsTypeOriginsAndWarnings() {
        AnalysisResult analysis = new TemplateAnalyzer().analyze("page.ftl",
                "<#-- @ftlvariable name=\"user\" type=\"com.acme.Missing\" -->${user.name}<#include \"nope.ftl\">",
                TemplateSource.NONE);
        List<String> notes = new SampleModelGenerator().generate(analysis, TypeResolver.NONE).notes();
        assertTrue(notes.stream().anyMatch(n -> n.contains("com.acme.Missing") && n.contains("could not be resolved")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("nope.ftl")));
    }
}

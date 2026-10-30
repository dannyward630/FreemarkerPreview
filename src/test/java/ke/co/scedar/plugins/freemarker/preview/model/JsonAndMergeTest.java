package ke.co.scedar.plugins.freemarker.preview.model;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonAndMergeTest {

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> list = Arrays.asList(keyValues);
        for (int i = 0; i < list.size(); i += 2) {
            map.put((String) list.get(i), list.get(i + 1));
        }
        return map;
    }

    @Test
    public void roundTripsValues() throws Exception {
        String json = "{\"s\": \"a \\\"q\\\" \\u00e9\\n\", \"i\": 85000, \"d\": 1.25, \"b\": true, \"n\": null, "
                + "\"l\": [1, {\"x\": []}], \"o\": {}}";
        Map<String, Object> parsed = Json.parseObject(json);
        assertEquals("a \"q\" é\n", parsed.get("s"));
        assertEquals(85000L, parsed.get("i"));
        assertEquals(new BigDecimal("1.25"), parsed.get("d"));
        assertEquals(Boolean.TRUE, parsed.get("b"));
        assertTrue(parsed.containsKey("n"));
        assertNull(parsed.get("n"));
        assertEquals(parsed, Json.parseObject(Json.write(parsed)));
    }

    @Test
    public void reportsErrorPosition() {
        try {
            Json.parse("{\n  \"a\": 1,\n  b: 2\n}");
            fail();
        } catch (Json.ParseException e) {
            assertEquals(3, e.line());
            assertEquals(3, e.column());
        }
    }

    @Test
    public void rejectsInvalidJson() {
        for (String bad : List.of("", "{", "{\"a\":}", "{\"a\":1,}", "[1 2]", "{\"a\":1}{", "{'a':1}", "{\"a\":01}",
                "{\"a\":1,\"a\":2}", "[\"unterminated]", "nul")) {
            try {
                Json.parse(bad);
                fail("accepted: " + bad);
            } catch (Json.ParseException expected) {
                // expected
            }
        }
    }

    @Test
    public void requiresAnObjectAtTheTop() {
        try {
            Json.parseObject("[1]");
            fail();
        } catch (Json.ParseException e) {
            assertTrue(e.getMessage().contains("JSON object"));
        }
    }

    @Test
    public void overridesWinAndNewGeneratedKeysAreKept() {
        Map<String, Object> generated = map("customer", map("name", "Jane Doe", "email", "jane@example.com"),
                "orders", List.of(map("total", 1)), "fresh", "generated");
        Map<String, Object> overrides = map("customer", map("name", "Ada"), "orders", List.of(), "removed", null);
        Map<String, Object> merged = ModelMerger.merge(generated, overrides);
        assertEquals(map("name", "Ada", "email", "jane@example.com"), merged.get("customer"));
        assertEquals(List.of(), merged.get("orders"));
        assertEquals("generated", merged.get("fresh"));
        assertTrue(merged.containsKey("removed"));
        assertNull(merged.get("removed"));
        assertFalse("inputs are not mutated", ((Map<?, ?>) generated.get("customer")).get("name").equals("Ada"));
    }
}

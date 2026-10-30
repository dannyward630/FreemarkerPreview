package ke.co.scedar.plugins.freemarker.preview.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A type established from project sources (e.g. a Java bean or record), used to pick better sample values.
 * Only what can be resolved reliably is described; anything else is {@link Kind#UNKNOWN} and falls back to
 * template-based inference.
 */
public interface TypeInfo {

    enum Kind { STRING, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, TIME, ENUM, SEQUENCE, MAP, BEAN, UNKNOWN }

    Kind kind();

    /** Human-readable type name, e.g. {@code com.acme.Customer} or {@code List<Order>}. */
    String displayName();

    /** Item type of a {@link Kind#SEQUENCE}, value type of a {@link Kind#MAP}. */
    default @Nullable TypeInfo elementType() {
        return null;
    }

    /** Type of a {@link Kind#BEAN} property (getter, record component) or the return type of a method with that name. */
    default @Nullable TypeInfo property(String name) {
        return null;
    }

    default List<String> enumConstants() {
        return List.of();
    }

    static TypeInfo of(Kind kind, String displayName) {
        return new Simple(kind, displayName, null, Map.of(), List.of());
    }

    static TypeInfo sequenceOf(TypeInfo element) {
        return new Simple(Kind.SEQUENCE, "List<" + element.displayName() + ">", element, Map.of(), List.of());
    }

    static TypeInfo enumOf(String displayName, List<String> constants) {
        return new Simple(Kind.ENUM, displayName, null, Map.of(), constants);
    }

    /** A bean whose property types are computed on demand (allows recursive types). */
    static TypeInfo bean(String displayName, Function<String, TypeInfo> properties) {
        return new TypeInfo() {
            @Override
            public Kind kind() {
                return Kind.BEAN;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public @Nullable TypeInfo property(String name) {
                return properties.apply(name);
            }
        };
    }

    record Simple(Kind kind, String displayName, @Nullable TypeInfo elementType, Map<String, TypeInfo> properties,
                  List<String> enumConstants) implements TypeInfo {
        @Override
        public @Nullable TypeInfo property(String name) {
            return properties.get(name);
        }
    }
}

package ke.co.scedar.plugins.freemarker.preview.model;

import ke.co.scedar.plugins.freemarker.preview.analysis.AnalysisResult;
import ke.co.scedar.plugins.freemarker.preview.analysis.Names;
import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape;
import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape.Hint;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an {@link AnalysisResult} into a JSON-compatible sample data model (maps, lists, strings, numbers, booleans).
 * <p>
 * Conventions understood by {@link ModelConverter}:
 * <ul>
 *     <li>{@code "name()"} - a method stub; calling {@code name(...)} with any arguments returns the value;</li>
 *     <li>{@code "@name"} - a user-defined directive stub; {@code <@name>...</@name>} prints the value (if a string)
 *     followed by the nested content;</li>
 *     <li>ISO-8601 strings ({@code 2026-01-15}, {@code 2026-01-15T09:30:00}, {@code 09:30:00}) act as dates and as strings.</li>
 * </ul>
 */
public final class SampleModelGenerator {

    public static final String METHOD_SUFFIX = "()";
    public static final String DIRECTIVE_PREFIX = "@";
    public static final int DEFAULT_COLLECTION_SIZE = 2;
    private static final int MAX_DEPTH = 10;

    private final int collectionSize;

    public SampleModelGenerator() {
        this(DEFAULT_COLLECTION_SIZE);
    }

    public SampleModelGenerator(int collectionSize) {
        this.collectionSize = Math.max(1, collectionSize);
    }

    /** @param notes how types were inferred and what could not be analyzed, for display next to the model */
    public record GeneratedModel(Map<String, Object> model, List<String> notes) {
    }

    public GeneratedModel generate(AnalysisResult analysis, TypeResolver resolver) {
        Map<String, Object> model = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (Map.Entry<String, VariableShape> root : analysis.roots().entrySet()) {
            String name = root.getKey();
            VariableShape shape = root.getValue();
            TypeResolver.Resolution resolution = resolver.resolve(name, shape);
            TypeInfo type = resolution == null ? null : resolution.type();
            if (resolution != null) {
                notes.add(name + ": " + type.displayName() + " (" + resolution.origin() + ")");
            } else if (shape.declaredType() != null) {
                notes.add(name + ": declared type " + shape.declaredType() + " could not be resolved; using template-based sample data");
            }
            put(model, name, name, shape, type, 0, 1);
        }
        notes.addAll(analysis.warnings());
        return new GeneratedModel(model, notes);
    }

    /**
     * @param key       the key in the model
     * @param valueName the name used to pick a sample value (adds context, e.g. {@code companyName} for {@code company.name})
     */
    private void put(Map<String, Object> target, String key, String valueName, VariableShape shape,
                     @Nullable TypeInfo type, int index, int depth) {
        if (shape.has(Hint.DIRECTIVE)) {
            target.put(DIRECTIVE_PREFIX + key, "");
        } else if (shape.has(Hint.METHOD) && shape.methodResultOrNull() != null) {
            target.put(key + METHOD_SUFFIX, value(shape.methodResultOrNull(), methodValueName(valueName), type, index, depth));
        } else {
            target.put(key, value(shape, valueName, type, index, depth));
        }
    }

    /** {@code getInstitutionName} is named like {@code institutionName}. */
    private static String methodValueName(String method) {
        for (String prefix : List.of("get", "is", "find", "fetch", "lookup", "resolve", "compute", "format", "to")) {
            if (method.length() > prefix.length() && method.startsWith(prefix)
                    && Character.isUpperCase(method.charAt(prefix.length()))) {
                String rest = method.substring(prefix.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }

    /** {@code company} + {@code name} gives {@code companyName}; multi-word keys such as {@code firstName} are kept. */
    private static String contextualName(String parentName, String key) {
        return Names.words(key).size() == 1 && !Names.words(parentName).isEmpty()
                ? parentName + Character.toUpperCase(key.charAt(0)) + key.substring(1)
                : key;
    }

    private Object value(VariableShape shape, String name, @Nullable TypeInfo type, int index, int depth) {
        if (depth > MAX_DEPTH) {
            return SampleValues.string(name, index);
        }
        if (type != null && type.kind() != TypeInfo.Kind.UNKNOWN) {
            Object typed = typed(shape, name, type, index, depth);
            if (typed != null) {
                return typed;
            }
        }
        return fromShape(shape, name, index, depth);
    }

    private Object fromShape(VariableShape shape, String name, int index, int depth) {
        boolean hasProperties = !shape.properties().isEmpty();
        if (shape.has(Hint.KEY_VALUE_LISTED) && !hasProperties) {
            return keyValues(shape, name, null, depth);
        }
        if (shape.has(Hint.SEQUENCE) || shape.elementOrNull() != null && !hasProperties) {
            return list(shape.element(), shape.element().name(), null, depth);
        }
        if (hasProperties || shape.has(Hint.HASH)) {
            return object(shape, name, null, index, depth);
        }
        return scalar(shape, name, index);
    }

    private static Object scalar(VariableShape shape, String name, int index) {
        if (shape.has(Hint.DATETIME)) {
            return SampleValues.dateTime(index);
        }
        if (shape.has(Hint.DATE)) {
            return SampleValues.date(index);
        }
        if (shape.has(Hint.TIME)) {
            return SampleValues.time(index);
        }
        if (shape.has(Hint.FRACTION)) {
            return SampleValues.fraction(index);
        }
        if (shape.has(Hint.NUMBER)) {
            return SampleValues.number(name, index);
        }
        if (shape.has(Hint.BOOLEAN_STRING)) {
            return "true";
        }
        if (shape.has(Hint.STRING)) {
            return SampleValues.string(name, index);
        }
        if (shape.has(Hint.BOOLEAN)) {
            return Boolean.TRUE;
        }
        SampleValues.Kind kind = SampleValues.guessKind(name);
        if (kind == SampleValues.Kind.BOOLEAN && shape.has(Hint.PRINTED)) {
            // FreeMarker cannot print a boolean with plain ${x}; the template treats it as text.
            kind = SampleValues.Kind.STRING;
        }
        if (kind == SampleValues.Kind.STRING) {
            // The name says nothing; fall back to the type of a default such as x!0. (x!"N/A" says little, so a
            // name like reportDate or institutionCount wins over it.)
            if (shape.has(Hint.DEFAULT_NUMBER)) {
                kind = SampleValues.Kind.NUMBER;
            } else if (shape.has(Hint.DEFAULT_BOOLEAN) && !shape.has(Hint.PRINTED)) {
                kind = SampleValues.Kind.BOOLEAN;
            }
        }
        return SampleValues.of(kind, name, index);
    }

    private @Nullable Object typed(VariableShape shape, String name, TypeInfo type, int index, int depth) {
        return switch (type.kind()) {
            case STRING -> shape.has(Hint.DATE) || shape.has(Hint.DATETIME) || shape.has(Hint.TIME)
                    ? scalar(shape, name, index) : SampleValues.string(name, index);
            case INTEGER, DECIMAL -> SampleValues.number(name, index);
            case BOOLEAN -> Boolean.TRUE;
            case DATE -> SampleValues.date(index);
            case DATETIME -> SampleValues.dateTime(index);
            case TIME -> SampleValues.time(index);
            case ENUM -> type.enumConstants().isEmpty() ? SampleValues.string(name, index)
                    : type.enumConstants().get(index % type.enumConstants().size());
            case SEQUENCE -> list(shape.element(), shape.element().name(), type.elementType(), depth);
            case MAP -> shape.properties().isEmpty()
                    ? keyValues(shape, name, type.elementType(), depth)
                    : object(shape, name, TypeInfo.bean(type.displayName(), ignored -> type.elementType()), index, depth);
            case BEAN -> shape.properties().isEmpty() ? null : object(shape, name, type, index, depth);
            case UNKNOWN -> null;
        };
    }

    private Map<String, Object> object(VariableShape shape, String name, @Nullable TypeInfo type, int index, int depth) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (Map.Entry<String, VariableShape> property : shape.properties().entrySet()) {
            String key = property.getKey();
            put(object, key, contextualName(name, key), property.getValue(), type == null ? null : type.property(key),
                    index, depth + 1);
        }
        return object;
    }

    private List<Object> list(VariableShape element, String elementName, @Nullable TypeInfo elementType, int depth) {
        List<Object> list = new ArrayList<>(collectionSize);
        for (int i = 0; i < collectionSize; i++) {
            list.add(value(element, elementName, elementType, i, depth + 1));
        }
        return list;
    }

    private Map<String, Object> keyValues(VariableShape shape, String name, @Nullable TypeInfo valueType, int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        String valueName = shape.element().name();
        String keyName = shape.keyName();
        for (int i = 0; i < collectionSize; i++) {
            // Keys come from the key's name (institutionCode -> "INS-1001", ...), so two maps keyed by the same
            // variable get matching keys and lookups like byInstitution[institutionCode] find their values.
            String key = keyName != null ? String.valueOf(SampleValues.string(keyName, i)) : Names.singular(name) + (i + 1);
            map.put(map.containsKey(key) ? key + " " + (i + 1) : key, value(shape.element(), valueName, valueType, i, depth + 1));
        }
        return map;
    }
}

package ke.co.scedar.plugins.freemarker.preview.analysis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a template reveals about one value: how it is used (hints), which properties are read from it, and the shape of
 * its elements when it is listed. Shapes are mutable while the analyzer walks the template; repeated references to the
 * same variable or property update the same shape.
 */
public final class VariableShape {

    public enum Hint {
        /** Properties are read with {@code .name} or {@code ["name"]}. */
        HASH,
        /** Listed with {@code #list x as item}, indexed with a number, or used with sequence built-ins. */
        SEQUENCE,
        /** Listed as {@code #list x as key, value}. */
        KEY_VALUE_LISTED,
        STRING,
        NUMBER,
        BOOLEAN,
        /** A string that must parse as a boolean ({@code ?boolean}). */
        BOOLEAN_STRING,
        DATE,
        DATETIME,
        TIME,
        /** Printed with {@code ${...}}. */
        PRINTED,
        /** Called as a method: {@code x(...)}. */
        METHOD,
        /** Called as a user-defined directive: {@code <@x/>}. */
        DIRECTIVE,
        /** Guarded with a default ({@code x!}) or an existence check ({@code x??}, {@code ?has_content}). */
        OPTIONAL,
        /** Compared with a fraction such as {@code 0.9}: a ratio rather than a whole number. */
        FRACTION,
        /**
         * Weak hints from a default literal ({@code x!"N/A"}): the fallback says little about the real value, so the
         * variable's name takes precedence over these.
         */
        DEFAULT_STRING,
        DEFAULT_NUMBER,
        DEFAULT_BOOLEAN
    }

    private final String name;
    private final EnumSet<Hint> hints = EnumSet.noneOf(Hint.class);
    private final Map<String, VariableShape> properties = new LinkedHashMap<>();
    private @Nullable VariableShape element;
    private @Nullable VariableShape methodResult;
    private @Nullable String declaredType;
    private @Nullable String preferredName;
    private @Nullable String keyName;

    public VariableShape(@NotNull String name) {
        this.name = name;
    }

    /** The name used to pick sample values: the template's loop variable name if one was bound to this shape. */
    public @NotNull String name() {
        return preferredName != null ? preferredName : name;
    }

    /** Records the loop variable name the template author chose for these items (the first one wins). */
    void preferName(String loopVariable) {
        if (preferredName == null) {
            preferredName = loopVariable;
        }
    }

    /** For maps with dynamic keys: the name of the key ({@code #list m as institutionCode, v}, {@code m[institutionCode]}). */
    public @Nullable String keyName() {
        return keyName;
    }

    void setKeyName(String name) {
        if (keyName == null) {
            keyName = name;
        }
    }

    public VariableShape hint(Hint hint) {
        hints.add(hint);
        return this;
    }

    public boolean has(Hint hint) {
        return hints.contains(hint);
    }

    public Set<Hint> hints() {
        return Collections.unmodifiableSet(hints);
    }

    public VariableShape property(String propertyName) {
        hints.add(Hint.HASH);
        return properties.computeIfAbsent(propertyName, VariableShape::new);
    }

    public Map<String, VariableShape> properties() {
        return Collections.unmodifiableMap(properties);
    }

    /** The shape of the items of a sequence (or of the values of a hash listed as key/value pairs). */
    public VariableShape element() {
        if (element == null) {
            element = new VariableShape(Names.singular(name));
        }
        return element;
    }

    public @Nullable VariableShape elementOrNull() {
        return element;
    }

    /** The shape of the value returned when this value is called as a method. */
    public VariableShape methodResult() {
        hints.add(Hint.METHOD);
        if (methodResult == null) {
            methodResult = new VariableShape(name);
        }
        return methodResult;
    }

    public @Nullable VariableShape methodResultOrNull() {
        return methodResult;
    }

    /** Java type declared for a root variable with {@code <#-- @ftlvariable name="x" type="..." -->}. */
    public @Nullable String declaredType() {
        return declaredType;
    }

    void setDeclaredType(@Nullable String declaredType) {
        this.declaredType = declaredType;
    }

    @Override
    public String toString() {
        return name + hints + (properties.isEmpty() ? "" : properties.values())
                + (element == null ? "" : "[" + element + "]");
    }
}

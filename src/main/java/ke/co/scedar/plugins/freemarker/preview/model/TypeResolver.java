package ke.co.scedar.plugins.freemarker.preview.model;

import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the type of a root data-model variable from project information.
 */
@FunctionalInterface
public interface TypeResolver {

    TypeResolver NONE = (name, shape) -> null;

    /**
     * @param name  root variable name
     * @param shape what the template does with it; {@link VariableShape#declaredType()} carries an {@code @ftlvariable} type
     * @return the resolved type and where it came from, or {@code null} to use template-based inference
     */
    @Nullable Resolution resolve(String name, VariableShape shape);

    /**
     * @param origin short explanation shown to the user, e.g. {@code "@ftlvariable"} or {@code "project class"}
     */
    record Resolution(TypeInfo type, String origin) {
    }
}

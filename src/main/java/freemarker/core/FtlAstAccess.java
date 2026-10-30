package freemarker.core;

import freemarker.template.Template;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only bridge to FreeMarker's AST.
 * <p>
 * FreeMarker exposes the parsed tree through {@link Template#getRootTreeNode()}, but the accessors needed to walk
 * expressions (node type, parameters and their roles) are package-private. This class lives in the {@code freemarker.core}
 * package of the plugin's own class loader, which is also the loader of the bundled FreeMarker jar, so the access is
 * legal at runtime. It only reads the tree; it never evaluates anything.
 * <p>
 * FreeMarker marks its AST classes {@code @deprecated} to flag them as internal API (there is no public replacement),
 * which is why all such access is isolated in this class.
 */
@SuppressWarnings("deprecation")
public final class FtlAstAccess {

    private FtlAstAccess() {
    }

    /** One parameter of an AST node: its FreeMarker role name (e.g. {@code "left-hand operand"}) and value. */
    public record Param(String role, Object value) {
    }

    /** The root element of a parsed template. */
    public static Object root(Template template) {
        return template.getRootTreeNode();
    }

    public static boolean isNode(Object o) {
        return o instanceof TemplateObject;
    }

    public static boolean isExpression(Object o) {
        return o instanceof Expression;
    }

    public static boolean isElement(Object o) {
        return o instanceof TemplateElement;
    }

    /** FreeMarker's node type symbol, e.g. {@code "#list"}, {@code "."}, {@code "?upper_case"}, {@code "${...}"}. */
    public static String nodeType(Object node) {
        return ((TemplateObject) node).getNodeTypeSymbol();
    }

    /** The AST node class name, e.g. {@code "Identifier"}, {@code "Dot"}, {@code "MethodCall"}. */
    public static String kind(Object node) {
        return node.getClass().getSimpleName();
    }

    public static String canonicalForm(Object node) {
        return ((TemplateObject) node).getCanonicalForm();
    }

    public static int beginLine(Object node) {
        return ((TemplateObject) node).getBeginLine();
    }

    public static List<Param> params(Object node) {
        TemplateObject t = (TemplateObject) node;
        int count = t.getParameterCount();
        List<Param> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new Param(t.getParameterRole(i).getName(), t.getParameterValue(i)));
        }
        return result;
    }

    public static List<Object> children(Object element) {
        TemplateElement e = (TemplateElement) element;
        int count = e.getChildCount();
        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(_CoreAPI.getChildElement(e, i));
        }
        return result;
    }

    /** Whether a string literal contains no interpolations, i.e. its value is known at parse time. */
    public static boolean isStaticStringLiteral(Object node) {
        return node instanceof StringLiteral
                && params(node).stream().noneMatch(p -> p.value() instanceof TemplateObject);
    }

    public static boolean isStringLiteral(Object node) {
        return node instanceof StringLiteral;
    }

    /** The value of a static string literal (see {@link #isStaticStringLiteral(Object)}). */
    public static String staticStringValue(Object node) {
        return ((StringLiteral) node).getAsString();
    }

    public static boolean isNumberLiteral(Object node) {
        return node instanceof NumberLiteral;
    }

    public static Number numberValue(Object node) {
        return ((NumberLiteral) node).getAsNumber();
    }

    public static boolean isBooleanLiteral(Object node) {
        return node instanceof BooleanLiteral;
    }

    public static boolean isRange(Object node) {
        return node instanceof Range;
    }

    public static boolean isLambda(Object node) {
        return node instanceof LocalLambdaExpression;
    }

    /** Makes FreeMarker check {@link Thread#isInterrupted()} inside loops and directive calls of the template. */
    public static void addInterruptionChecks(Template template) {
        _CoreAPI.addThreadInterruptedChecks(template);
    }

    /** Whether the throwable is FreeMarker's signal that a template run was stopped by thread interruption. */
    public static boolean isInterruption(Throwable t) {
        return t instanceof ThreadInterruptionSupportTemplatePostProcessor.TemplateProcessingThreadInterruptedException;
    }
}

package ke.co.scedar.plugins.freemarker.preview.analysis;

import freemarker.core.FtlAstAccess;
import freemarker.core.FtlAstAccess.Param;
import freemarker.core.ParseException;
import freemarker.template.Template;
import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape.Hint;
import ke.co.scedar.plugins.freemarker.preview.engine.PreviewConfiguration;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplatePaths;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the data-model variables a template needs by walking FreeMarker's own parse tree.
 * <p>
 * Because it works on the AST, comments, string literals and text that merely look like {@code ${...}} are never
 * mistaken for references, and scoping is respected: loop variables, macro parameters, lambda parameters,
 * {@code #assign}/{@code #local}/{@code #global} targets, macro names and {@code #import} namespaces are not reported
 * as data-model variables. Templates are only parsed, never executed.
 */
public final class TemplateAnalyzer {

    static final int MAX_INCLUDE_DEPTH = 8;

    private static final Pattern FTL_VARIABLE = Pattern.compile(
            "@ftlvariable\\s+name\\s*=\\s*\"([^\"]+)\"\\s+type\\s*=\\s*\"([^\"]+)\"");

    private static final Set<String> STRING_BUILT_INS = Set.of(
            "upper_case", "lower_case", "cap_first", "uncap_first", "capitalize", "trim", "length", "html", "xml",
            "xhtml", "rtf", "url", "url_path", "js_string", "json_string", "j_string", "word_list", "split",
            "contains", "starts_with", "ends_with", "index_of", "last_index_of", "replace", "substring", "left_pad",
            "right_pad", "chop_linebreak", "truncate", "truncate_w", "truncate_c", "truncate_m", "keep_after",
            "keep_before", "keep_after_last", "keep_before_last", "remove_beginning", "remove_ending",
            "ensure_starts_with", "ensure_ends_with", "matches", "groups", "eval", "eval_json", "interpret",
            "c_lower_case", "c_upper_case", "markup_string", "blank_to_null", "empty_to_null", "trim_to_null",
            "upperCase", "lowerCase", "capFirst", "uncapFirst", "jsString", "jsonString", "jString", "wordList",
            "startsWith", "endsWith", "indexOf", "lastIndexOf", "leftPad", "rightPad", "keepAfter", "keepBefore");
    private static final Set<String> NUMBER_BUILT_INS = Set.of(
            "abs", "round", "floor", "ceiling", "int", "short", "long", "byte", "double", "float", "is_infinite",
            "is_nan", "lower_abc", "upper_abc", "number_to_date", "number_to_time", "number_to_datetime", "number",
            "isInfinite", "isNan", "lowerAbc", "upperAbc", "numberToDate", "numberToTime", "numberToDatetime");
    private static final Set<String> SEQUENCE_BUILT_INS = Set.of(
            "size", "first", "last", "join", "seq_contains", "seq_index_of", "seq_last_index_of", "sort", "sort_by",
            "reverse", "chunk", "filter", "map", "take_while", "drop_while", "min", "max", "sequence",
            "seqContains", "seqIndexOf", "seqLastIndexOf", "sortBy", "takeWhile", "dropWhile");
    /**
     * Built-ins whose result is the same sequence (possibly reordered or filtered).
     */
    private static final Set<String> SAME_SEQUENCE_BUILT_INS = Set.of(
            "sort", "sort_by", "sortBy", "reverse", "filter", "take_while", "takeWhile", "drop_while", "dropWhile",
            "sequence");
    private static final Set<String> LAMBDA_ELEMENT_BUILT_INS = Set.of(
            "filter", "map", "take_while", "takeWhile", "drop_while", "dropWhile");
    private static final Set<String> OPTIONAL_BUILT_INS = Set.of(
            "has_content", "hasContent", "if_exists", "ifExists", "default", "exists");
    /**
     * Java methods of {@code BigDecimal}/{@code Number} that templates call when the application exposes numbers with
     * methods (BeansWrapper). The receiver is a number; for the arithmetic ones, so are the arguments.
     */
    private static final Set<String> NUMBER_METHODS = Set.of("divide", "multiply", "add", "subtract", "remainder",
            "compareTo", "max", "min", "pow", "abs", "negate", "signum", "setScale", "stripTrailingZeros",
            "doubleValue", "floatValue", "intValue", "longValue", "shortValue", "byteValue", "toPlainString",
            "intValueExact", "longValueExact", "scale", "precision", "movePointLeft", "movePointRight");
    private static final Set<String> NUMBER_ARGUMENT_METHODS = Set.of("divide", "multiply", "add", "subtract",
            "remainder", "compareTo", "max", "min");

    /**
     * Built-ins that pass the value through unchanged.
     */
    private static final Set<String> PASS_THROUGH_BUILT_INS = Set.of("no_esc", "noEsc", "esc", "if_exists",
            "ifExists", "default");

    private static AnalysisResult failure(String message, int line, int column) {
        return new AnalysisResult(Map.of(), Map.of(), List.of(), List.of(),
                new AnalysisResult.ParseFailure(message, line, column));
    }

    /**
     * Analyzes {@code text} as the template named {@code templateName} (root-relative, used to resolve relative
     * includes). Included templates are read from {@code source} and analyzed too, as {@code #include} shares scope.
     */
    public AnalysisResult analyze(String templateName, String text, TemplateSource source) {
        TemplateSource overlay = source.overlay(templateName, text);
        PreviewConfiguration configuration = new PreviewConfiguration(overlay, false);
        Template template;
        try {
            template = configuration.getTemplate(templateName);
        } catch (ParseException e) {
            return failure(e.getEditorMessage(), e.getLineNumber(), e.getColumnNumber());
        } catch (IOException e) {
            return failure(String.valueOf(e.getMessage()), 0, 0);
        }
        Walker walker = new Walker(configuration, null);
        walker.walkTemplate(template, templateName);
        return new AnalysisResult(walker.roots, walker.declarations, List.copyOf(walker.warnings),
                List.copyOf(walker.templates), null);
    }

    /**
     * A lexical scope; a {@code null} shape means "local, value unknown".
     */
    private static final class Scope {
        final @Nullable Scope parent;
        final Map<String, VariableShape> vars = new HashMap<>();

        Scope(@Nullable Scope parent) {
            this.parent = parent;
        }

        boolean defines(String name) {
            for (Scope s = this; s != null; s = s.parent) {
                if (s.vars.containsKey(name)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable VariableShape get(String name) {
            for (Scope s = this; s != null; s = s.parent) {
                if (s.vars.containsKey(name)) {
                    return s.vars.get(name);
                }
            }
            return null;
        }
    }

    private static final class Walker {
        final PreviewConfiguration configuration;
        // Shared by a template and every library it imports: they all read from the same data model.
        final Map<String, VariableShape> roots;
        final Map<String, String> declarations;
        final Set<String> warnings;
        final List<String> templates;
        final Set<String> importedLibraries;

        /**
         * Template-namespace variables created by {@code #assign}/{@code #global}.
         */
        final Scope globals = new Scope(null);
        /**
         * Names assigned anywhere at namespace level; known before the walk reaches the assignment.
         */
        final Set<String> assignedSomewhere = new HashSet<>();
        final Set<String> macroNames = new HashSet<>();
        final Set<String> namespaces = new HashSet<>();
        final Deque<String> includeStack = new ArrayDeque<>();
        final Deque<VariableShape> pendingListSources = new ArrayDeque<>();

        Scope scope = globals;
        int macroDepth;

        /**
         * @param parent the importing template's walker; an imported library gets its own namespace
         */
        Walker(PreviewConfiguration configuration, @Nullable Walker parent) {
            this.configuration = configuration;
            roots = parent == null ? new LinkedHashMap<>() : parent.roots;
            declarations = parent == null ? new LinkedHashMap<>() : parent.declarations;
            warnings = parent == null ? new LinkedHashSet<>() : parent.warnings;
            templates = parent == null ? new ArrayList<>() : parent.templates;
            importedLibraries = parent == null ? new HashSet<>() : parent.importedLibraries;
        }

        private static void hintIfShape(@Nullable VariableShape shape, Hint hint) {
            if (shape != null) {
                shape.hint(hint);
            }
        }

        /**
         * Built-in nodes have symbols like {@code ?upper_case}; note that {@code ??} is the existence operator.
         */
        private static boolean isBuiltIn(Object node) {
            String symbol = FtlAstAccess.nodeType(node);
            return symbol.length() > 1 && symbol.charAt(0) == '?' && Character.isLetter(symbol.charAt(1));
        }

        // ---------------------------------------------------------------------------------------------- elements

        private static List<Object> argumentValues(Object node) {
            List<Object> values = new ArrayList<>();
            for (Param p : FtlAstAccess.params(node)) {
                if (p.role().equals("argument value")) {
                    values.add(p.value());
                }
            }
            return values;
        }

        private static @Nullable Object param(@Nullable Object node, String role) {
            if (node == null || !FtlAstAccess.isNode(node)) {
                return null;
            }
            for (Param p : FtlAstAccess.params(node)) {
                if (p.role().equals(role)) {
                    return p.value();
                }
            }
            return null;
        }

        private static @Nullable String stringParam(Object node, String role) {
            return param(node, role) instanceof String s ? s : null;
        }

        private static List<String> stringParams(Object node, String role) {
            List<String> values = new ArrayList<>();
            for (Param p : FtlAstAccess.params(node)) {
                if (p.role().equals(role) && p.value() instanceof String s) {
                    values.add(s);
                }
            }
            return values;
        }

        void walkTemplate(Template template, String name) {
            templates.add(name);
            includeStack.push(name);
            Object root = FtlAstAccess.root(template);
            prepass(root, false);
            element(root);
            includeStack.pop();
            configuration.templateFallbacks().forEach((requested, used) -> warnings.add("\"" + requested
                    + "\" does not exist; the preview loaded \"" + used + "\" instead. Your application may resolve"
                    + " this path differently."));
            declarations.forEach((variable, type) -> {
                VariableShape shape = roots.get(variable);
                if (shape != null && shape.declaredType() == null) {
                    shape.setDeclaredType(type);
                }
            });
        }

        /**
         * Collects macro names and namespace-level assignment targets, which are visible before their definition.
         */
        private void prepass(Object element, boolean insideMacro) {
            String type = FtlAstAccess.nodeType(element);
            boolean macro = type.equals("#macro") || type.equals("#function");
            if (macro) {
                String name = stringParam(element, "assignment target");
                if (name != null) {
                    macroNames.add(name);
                }
            } else if ((type.equals("#assign") || type.equals("#global")) && !insideMacro) {
                String name = stringParam(element, "assignment target");
                if (name != null && param(element, "namespace") == null) {
                    assignedSomewhere.add(name);
                }
            } else if (type.equals("#--...--")) {
                Object content = param(element, "content");
                if (content instanceof String comment) {
                    Matcher m = FTL_VARIABLE.matcher(comment);
                    while (m.find()) {
                        declarations.putIfAbsent(m.group(1), m.group(2));
                    }
                }
            }
            for (Object child : FtlAstAccess.children(element)) {
                prepass(child, insideMacro || macro);
            }
        }

        void element(Object el) {
            String type = FtlAstAccess.nodeType(el);
            switch (type) {
                case "#list", "#foreach" -> listBlock(el);
                case "#items" -> items(el);
                case "#macro", "#function" -> macro(el);
                case "#assign", "#global", "#local" -> assignment(el, type);
                case "@" -> userDirectiveCall(el);
                case "#include" -> include(el);
                case "#import" -> importLibrary(el);
                case "#escape" -> escape(el);
                case "${...}" -> printed(param(el, "content"), Hint.PRINTED);
                case "#{...}" -> printed(param(el, "content"), Hint.NUMBER);
                case "#if", "#elseif" -> {
                    hintIfShape(resolve(param(el, "condition")), Hint.BOOLEAN);
                    children(el);
                }
                case "#switch" -> switchBlock(el);
                default -> generic(el);
            }
        }

        private void generic(Object el) {
            List<String> loopVariables = new ArrayList<>();
            for (Param p : FtlAstAccess.params(el)) {
                if (FtlAstAccess.isExpression(p.value())) {
                    resolve(p.value());
                } else if (p.role().equals("target loop variable") && p.value() instanceof String name) {
                    loopVariables.add(name);
                }
            }
            if (loopVariables.isEmpty()) {
                children(el);
            } else {
                withScope(() -> {
                    loopVariables.forEach(v -> scope.vars.put(v, null));
                    children(el);
                });
            }
        }

        private void children(Object el) {
            for (Object child : FtlAstAccess.children(el)) {
                element(child);
            }
        }

        private void printed(@Nullable Object expression, Hint hint) {
            hintIfShape(resolve(expression), hint);
        }

        private void listBlock(Object el) {
            Object sourceExpression = param(el, "list source");
            VariableShape source = resolve(sourceExpression);
            List<String> loopVariables = stringParams(el, "target loop variable");
            boolean range = sourceExpression != null && FtlAstAccess.isRange(sourceExpression);
            withScope(() -> {
                if (loopVariables.isEmpty()) {
                    // "#list xs> ... <#items as x>": the loop variable is declared by the nested #items.
                    pendingListSources.push(source == null ? new VariableShape("_") : source);
                    children(el);
                    pendingListSources.pop();
                } else {
                    bindLoopVariables(source, loopVariables);
                    if (range) {
                        // <#list 0..n as i>: i is a number (local), so xs[i] is a list index.
                        scope.vars.put(loopVariables.get(0), new VariableShape(loopVariables.get(0)).hint(Hint.NUMBER));
                    }
                    children(el);
                }
            });
        }

        private void items(Object el) {
            VariableShape source = pendingListSources.isEmpty() ? null : pendingListSources.peek();
            withScope(() -> {
                bindLoopVariables(source == null || source.name().equals("_") ? null : source,
                        stringParams(el, "target loop variable"));
                children(el);
            });
        }

        private void bindLoopVariables(@Nullable VariableShape source, List<String> loopVariables) {
            if (loopVariables.size() >= 2) {
                // "#list hash as key, value"
                // Local (not part of the model); it names dynamic keys such as other[key].
                VariableShape key = new VariableShape(loopVariables.get(0));
                VariableShape value = null;
                if (source != null) {
                    source.hint(Hint.KEY_VALUE_LISTED);
                    source.setKeyName(loopVariables.get(0));
                    value = source.element();
                    value.preferName(loopVariables.get(1));
                }
                scope.vars.put(loopVariables.get(0), key);
                scope.vars.put(loopVariables.get(1), value);
            } else if (loopVariables.size() == 1) {
                VariableShape item = null;
                if (source != null) {
                    source.hint(Hint.SEQUENCE);
                    item = source.element();
                    item.preferName(loopVariables.get(0));
                }
                scope.vars.put(loopVariables.get(0), item);
            }
        }

        private void macro(Object el) {
            Scope saved = scope;
            scope = new Scope(globals);
            macroDepth++;
            try {
                List<Param> params = FtlAstAccess.params(el);
                for (Param p : params) {
                    if ((p.role().equals("parameter name") || p.role().equals("catch-all parameter name"))
                            && p.value() instanceof String name) {
                        scope.vars.put(name, null);
                    }
                }
                for (Param p : params) {
                    if (p.role().equals("parameter default")) {
                        resolve(p.value());
                    }
                }
                children(el);
            } finally {
                macroDepth--;
                scope = saved;
            }
        }

        // ------------------------------------------------------------------------------------------- expressions

        private void assignment(Object el, String type) {
            String target = stringParam(el, "assignment target");
            if (target == null) {
                // A multi-assignment container (<#assign a=1 b=2>); its children are the assignments.
                generic(el);
                return;
            }
            Object source = param(el, "assignment source");
            VariableShape value = null;
            if (source != null) {
                VariableShape evaluated = resolve(source);
                Object operator = param(el, "assignment operator");
                if ("=".equals(operator)) {
                    value = evaluated;
                }
            } else {
                // Block assignment (<#assign x>...</#assign>) captures output.
                children(el);
            }
            if (param(el, "namespace") != null) {
                return;
            }
            Scope targetScope = type.equals("#local") && macroDepth > 0 ? scope : globals;
            targetScope.vars.put(target, value);
        }

        private void userDirectiveCall(Object el) {
            Object callee = param(el, "callee");
            if (callee != null && FtlAstAccess.kind(callee).equals("Identifier")) {
                VariableShape shape = identifier(FtlAstAccess.nodeType(callee));
                hintIfShape(shape, Hint.DIRECTIVE);
            } else {
                resolve(callee);
            }
            // Arguments and nested-content loop variables; re-resolving the callee is harmless (hints are idempotent).
            generic(el);
        }

        private void include(Object el) {
            Object nameExpression = param(el, "template name");
            if (nameExpression == null) {
                return;
            }
            if (!FtlAstAccess.isStaticStringLiteral(nameExpression)) {
                resolve(nameExpression);
                warnings.add("Include with a computed name (" + FtlAstAccess.canonicalForm(nameExpression)
                        + ") was not analyzed; add its variables to the sample data manually if needed.");
                return;
            }
            String current = includeStack.isEmpty() ? "" : includeStack.peek();
            String requested = FtlAstAccess.staticStringValue(nameExpression);
            String resolved = TemplatePaths.resolve(current, requested);
            if (resolved == null) {
                warnings.add("Include \"" + requested + "\" is outside the template root or uses an unsupported path.");
                return;
            }
            if (includeStack.contains(resolved)) {
                return; // recursive include; its variables are already being collected
            }
            if (includeStack.size() >= MAX_INCLUDE_DEPTH) {
                warnings.add("Includes nested deeper than " + MAX_INCLUDE_DEPTH + " levels were not analyzed.");
                return;
            }
            Template included;
            try {
                included = configuration.getTemplate(resolved);
            } catch (ParseException e) {
                warnings.add("Included template \"" + resolved + "\" has a syntax error at line " + e.getLineNumber()
                        + "; its variables were not analyzed.");
                return;
            } catch (IOException e) {
                warnings.add("Included template \"" + resolved + "\" was not found under the template root.");
                return;
            }
            walkTemplate(included, resolved);
        }

        private void importLibrary(Object el) {
            String namespace = stringParam(el, "namespace");
            if (namespace != null) {
                namespaces.add(namespace);
                globals.vars.put(namespace, null);
            }
            Object nameExpression = param(el, "template name");
            if (nameExpression == null || !FtlAstAccess.isStaticStringLiteral(nameExpression)) {
                resolve(nameExpression);
                if (nameExpression != null) {
                    warnings.add("Import with a computed name (" + FtlAstAccess.canonicalForm(nameExpression)
                            + ") was not analyzed; add its variables to the sample data manually if needed.");
                }
                return;
            }
            String current = includeStack.isEmpty() ? "" : includeStack.peek();
            String requested = FtlAstAccess.staticStringValue(nameExpression);
            String resolved = TemplatePaths.resolve(current, requested);
            if (resolved == null) {
                warnings.add("Import \"" + requested + "\" is outside the template root or uses an unsupported path.");
                return;
            }
            if (!importedLibraries.add(resolved) || importedLibraries.size() > MAX_INCLUDE_DEPTH * 8) {
                return; // already analyzed (libraries are imported once per namespace anyway)
            }
            Template library;
            try {
                library = configuration.getTemplate(resolved);
            } catch (ParseException e) {
                warnings.add("Imported template \"" + resolved + "\" has a syntax error at line " + e.getLineNumber()
                        + "; its variables were not analyzed.");
                return;
            } catch (IOException e) {
                warnings.add("Imported template \"" + resolved + "\" was not found under the template root.");
                return;
            }
            // A library has its own namespace (its #assign and macros are not ours), but its macro bodies read the
            // same data model, e.g. ${model.clientConfig.name} inside <#macro render>.
            new Walker(configuration, this).walkTemplate(library, resolved);
        }

        private void escape(Object el) {
            String placeholder = stringParam(el, "placeholder variable");
            Object expression = param(el, "expression template");
            if (placeholder != null && expression != null) {
                withScope(() -> {
                    scope.vars.put(placeholder, null);
                    resolve(expression);
                });
            }
            children(el);
        }

        private void switchBlock(Object el) {
            VariableShape value = resolve(param(el, "value"));
            if (value != null) {
                for (Object child : FtlAstAccess.children(el)) {
                    Object condition = param(child, "condition");
                    if (condition != null) {
                        literalHint(value, condition);
                    }
                }
            }
            children(el);
        }

        /**
         * Resolves an expression symbolically (nothing is executed). Returns the shape of the data-model value the expression denotes, or
         * {@code null} when it denotes a literal, a computed value or a template-local variable.
         */
        @Nullable VariableShape resolve(@Nullable Object expression) {
            if (expression == null || !FtlAstAccess.isNode(expression)) {
                return null;
            }
            if (FtlAstAccess.isElement(expression)) {
                element(expression); // interpolation inside a string literal
                return null;
            }
            String symbol = FtlAstAccess.nodeType(expression);
            if (isBuiltIn(expression)) {
                return builtIn(expression, argumentValues(expression));
            }
            switch (FtlAstAccess.kind(expression)) {
                case "Identifier":
                    return identifier(symbol);
                case "Dot":
                case "DotBeforeMethodCall": {
                    VariableShape target = resolve(param(expression, "left-hand operand"));
                    Object key = param(expression, "right-hand operand");
                    return target != null && key instanceof String k ? target.property(k) : null;
                }
                case "DynamicKeyName":
                    return dynamicKey(expression);
                case "MethodCall":
                    return methodCall(expression);
                case "DefaultToExpression": {
                    VariableShape value = resolve(param(expression, "left-hand operand"));
                    Object fallback = param(expression, "right-hand operand");
                    if (value != null) {
                        value.hint(Hint.OPTIONAL);
                        if (fallback != null) {
                            defaultHint(value, fallback);
                        }
                    }
                    resolve(fallback);
                    return value;
                }
                case "ExistsExpression":
                    hintIfShape(resolve(param(expression, "left-hand operand")), Hint.OPTIONAL);
                    return null;
                case "ParentheticalExpression":
                    return resolve(param(expression, "enclosed operand"));
                case "NotExpression":
                case "AndExpression":
                case "OrExpression":
                    evalOperands(expression, Hint.BOOLEAN);
                    return null;
                case "ArithmeticExpression":
                case "UnaryPlusMinusExpression":
                case "Range":
                    evalOperands(expression, Hint.NUMBER);
                    return null;
                case "ComparisonExpression":
                case "AddConcatExpression": {
                    Object left = param(expression, "left-hand operand");
                    Object right = param(expression, "right-hand operand");
                    VariableShape l = resolve(left);
                    VariableShape r = resolve(right);
                    if (l != null && right != null) {
                        literalHint(l, right);
                    }
                    if (r != null && left != null) {
                        literalHint(r, left);
                    }
                    return null;
                }
                case "LocalLambdaExpression":
                    lambda(expression, null);
                    return null;
                default:
                    for (Param p : FtlAstAccess.params(expression)) {
                        resolve(p.value());
                    }
                    return null;
            }
        }

        private @Nullable VariableShape identifier(String name) {
            if (scope.defines(name)) {
                return scope.get(name);
            }
            if (macroNames.contains(name) || namespaces.contains(name)) {
                return null;
            }
            // Assigned somewhere in the template: local inside macro bodies (they run later); at top level only
            // before the assignment is reached, e.g. <#assign title = title!"Default">.
            if (assignedSomewhere.contains(name) && macroDepth > 0) {
                return null;
            }
            return roots.computeIfAbsent(name, VariableShape::new);
        }

        private @Nullable VariableShape dynamicKey(Object expression) {
            Object targetExpression = param(expression, "left-hand operand");
            Object key = param(expression, "enclosed operand");
            if (targetExpression != null && key != null && FtlAstAccess.nodeType(targetExpression).equals("?string")) {
                // Legacy call syntax: x?string["#,##0.00"] is x?string("#,##0.00").
                return builtIn(targetExpression, List.of(key));
            }
            VariableShape target = resolve(targetExpression);
            if (key == null) {
                return null;
            }
            if (FtlAstAccess.isStaticStringLiteral(key)) {
                return target == null ? null : target.property(FtlAstAccess.staticStringValue(key));
            }
            if (FtlAstAccess.isNumberLiteral(key)) {
                return target == null ? null : target.hint(Hint.SEQUENCE).element();
            }
            if (FtlAstAccess.isRange(key)) {
                resolve(key);
                return target;
            }
            VariableShape keyShape = resolve(key);
            if (target == null) {
                return null;
            }
            String keyKind = FtlAstAccess.kind(key);
            boolean numericKey = keyKind.equals("ArithmeticExpression") || keyKind.equals("NumberLiteral")
                    || keyKind.equals("AddConcatExpression") && FtlAstAccess.params(key).stream()
                    .anyMatch(p -> FtlAstAccess.isNumberLiteral(p.value()))
                    || isBuiltIn(key) && Set.of("?index", "?counter", "?size").contains(FtlAstAccess.nodeType(key))
                    || keyShape != null && keyShape.has(Hint.NUMBER);
            if (numericKey) {
                return target.hint(Hint.SEQUENCE).element();
            }
            // m[k] with a computed key: a map whose keys are values like k.
            target.hint(Hint.KEY_VALUE_LISTED);
            if (keyShape != null) {
                keyShape.hint(Hint.STRING);
                target.setKeyName(keyShape.name());
            } else if (keyKind.equals("Identifier")) {
                target.setKeyName(FtlAstAccess.nodeType(key));
            }
            return target.element();
        }

        private @Nullable VariableShape methodCall(Object expression) {
            Object callee = param(expression, "callee");
            List<Object> arguments = argumentValues(expression);
            if (callee != null && isBuiltIn(callee)) {
                // x?builtIn(args): the arguments belong to the built-in.
                return builtIn(callee, arguments);
            }
            if (callee != null && Set.of("Dot", "DotBeforeMethodCall").contains(FtlAstAccess.kind(callee))
                    && param(callee, "right-hand operand") instanceof String methodName
                    && NUMBER_METHODS.contains(methodName)) {
                // amount.divide(total, 4, "roundHalfUp"): a number with Java methods, not an object with a "divide" key.
                hintIfShape(resolve(param(callee, "left-hand operand")), Hint.NUMBER);
                for (Object argument : arguments) {
                    VariableShape shape = resolve(argument);
                    if (NUMBER_ARGUMENT_METHODS.contains(methodName)) {
                        hintIfShape(shape, Hint.NUMBER);
                    }
                }
                return null;
            }
            VariableShape method = resolve(callee);
            arguments.forEach(this::resolve);
            return method == null ? null : method.methodResult();
        }

        // ------------------------------------------------------------------------------------------------ helpers

        private @Nullable VariableShape builtIn(Object expression, List<Object> arguments) {
            Object nameParam = param(expression, "right-hand operand");
            String name = nameParam instanceof String s ? s : FtlAstAccess.nodeType(expression).substring(1);
            VariableShape target = resolve(param(expression, "left-hand operand"));

            if (LAMBDA_ELEMENT_BUILT_INS.contains(name)) {
                VariableShape element = target == null ? null : target.hint(Hint.SEQUENCE).element();
                for (Object argument : arguments) {
                    if (FtlAstAccess.isLambda(argument)) {
                        lambda(argument, element);
                    } else {
                        resolve(argument);
                    }
                }
            } else {
                arguments.forEach(this::resolve);
            }
            if (target == null) {
                return null;
            }

            if (STRING_BUILT_INS.contains(name)) {
                target.hint(Hint.STRING);
            } else if (NUMBER_BUILT_INS.contains(name)) {
                target.hint(Hint.NUMBER);
            } else if (SEQUENCE_BUILT_INS.contains(name)) {
                target.hint(Hint.SEQUENCE);
            } else if (OPTIONAL_BUILT_INS.contains(name)) {
                target.hint(Hint.OPTIONAL);
            }
            switch (name) {
                case "date", "date_if_unknown", "dateIfUnknown" -> target.hint(Hint.DATE);
                case "time", "time_if_unknown", "timeIfUnknown" -> target.hint(Hint.TIME);
                case "datetime", "datetime_if_unknown", "datetimeIfUnknown" -> target.hint(Hint.DATETIME);
                case "then" -> target.hint(Hint.BOOLEAN);
                case "boolean" -> target.hint(Hint.BOOLEAN_STRING);
                case "keys", "values" -> target.hint(Hint.KEY_VALUE_LISTED);
                case "string" -> stringBuiltInHint(target, arguments);
                case "sort_by", "sortBy" -> {
                    for (Object argument : arguments) {
                        if (FtlAstAccess.isStaticStringLiteral(argument)) {
                            target.element().property(FtlAstAccess.staticStringValue(argument));
                        }
                    }
                }
                default -> {
                    if (name.startsWith("iso")) {
                        target.hint(Hint.DATETIME);
                    }
                }
            }
            if (name.equals("first") || name.equals("last") || name.equals("values")) {
                return target.element();
            }
            if (SAME_SEQUENCE_BUILT_INS.contains(name) || PASS_THROUGH_BUILT_INS.contains(name)) {
                return target;
            }
            return null;
        }

        /**
         * {@code ?string("yes", "no")} formats a boolean; {@code ?string("dd.MM.yyyy")} a date; {@code "0.00"} a number.
         */
        private void stringBuiltInHint(VariableShape target, List<Object> arguments) {
            if (arguments.size() == 2) {
                target.hint(Hint.BOOLEAN);
            } else if (arguments.size() == 1 && FtlAstAccess.isStaticStringLiteral(arguments.get(0))) {
                String format = FtlAstAccess.staticStringValue(arguments.get(0));
                if (format.matches("[0#.,%\\s¤']+|currency|number|percent|computer")) {
                    target.hint(Hint.NUMBER);
                } else if (format.matches(".*[yMdHhmsEa].*") || format.startsWith("iso") || format.equals("xs")) {
                    target.hint(format.matches(".*[Hhms].*") && format.matches(".*[yMd].*") ? Hint.DATETIME
                            : format.matches(".*[Hhms].*") ? Hint.TIME : Hint.DATE);
                }
            }
        }

        private void lambda(Object lambda, @Nullable VariableShape parameterShape) {
            withScope(() -> {
                for (Param p : FtlAstAccess.params(lambda)) {
                    if (p.role().equals("argument name") && FtlAstAccess.isNode(p.value())) {
                        scope.vars.put(FtlAstAccess.nodeType(p.value()), parameterShape);
                    }
                }
                for (Param p : FtlAstAccess.params(lambda)) {
                    if (!p.role().equals("argument name")) {
                        resolve(p.value());
                    }
                }
            });
        }

        private void evalOperands(Object expression, Hint hint) {
            for (Param p : FtlAstAccess.params(expression)) {
                hintIfShape(resolve(p.value()), hint);
            }
        }

        /**
         * Infers the type of {@code shape} from a literal it is compared, concatenated or defaulted with.
         */
        private void literalHint(VariableShape shape, Object literal) {
            if (FtlAstAccess.isStringLiteral(literal)) {
                shape.hint(Hint.STRING);
            } else if (FtlAstAccess.isNumberLiteral(literal)) {
                shape.hint(Hint.NUMBER);
                double value = FtlAstAccess.numberValue(literal).doubleValue();
                if (value != 0 && Math.abs(value) < 1) {
                    shape.hint(Hint.FRACTION);
                }
            } else if (FtlAstAccess.isBooleanLiteral(literal)) {
                shape.hint(Hint.BOOLEAN);
            } else if (FtlAstAccess.isNode(literal)) {
                switch (FtlAstAccess.kind(literal)) {
                    case "ListLiteral" -> shape.hint(Hint.SEQUENCE);
                    case "HashLiteral" -> shape.hint(Hint.HASH);
                    default -> {
                    }
                }
            }
        }

        /**
         * {@code x!"N/A"}: a weak hint, because the fallback is often unrelated to the real value's type.
         */
        private void defaultHint(VariableShape shape, Object literal) {
            if (FtlAstAccess.isStringLiteral(literal)) {
                shape.hint(Hint.DEFAULT_STRING);
            } else if (FtlAstAccess.isNumberLiteral(literal)) {
                shape.hint(Hint.DEFAULT_NUMBER);
            } else if (FtlAstAccess.isBooleanLiteral(literal)) {
                shape.hint(Hint.DEFAULT_BOOLEAN);
            } else {
                literalHint(shape, literal); // [] and {} are structural and reliable
            }
        }

        private void withScope(Runnable body) {
            Scope saved = scope;
            scope = new Scope(saved);
            try {
                body.run();
            } finally {
                scope = saved;
            }
        }
    }
}

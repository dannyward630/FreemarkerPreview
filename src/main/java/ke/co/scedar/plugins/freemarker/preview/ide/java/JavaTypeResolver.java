package ke.co.scedar.plugins.freemarker.preview.ide.java;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import ke.co.scedar.plugins.freemarker.preview.analysis.Names;
import ke.co.scedar.plugins.freemarker.preview.analysis.VariableShape;
import ke.co.scedar.plugins.freemarker.preview.model.TypeInfo;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves template variables to Java classes using PSI only (declarations are read; no code is compiled or run).
 * <ol>
 *     <li>An {@code <#-- @ftlvariable name="x" type="com.acme.X" -->} declaration, if its type resolves.</li>
 *     <li>Otherwise, for variables whose properties the template reads, a project class whose simple name matches the
 *     variable ({@code customer -> Customer}, or for a list {@code orders -> Order}) - only when exactly one such class
 *     exists, so guesses are never ambiguous.</li>
 * </ol>
 * Properties follow FreeMarker's bean conventions: getters ({@code getX}/{@code isX}), record components, and
 * methods called from the template. Must be called inside a read action.
 */
final class JavaTypeResolver implements TypeResolver {

    private static final Map<String, TypeInfo.Kind> KNOWN = Map.ofEntries(
            Map.entry(CommonClassNames.JAVA_LANG_STRING, TypeInfo.Kind.STRING),
            Map.entry("java.lang.CharSequence", TypeInfo.Kind.STRING),
            Map.entry("java.lang.Character", TypeInfo.Kind.STRING),
            Map.entry("java.util.UUID", TypeInfo.Kind.STRING),
            Map.entry("java.net.URI", TypeInfo.Kind.STRING),
            Map.entry("java.net.URL", TypeInfo.Kind.STRING),
            Map.entry(CommonClassNames.JAVA_LANG_BOOLEAN, TypeInfo.Kind.BOOLEAN),
            Map.entry(CommonClassNames.JAVA_LANG_INTEGER, TypeInfo.Kind.INTEGER),
            Map.entry(CommonClassNames.JAVA_LANG_LONG, TypeInfo.Kind.INTEGER),
            Map.entry(CommonClassNames.JAVA_LANG_SHORT, TypeInfo.Kind.INTEGER),
            Map.entry(CommonClassNames.JAVA_LANG_BYTE, TypeInfo.Kind.INTEGER),
            Map.entry("java.math.BigInteger", TypeInfo.Kind.INTEGER),
            Map.entry(CommonClassNames.JAVA_LANG_DOUBLE, TypeInfo.Kind.DECIMAL),
            Map.entry(CommonClassNames.JAVA_LANG_FLOAT, TypeInfo.Kind.DECIMAL),
            Map.entry("java.math.BigDecimal", TypeInfo.Kind.DECIMAL),
            Map.entry(CommonClassNames.JAVA_LANG_NUMBER, TypeInfo.Kind.DECIMAL),
            Map.entry(CommonClassNames.JAVA_UTIL_DATE, TypeInfo.Kind.DATETIME),
            Map.entry("java.sql.Timestamp", TypeInfo.Kind.DATETIME),
            Map.entry("java.util.Calendar", TypeInfo.Kind.DATETIME),
            Map.entry("java.time.Instant", TypeInfo.Kind.DATETIME),
            Map.entry("java.time.LocalDateTime", TypeInfo.Kind.DATETIME),
            Map.entry("java.time.ZonedDateTime", TypeInfo.Kind.DATETIME),
            Map.entry("java.time.OffsetDateTime", TypeInfo.Kind.DATETIME),
            Map.entry("java.time.LocalDate", TypeInfo.Kind.DATE),
            Map.entry("java.sql.Date", TypeInfo.Kind.DATE),
            Map.entry("java.time.LocalTime", TypeInfo.Kind.TIME),
            Map.entry("java.time.OffsetTime", TypeInfo.Kind.TIME),
            Map.entry("java.sql.Time", TypeInfo.Kind.TIME));

    private final Project project;

    JavaTypeResolver(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Resolution resolve(String name, VariableShape shape) {
        if (project.isDisposed() || DumbService.getInstance(project).isDumb()) {
            return null;
        }
        String declared = shape.declaredType();
        if (declared != null) {
            PsiType type = parse(declared);
            return type == null ? null : new Resolution(convert(type), "@ftlvariable");
        }
        if (!shape.properties().isEmpty()) {
            PsiClass match = uniqueProjectClass(StringUtil.capitalize(name));
            if (match != null) {
                return new Resolution(bean(match, PsiSubstitutor.EMPTY), "project class matched by name");
            }
        }
        VariableShape element = shape.elementOrNull();
        if (shape.has(VariableShape.Hint.SEQUENCE) && element != null && !element.properties().isEmpty()) {
            PsiClass match = uniqueProjectClass(StringUtil.capitalize(Names.singular(name)));
            if (match != null) {
                return new Resolution(TypeInfo.sequenceOf(bean(match, PsiSubstitutor.EMPTY)),
                        "list of project class matched by name");
            }
        }
        return null;
    }

    private @Nullable PsiType parse(String text) {
        try {
            PsiType type = JavaPsiFacade.getElementFactory(project).createTypeFromText(text.trim(), null);
            if (type instanceof PsiClassType classType && classType.resolve() == null) {
                return null;
            }
            return type;
        } catch (IncorrectOperationException e) {
            return null;
        }
    }

    private @Nullable PsiClass uniqueProjectClass(String simpleName) {
        PsiClass[] classes = PsiShortNamesCache.getInstance(project)
                .getClassesByName(simpleName, GlobalSearchScope.projectScope(project));
        return classes.length == 1 ? classes[0] : null;
    }

    private TypeInfo convert(@Nullable PsiType type) {
        if (type == null) {
            return TypeInfo.of(TypeInfo.Kind.UNKNOWN, "?");
        }
        if (type instanceof PsiWildcardType wildcard) {
            return wildcard.isExtends() ? convert(wildcard.getBound()) : TypeInfo.of(TypeInfo.Kind.UNKNOWN, type.getPresentableText());
        }
        if (type instanceof PsiPrimitiveType primitive) {
            if (primitive.equals(PsiTypes.booleanType())) {
                return TypeInfo.of(TypeInfo.Kind.BOOLEAN, "boolean");
            }
            if (primitive.equals(PsiTypes.doubleType()) || primitive.equals(PsiTypes.floatType())) {
                return TypeInfo.of(TypeInfo.Kind.DECIMAL, primitive.getName());
            }
            if (primitive.equals(PsiTypes.charType())) {
                return TypeInfo.of(TypeInfo.Kind.STRING, "char");
            }
            if (primitive.equals(PsiTypes.voidType())) {
                return TypeInfo.of(TypeInfo.Kind.UNKNOWN, "void");
            }
            return TypeInfo.of(TypeInfo.Kind.INTEGER, primitive.getName());
        }
        if (type instanceof PsiArrayType array) {
            return TypeInfo.sequenceOf(convert(array.getComponentType()));
        }
        if (!(type instanceof PsiClassType classType)) {
            return TypeInfo.of(TypeInfo.Kind.UNKNOWN, type.getPresentableText());
        }
        PsiClassType.ClassResolveResult resolved = classType.resolveGenerics();
        PsiClass psiClass = resolved.getElement();
        if (psiClass == null || psiClass instanceof PsiTypeParameter) {
            return TypeInfo.of(TypeInfo.Kind.UNKNOWN, type.getPresentableText());
        }
        String qualifiedName = psiClass.getQualifiedName();
        TypeInfo.Kind known = qualifiedName == null ? null : KNOWN.get(qualifiedName);
        if (known != null) {
            return TypeInfo.of(known, qualifiedName);
        }
        if (psiClass.isEnum()) {
            List<String> constants = new ArrayList<>();
            for (PsiField field : psiClass.getFields()) {
                if (field instanceof PsiEnumConstant) {
                    constants.add(field.getName());
                }
            }
            return TypeInfo.enumOf(qualifiedName == null ? psiClass.getName() : qualifiedName, constants);
        }
        if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_MAP)) {
            TypeInfo value = convert(PsiUtil.substituteTypeParameter(classType, CommonClassNames.JAVA_UTIL_MAP, 1, false));
            return new TypeInfo.Simple(TypeInfo.Kind.MAP, type.getPresentableText(), value, Map.of(), List.of());
        }
        if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
            return TypeInfo.sequenceOf(convert(PsiUtil.substituteTypeParameter(classType, CommonClassNames.JAVA_LANG_ITERABLE, 0, false)));
        }
        if (qualifiedName != null && (qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.")
                || qualifiedName.startsWith("jakarta.") || qualifiedName.startsWith("kotlin."))) {
            return TypeInfo.of(TypeInfo.Kind.UNKNOWN, qualifiedName); // not a bean worth introspecting (Object, Optional, ...)
        }
        return bean(psiClass, resolved.getSubstitutor());
    }

    private TypeInfo bean(PsiClass psiClass, PsiSubstitutor substitutor) {
        String display = psiClass.getQualifiedName() == null ? String.valueOf(psiClass.getName()) : psiClass.getQualifiedName();
        return TypeInfo.bean(display, property -> propertyType(psiClass, substitutor, property));
    }

    private @Nullable TypeInfo propertyType(PsiClass psiClass, PsiSubstitutor substitutor, String property) {
        if (!psiClass.isValid()) {
            return null;
        }
        if (psiClass.isRecord()) {
            for (PsiRecordComponent component : psiClass.getRecordComponents()) {
                if (property.equals(component.getName())) {
                    return convert(substitutor.substitute(component.getType()));
                }
            }
        }
        String capitalized = StringUtil.capitalize(property);
        for (String accessor : List.of("get" + capitalized, "is" + capitalized, property)) {
            for (PsiMethod method : psiClass.findMethodsByName(accessor, true)) {
                boolean isGetter = !accessor.equals(property);
                if (method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)
                        || method.isConstructor() || isGetter && method.getParameterList().getParametersCount() > 0) {
                    continue;
                }
                PsiType returnType = method.getReturnType();
                if (returnType == null || PsiTypes.voidType().equals(returnType)) {
                    continue;
                }
                PsiClass owner = method.getContainingClass();
                PsiSubstitutor ownerSubstitutor = owner == null ? substitutor
                        : TypeConversionUtil.getSuperClassSubstitutor(owner, psiClass, substitutor);
                return convert(ownerSubstitutor.substitute(returnType));
            }
        }
        return null;
    }
}

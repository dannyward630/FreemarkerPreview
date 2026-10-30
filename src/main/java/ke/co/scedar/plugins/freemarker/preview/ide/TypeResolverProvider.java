package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point for project-aware sample data. The Java implementation is registered only when the Java plugin is
 * present (see {@code freemarker-preview-java.xml}). Resolvers are called inside a read action and must not run code.
 */
public interface TypeResolverProvider {

    ExtensionPointName<TypeResolverProvider> EP_NAME =
            ExtensionPointName.create("ke.co.scedar.plugins.freemarker-preview.typeResolverProvider");

    /**
     * The first resolver offered by an installed provider, or {@link TypeResolver#NONE}.
     */
    static TypeResolver forFile(Project project, VirtualFile template) {
        for (TypeResolverProvider provider : EP_NAME.getExtensionList()) {
            TypeResolver resolver = provider.create(project, template);
            if (resolver != null) {
                return resolver;
            }
        }
        return TypeResolver.NONE;
    }

    @Nullable TypeResolver create(Project project, VirtualFile template);
}

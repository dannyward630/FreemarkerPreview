package ke.co.scedar.plugins.freemarker.preview.ide.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.ide.TypeResolverProvider;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;

/**
 * Registered only when the Java plugin is installed (optional dependency).
 */
public final class JavaTypeResolverProvider implements TypeResolverProvider {

    @Override
    public TypeResolver create(Project project, VirtualFile template) {
        return new JavaTypeResolver(project);
    }
}

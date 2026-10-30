package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * The directory that template names are relative to, i.e. what {@code <#include "/x.ftl">} resolves against.
 * <p>
 * Chosen as the nearest enclosing {@code templates} directory inside the source/resource root (the Spring and common
 * servlet convention), otherwise the source root, the content root, the project directory, or finally the template's
 * own directory.
 * Includes cannot reach outside this directory.
 *
 * @param directory    root directory
 * @param templateName path of the template relative to {@code directory}
 */
public record TemplateRoot(VirtualFile directory, String templateName) {

    private static final String TEMPLATES_DIRECTORY = "templates";

    /** Must be called inside a read action. */
    public static @Nullable TemplateRoot locate(Project project, VirtualFile file) {
        VirtualFile parent = file.getParent();
        if (parent == null || !file.isValid()) {
            return null;
        }
        VirtualFile boundary = null;
        if (!project.isDisposed()) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            boundary = index.getSourceRootForFile(file);
            if (boundary == null) {
                boundary = index.getContentRootForFile(file);
            }
        }
        if (boundary == null && !project.isDisposed() && project.getBasePath() != null) {
            // Not inside a module (e.g. the build was not imported): use the project directory, so sibling folders
            // such as templates/fragments stay reachable.
            VirtualFile base = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
            if (base != null && VfsUtilCore.isAncestor(base, file, true)) {
                boundary = base;
            }
        }
        if (boundary == null) {
            boundary = parent;
        }
        VirtualFile root = boundary;
        for (VirtualFile dir = parent; dir != null && VfsUtilCore.isAncestor(boundary, dir, false); dir = dir.getParent()) {
            if (dir.getName().equals(TEMPLATES_DIRECTORY)) {
                root = dir;
                break;
            }
        }
        String name = VfsUtilCore.getRelativePath(file, root, '/');
        return name == null ? null : new TemplateRoot(root, name);
    }
}

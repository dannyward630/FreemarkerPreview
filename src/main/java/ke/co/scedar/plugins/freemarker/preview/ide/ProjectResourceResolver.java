package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplatePaths;
import ke.co.scedar.plugins.freemarker.preview.render.ResourceInliner;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds images and stylesheets referenced by the rendered page inside the project, the way a web application would
 * serve them: relative to the template, the template root, and the usual static-resource folders of the module
 * ({@code static/}, {@code public/}, {@code META-INF/resources/}, {@code webapp/}, ...).
 * <p>
 * Only files inside the project's content with an image or CSS extension and a modest size are read; nothing else on
 * disk is reachable. Must be used inside a read action.
 */
public final class ProjectResourceResolver implements ResourceInliner.Resolver {

    private static final long MAX_IMAGE_BYTES = 2_000_000;
    private static final long MAX_STYLESHEET_BYTES = 500_000;
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
            "svg", "image/svg+xml", "webp", "image/webp", "ico", "image/x-icon", "bmp", "image/bmp",
            "avif", "image/avif");
    private static final List<String> STATIC_FOLDERS = List.of("static", "public", "assets", "resources",
            "META-INF/resources", "webapp", "src/main/webapp", "web", "WebContent", "src/main/resources/static",
            "src/main/resources/public");

    private final Project project;
    private final VirtualFile template;
    private final List<VirtualFile> bases;

    public ProjectResourceResolver(Project project, VirtualFile template, VirtualFile templateRoot) {
        this.project = project;
        this.template = template;
        this.bases = bases(templateRoot);
    }

    @Override
    public @Nullable String imageDataUri(String path) {
        VirtualFile file = find(path, IMAGE_TYPES.keySet(), MAX_IMAGE_BYTES);
        if (file == null) {
            return null;
        }
        try {
            String type = IMAGE_TYPES.get(extension(file));
            return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(file.contentsToByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public @Nullable String stylesheet(String path) {
        VirtualFile file = find(path, Set.of("css"), MAX_STYLESHEET_BYTES);
        if (file == null) {
            return null;
        }
        try {
            return new String(file.contentsToByteArray(), file.getCharset() == null ? StandardCharsets.UTF_8 : file.getCharset());
        } catch (IOException e) {
            return null;
        }
    }

    private @Nullable VirtualFile find(String reference, Set<String> extensions, long maxBytes) {
        String path = reference.replace('\\', '/');
        boolean absolute = path.startsWith("/");
        String normalized = TemplatePaths.normalize(path);
        if (normalized == null) {
            return null;
        }
        List<VirtualFile> searchBases = new ArrayList<>();
        if (!absolute && template.getParent() != null) {
            searchBases.add(template.getParent());
        }
        searchBases.addAll(bases);
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        for (VirtualFile base : searchBases) {
            VirtualFile file = base.findFileByRelativePath(normalized);
            if (file != null && file.isValid() && !file.isDirectory() && extensions.contains(extension(file))
                    && file.getLength() <= maxBytes && index.isInContent(file) && insideProject(file, index)) {
                return file;
            }
        }
        return null;
    }

    /** Rejects symbolic links that lead out of the project. */
    private static boolean insideProject(VirtualFile file, ProjectFileIndex index) {
        VirtualFile canonical = file.getCanonicalFile();
        return canonical != null && index.isInContent(canonical);
    }

    private List<VirtualFile> bases(VirtualFile templateRoot) {
        Set<VirtualFile> result = new LinkedHashSet<>();
        result.add(templateRoot);
        Module module = project.isDisposed() ? null : ModuleUtilCore.findModuleForFile(template, project);
        List<VirtualFile> roots = new ArrayList<>();
        if (module != null) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            roots.addAll(List.of(rootManager.getSourceRoots()));
            roots.addAll(List.of(rootManager.getContentRoots()));
        }
        for (VirtualFile root : roots) {
            result.add(root);
            for (String folder : STATIC_FOLDERS) {
                VirtualFile candidate = root.findFileByRelativePath(folder);
                if (candidate != null && candidate.isDirectory()) {
                    result.add(candidate);
                }
            }
        }
        return List.copyOf(result);
    }

    private static String extension(VirtualFile file) {
        String extension = file.getExtension();
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }
}

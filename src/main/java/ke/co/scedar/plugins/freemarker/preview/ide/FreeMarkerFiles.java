package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Recognizes FreeMarker templates by extension, independent of which plugin (if any) registers a file type for them.
 */
public final class FreeMarkerFiles {

    public static final Set<String> EXTENSIONS = Set.of("ftl", "ftlh", "ftlx");

    private FreeMarkerFiles() {
    }

    public static boolean isTemplate(@Nullable VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        String extension = file.getExtension();
        return extension != null && EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}

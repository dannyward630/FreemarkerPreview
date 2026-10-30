package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves templates from below a root directory, preferring unsaved editor content. Files outside the root (including
 * through symbolic links), binary files and very large files are never read. Each read is a short read action.
 */
public final class VfsTemplateSource implements TemplateSource {

    private static final long MAX_BYTES = 1_000_000;

    private final VirtualFile root;
    private final Set<String> readUrls = ConcurrentHashMap.newKeySet();

    public VfsTemplateSource(VirtualFile root) {
        this.root = root;
    }

    @Override
    public @Nullable String read(@NotNull String name) throws IOException {
        return ReadAction.computeBlocking(() -> {
            VirtualFile file = resolve(name);
            if (file == null) {
                return null;
            }
            readUrls.add(file.getUrl());
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            return document != null ? document.getText() : VfsUtilCore.loadText(file);
        });
    }

    /** URLs of the files read so far, used to refresh the preview when an included template changes. */
    public Set<String> readUrls() {
        return Set.copyOf(readUrls);
    }

    private @Nullable VirtualFile resolve(String name) {
        if (!root.isValid()) {
            return null;
        }
        VirtualFile file = root.findFileByRelativePath(name);
        if (file == null || !file.isValid() || file.isDirectory() || file.getFileType().isBinary()
                || file.getLength() > MAX_BYTES || !VfsUtilCore.isAncestor(root, file, true)) {
            return null;
        }
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if (rootPath == null || filePath == null || !filePath.startsWith(rootPath + "/")) {
            return null; // a symbolic link pointing outside the root
        }
        return file;
    }
}

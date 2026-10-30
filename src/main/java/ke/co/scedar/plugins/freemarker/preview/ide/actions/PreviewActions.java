package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.ide.editor.FreeMarkerSplitEditor;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the split editor an action applies to (from the toolbar, the editor, or the selected file).
 */
final class PreviewActions {

    private PreviewActions() {
    }

    static @Nullable FreeMarkerSplitEditor splitEditor(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return null;
        }
        FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
        if (fileEditor instanceof FreeMarkerSplitEditor split) {
            return split;
        }
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return null;
        }
        FileEditorManager manager = FileEditorManager.getInstance(project);
        if (manager.getSelectedEditor(file) instanceof FreeMarkerSplitEditor split) {
            return split;
        }
        for (FileEditor editor : manager.getAllEditors(file)) {
            if (editor instanceof FreeMarkerSplitEditor split) {
                return split;
            }
        }
        return null;
    }

    static @Nullable PreviewController controller(AnActionEvent e) {
        FreeMarkerSplitEditor split = splitEditor(e);
        return split == null ? null : split.preview().controller();
    }
}

package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.ide.FreeMarkerFiles;
import ke.co.scedar.plugins.freemarker.preview.ide.editor.FreeMarkerSplitEditor;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the template (if needed) and shows the source and preview side by side.
 */
public final class OpenPreviewAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || !FreeMarkerFiles.isTemplate(file)) {
            return;
        }
        FileEditorManager manager = FileEditorManager.getInstance(project);
        for (FileEditor editor : manager.openFile(file, true)) {
            if (editor instanceof FreeMarkerSplitEditor split) {
                split.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW);
                PreviewController controller = split.preview().controller();
                if (controller != null) {
                    controller.refresh();
                }
                return;
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && FreeMarkerFiles.isTemplate(e.getData(CommonDataKeys.VIRTUAL_FILE)));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

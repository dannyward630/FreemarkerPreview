package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.NotNull;

public final class RefreshPreviewAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PreviewController controller = PreviewActions.controller(e);
        if (controller != null) {
            controller.refresh();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(PreviewActions.controller(e) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

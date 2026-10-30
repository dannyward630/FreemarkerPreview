package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.NotNull;

/** Discards custom sample data and generates it again from the template. */
public final class RegenerateSampleDataAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PreviewController controller = PreviewActions.controller(e);
        if (controller != null) {
            controller.regenerateSampleData();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PreviewController controller = PreviewActions.controller(e);
        e.getPresentation().setVisible(controller != null);
        e.getPresentation().setEnabled(controller != null);
        e.getPresentation().setDescription(controller != null && controller.isCustomData()
                ? "Discard your sample data edits and generate it from the template again"
                : "Generate sample data from the template again");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

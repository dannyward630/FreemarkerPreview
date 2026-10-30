package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.NotNull;

/**
 * Shows or hides the sample-data editor below the preview.
 */
public final class ToggleSampleDataAction extends ToggleAction implements DumbAware {

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        PreviewController controller = PreviewActions.controller(e);
        return controller != null && controller.isSampleDataVisible();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PreviewController controller = PreviewActions.controller(e);
        if (controller != null) {
            controller.setSampleDataVisible(state);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(PreviewActions.controller(e) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}

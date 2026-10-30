package ke.co.scedar.plugins.freemarker.preview.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import ke.co.scedar.plugins.freemarker.preview.ide.PreviewSettings;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import org.jetbrains.annotations.NotNull;

/**
 * Allows or blocks images, stylesheets and fonts referenced by http(s) URLs in all previews.
 */
public final class ToggleRemoteResourcesAction extends ToggleAction implements DumbAware {

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return PreviewSettings.getInstance().isLoadRemoteResources();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PreviewSettings.getInstance().setLoadRemoteResources(state);
        PreviewController controller = PreviewActions.controller(e);
        if (controller != null) {
            controller.refresh();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(PreviewActions.controller(e) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

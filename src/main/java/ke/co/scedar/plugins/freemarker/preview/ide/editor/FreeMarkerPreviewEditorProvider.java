package ke.co.scedar.plugins.freemarker.preview.ide.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ke.co.scedar.plugins.freemarker.preview.ide.FreeMarkerFiles;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.FreeMarkerPreviewFileEditor;
import org.jetbrains.annotations.NotNull;

/** Creates the preview half; not registered on its own, only used by {@link FreeMarkerSplitEditorProvider}. */
final class FreeMarkerPreviewEditorProvider implements FileEditorProvider, DumbAware {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return FreeMarkerFiles.isTemplate(file);
    }

    @Override
    public boolean acceptRequiresReadAction() {
        return false;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new FreeMarkerPreviewFileEditor(project, file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return "freemarker-preview";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
}

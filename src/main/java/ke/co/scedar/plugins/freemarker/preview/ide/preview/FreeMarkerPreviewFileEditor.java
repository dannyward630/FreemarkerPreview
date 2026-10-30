package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;

/**
 * The preview half of the split editor. Owns the browser, sample-data editor and controller; disposing it disposes
 * all of them (including the JCEF browser) and stops pending background work.
 */
public final class FreeMarkerPreviewFileEditor extends UserDataHolderBase implements FileEditor {

    private final VirtualFile file;
    private final PreviewPanel panel;
    private final @Nullable PreviewController controller;

    public FreeMarkerPreviewFileEditor(Project project, VirtualFile file) {
        this.file = file;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        PreviewController[] controllerRef = new PreviewController[1];
        SampleDataPanel sampleData = new SampleDataPanel(project, this,
                text -> {
                    if (controllerRef[0] != null) {
                        controllerRef[0].onSampleDataEdited(text);
                    }
                },
                () -> {
                    if (controllerRef[0] != null) {
                        controllerRef[0].regenerateSampleData();
                    }
                });
        panel = new PreviewPanel(PreviewBrowser.create(), sampleData);
        Disposer.register(this, panel);
        if (document == null) {
            controller = null;
            panel.showMessage("Cannot preview this file", "The file could not be opened as text.", java.util.List.of());
            return;
        }
        controller = new PreviewController(project, file, document, panel, sampleData);
        controllerRef[0] = controller;
        Disposer.register(this, controller);
        controller.schedule(0);
    }

    public @Nullable PreviewController controller() {
        return controller;
    }

    @TestOnly
    public String statusText() {
        return panel.statusText();
    }

    @TestOnly
    public @Nullable String lastPageHtml() {
        return panel.browser() instanceof SourcePreviewBrowser source ? source.lastHtml() : null;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return panel.preferredFocus();
    }

    @Override
    public @NotNull String getName() {
        return "Preview";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void dispose() {
    }
}

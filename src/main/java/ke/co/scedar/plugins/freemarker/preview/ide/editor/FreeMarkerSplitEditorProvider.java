package ke.co.scedar.plugins.freemarker.preview.ide.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider;
import com.intellij.openapi.project.DumbAware;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.FreeMarkerPreviewFileEditor;
import org.jetbrains.annotations.NotNull;

/**
 * Opens {@code .ftl}, {@code .ftlh} and {@code .ftlx} files as source + live preview. The regular text editor (with
 * whatever FreeMarker language support is installed) is used for the source side.
 */
public final class FreeMarkerSplitEditorProvider extends TextEditorWithPreviewProvider implements DumbAware {

    public FreeMarkerSplitEditorProvider() {
        super(new FreeMarkerPreviewEditorProvider());
    }

    @Override
    protected @NotNull FileEditor createSplitEditor(@NotNull TextEditor firstEditor, @NotNull FileEditor secondEditor) {
        return new FreeMarkerSplitEditor(firstEditor, (FreeMarkerPreviewFileEditor) secondEditor);
    }
}

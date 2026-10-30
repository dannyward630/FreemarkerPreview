package ke.co.scedar.plugins.freemarker.preview.ide.editor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.FreeMarkerPreviewFileEditor;
import org.jetbrains.annotations.NotNull;

/**
 * Source on the left, preview on the right, with the preview actions in the editor toolbar.
 */
public final class FreeMarkerSplitEditor extends TextEditorWithPreview {

    public static final String TOOLBAR_GROUP_ID = "FreeMarkerPreview.EditorToolbar";

    public FreeMarkerSplitEditor(@NotNull TextEditor editor, @NotNull FreeMarkerPreviewFileEditor preview) {
        super(editor, preview, "FreeMarker", Layout.SHOW_EDITOR_AND_PREVIEW);
    }

    public FreeMarkerPreviewFileEditor preview() {
        return (FreeMarkerPreviewFileEditor) myPreview;
    }

    @Override
    protected ActionGroup createLeftToolbarActionGroup() {
        AnAction group = ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID);
        return group instanceof ActionGroup actionGroup ? actionGroup : super.createLeftToolbarActionGroup();
    }
}

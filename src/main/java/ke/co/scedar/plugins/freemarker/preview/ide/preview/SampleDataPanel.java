package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.TestOnly;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;

/**
 * JSON editor for the sample data model. Shows whether the data is generated (follows the template) or custom (the
 * user's edits are kept and only missing variables are filled in from the generated model).
 */
final class SampleDataPanel extends JPanel {

    private final Document document = EditorFactory.getInstance().createDocument("");
    private final JBLabel modeLabel = new JBLabel();
    private final JBLabel notesLabel = new JBLabel();
    private boolean updatingProgrammatically;

    SampleDataPanel(Project project, Disposable parent, Consumer<String> onUserEdit, Runnable onRegenerate) {
        super(new BorderLayout());

        EditorTextField editor = new EditorTextField(document, project, jsonFileType(), false, false);
        editor.addSettingsProvider(e -> {
            e.setVerticalScrollbarVisible(true);
            e.setHorizontalScrollbarVisible(true);
            e.getSettings().setLineNumbersShown(true);
            e.getSettings().setFoldingOutlineShown(true);
            e.getSettings().setUseSoftWraps(false);
            e.setBorder(JBUI.Borders.empty());
        });

        ActionLink regenerate = new ActionLink("Regenerate", (ActionListener) e -> onRegenerate.run());
        regenerate.setToolTipText("Discard edits and generate sample data from the template again");

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()), JBUI.Borders.empty(4, 8)));
        header.add(modeLabel, BorderLayout.CENTER);
        header.add(regenerate, BorderLayout.EAST);

        notesLabel.setForeground(UIUtil.getContextHelpForeground());
        notesLabel.setFont(JBUI.Fonts.smallFont());
        notesLabel.setBorder(JBUI.Borders.empty(0, 8, 4, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        header.setAlignmentX(LEFT_ALIGNMENT);
        notesLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(header);
        top.add(notesLabel);

        add(top, BorderLayout.NORTH);
        add(editor, BorderLayout.CENTER);

        document.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                if (!updatingProgrammatically) {
                    onUserEdit.accept(document.getText());
                }
            }
        }, parent);
        setCustom(false);
    }

    /** Replaces the text without treating it as a user edit. */
    void setText(String text) {
        if (text.equals(document.getText())) {
            return;
        }
        updatingProgrammatically = true;
        try {
            CommandProcessor.getInstance().runUndoTransparentAction(() -> WriteAction.run(() -> document.setText(text)));
        } finally {
            updatingProgrammatically = false;
        }
    }

    String getText() {
        return document.getText();
    }

    void setCustom(boolean custom) {
        if (custom) {
            modeLabel.setIcon(AllIcons.Actions.Edit);
            modeLabel.setText("Custom sample data");
            modeLabel.setToolTipText("Your edits are kept. Variables you have not set are still generated from the "
                    + "template. Use null to make a value missing.");
        } else {
            modeLabel.setIcon(AllIcons.Actions.Lightning);
            modeLabel.setText("Generated sample data");
            modeLabel.setToolTipText("Generated from the template and updated as you type. Edit it to customize.");
        }
    }

    void setNotes(List<String> notes) {
        if (notes.isEmpty()) {
            notesLabel.setText("");
            notesLabel.setVisible(false);
            return;
        }
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < notes.size() && i < 6; i++) {
            html.append(i > 0 ? "<br>" : "").append(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(notes.get(i)));
        }
        if (notes.size() > 6) {
            html.append("<br>… and ").append(notes.size() - 6).append(" more");
        }
        notesLabel.setText(html.append("</html>").toString());
        notesLabel.setVisible(true);
    }

    /** Replaces the text as if the user had typed it. */
    @TestOnly
    void setTextAsUser(String text) {
        WriteAction.run(() -> document.setText(text));
    }

    private static FileType jsonFileType() {
        FileType type = FileTypeManager.getInstance().getFileTypeByExtension("json");
        return type instanceof UnknownFileType ? PlainTextFileType.INSTANCE : type;
    }
}

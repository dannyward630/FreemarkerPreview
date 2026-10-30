package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Used when JCEF is unavailable (e.g. some remote-development setups or headless tests). Shows the generated page as
 * text instead of rendering it with Swing's HTML support, which would fetch remote resources referenced by the
 * template.
 */
final class SourcePreviewBrowser implements PreviewBrowser {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JBTextArea text = new JBTextArea();
    private volatile String lastHtml = "";

    SourcePreviewBrowser() {
        text.setEditable(false);
        text.setLineWrap(true);
        text.setFont(JBUI.Fonts.create("Monospaced", UIUtil.getLabelFont().getSize()));
        JComponent notice = new com.intellij.ui.components.JBLabel(
                "The embedded browser (JCEF) is not available in this IDE; showing the generated HTML instead.");
        notice.setBorder(JBUI.Borders.empty(6, 8));
        panel.add(notice, BorderLayout.NORTH);
        panel.add(new JBScrollPane(text), BorderLayout.CENTER);
    }

    @Override
    public JComponent component() {
        return panel;
    }

    @Override
    public void load(String html) {
        lastHtml = html;
        text.setText(html);
        text.setCaretPosition(0);
    }

    String lastHtml() {
        return lastHtml;
    }

    @Override
    public void dispose() {
    }
}

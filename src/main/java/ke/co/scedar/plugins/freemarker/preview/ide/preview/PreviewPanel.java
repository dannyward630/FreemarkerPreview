package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import ke.co.scedar.plugins.freemarker.preview.render.PreviewHtml;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic;
import ke.co.scedar.plugins.freemarker.preview.render.RenderResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The right-hand side of the split editor: status line, rendered page and the sample-data editor.
 */
final class PreviewPanel extends JPanel implements Disposable {

    private static final String SAMPLE_DATA_VISIBLE_KEY = "FreeMarkerPreview.sampleDataVisible";

    private final PreviewBrowser browser;
    private final SampleDataPanel sampleData;
    private final OnePixelSplitter splitter;
    private final JBLabel status = new JBLabel();

    PreviewPanel(PreviewBrowser browser, SampleDataPanel sampleData) {
        super(new BorderLayout());
        this.browser = browser;
        this.sampleData = sampleData;
        Disposer.register(this, browser);

        status.setBorder(JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(3, 8)));
        status.setFont(JBUI.Fonts.smallFont());
        splitter = new OnePixelSplitter(true, "FreeMarkerPreview.sampleDataProportion", 0.68f);
        splitter.setFirstComponent(browser.component());
        setSampleDataVisible(PropertiesComponent.getInstance().getBoolean(SAMPLE_DATA_VISIBLE_KEY, true));

        add(status, BorderLayout.NORTH);
        add(splitter, BorderLayout.CENTER);
        showMessage("Preparing preview…", "The template is being analyzed.", List.of());
    }

    private static PreviewHtml.Theme theme() {
        return JBColor.isBright() ? PreviewHtml.Theme.light() : PreviewHtml.Theme.dark();
    }

    JComponent preferredFocus() {
        return browser.component();
    }

    boolean isSampleDataVisible() {
        return splitter.getSecondComponent() != null;
    }

    void setSampleDataVisible(boolean visible) {
        splitter.setSecondComponent(visible ? sampleData : null);
        PropertiesComponent.getInstance().setValue(SAMPLE_DATA_VISIBLE_KEY, visible, true);
        revalidate();
        repaint();
    }

    void showResult(RenderResult result, boolean customData) {
        showResult(result, customData, null);
    }

    /**
     * @param pathWarning shown in the status line, e.g. when a fragment was found only by the lenient path fallback
     */
    void showResult(RenderResult result, boolean customData, @org.jetbrains.annotations.Nullable String pathWarning) {
        String data = customData ? "custom sample data" : "generated sample data";
        if (result instanceof RenderResult.Success success && pathWarning != null) {
            boolean remote = ke.co.scedar.plugins.freemarker.preview.ide.PreviewSettings.getInstance().isLoadRemoteResources();
            browser.setRemoteResourcesAllowed(remote);
            browser.load(PreviewHtml.forResult(success, remote));
            setStatus(AllIcons.General.Warning, "Rendered with " + data + " · " + pathWarning);
        } else if (result instanceof RenderResult.Success success) {
            boolean remote = ke.co.scedar.plugins.freemarker.preview.ide.PreviewSettings.getInstance().isLoadRemoteResources();
            browser.setRemoteResourcesAllowed(remote);
            browser.load(PreviewHtml.forResult(success, remote));
            setStatus(AllIcons.General.InspectionsOK, "Rendered in " + success.millis() + " ms with " + data
                    + (success.kind() == RenderResult.OutputKind.HTML ? "" : " · shown as " + success.kind().name().toLowerCase() + " text"));
        } else if (result instanceof RenderResult.Failure failure) {
            RenderDiagnostic d = failure.diagnostic();
            browser.load(PreviewHtml.forDiagnostic(d, theme()));
            setStatus(d.kind() == RenderDiagnostic.Kind.TEMPLATE_SYNTAX || d.kind() == RenderDiagnostic.Kind.SAMPLE_DATA
                    ? AllIcons.General.Warning : AllIcons.General.Error, d.title() + (d.location().isEmpty() ? "" : " · " + d.location()));
        }
    }

    void showMessage(String title, String text, List<String> details) {
        browser.load(PreviewHtml.message(title, text, details, theme()));
        setStatus(AllIcons.General.Information, title);
    }

    private void setStatus(javax.swing.Icon icon, String text) {
        status.setIcon(icon);
        status.setText(text);
        status.setToolTipText(text);
    }

    String statusText() {
        return status.getText();
    }

    PreviewBrowser browser() {
        return browser;
    }

    @Override
    public void dispose() {
    }
}

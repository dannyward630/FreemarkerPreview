package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.openapi.Disposable;
import com.intellij.ui.jcef.JBCefApp;

import javax.swing.*;

/**
 * Displays preview pages. Must be used on the EDT.
 */
interface PreviewBrowser extends Disposable {

    static PreviewBrowser create() {
        return JBCefApp.isSupported() ? new JcefPreviewBrowser() : new SourcePreviewBrowser();
    }

    JComponent component();

    void load(String html);

    /**
     * Whether images, stylesheets and fonts may be fetched from http(s) URLs for the next pages.
     */
    default void setRemoteResourcesAllowed(boolean allowed) {
    }
}

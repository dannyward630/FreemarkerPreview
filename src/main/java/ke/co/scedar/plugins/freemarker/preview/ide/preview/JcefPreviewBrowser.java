package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefFileSchemeHandlerFactory;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

import javax.swing.*;

/**
 * JCEF-based preview. Besides the Content-Security-Policy in every page, all requests are filtered here: the in-memory
 * page itself, {@code data:} URLs and, when allowed, images, stylesheets and fonts from {@code http(s)} load; scripts,
 * frames and local files never do. Navigation away from the preview is cancelled; links the user clicks on are opened
 * in the system browser instead.
 */
final class JcefPreviewBrowser implements PreviewBrowser {

    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(JcefPreviewBrowser.class);

    /**
     * {@link JBCefBrowser#loadHTML} serves pages from this in-memory pseudo location.
     */
    private static final String PAGE_PREFIX = JBCefFileSchemeHandlerFactory.LOADHTML_RANDOM_URL_PREFIX;
    /**
     * {@link JBCefBrowser#loadHTML} silently drops a page whose URL equals the previously requested one (even after that
     * load finished), so with one URL only the first page ever showed. Pages alternate between two URLs: never the last
     * one requested, and the platform keeps only two pages per browser.
     */
    private static final String[] PAGE_URLS = {PAGE_PREFIX + "freemarker-preview-a.html", PAGE_PREFIX + "freemarker-preview-b.html"};

    private final JBCefBrowser browser;
    private volatile boolean remoteAllowed;
    /**
     * The newest page; loaded once the native browser exists. Only the EDT writes it.
     */
    private volatile @org.jetbrains.annotations.Nullable String latestHtml;
    private volatile boolean created;
    private volatile boolean disposed;
    private int pageIndex;

    JcefPreviewBrowser() {
        browser = JBCefBrowser.createBuilder().setEnableOpenDevToolsMenuItem(false).build();
        browser.getJBCefClient().addRequestHandler(new RequestGuard(this), browser.getCefBrowser());
        browser.getJBCefClient().addLifeSpanHandler(new org.cef.handler.CefLifeSpanHandlerAdapter() {
            @Override
            public void onAfterCreated(CefBrowser cefBrowser) {
                // JCEF itself would replay only one page queued before creation, and could replay it after a newer
                // one was loaded; instead, show whatever is newest once the browser is ready.
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    created = true;
                    String html = latestHtml;
                    LOG.debug("browser created; loading newest page (", html == null ? 0 : html.length(), " chars)");
                    if (html != null) {
                        loadNow(html);
                    }
                }, com.intellij.openapi.application.ModalityState.any(), o -> disposed);
            }
        }, browser.getCefBrowser());
        Disposer.register(this, browser);
    }

    private static boolean isAllowed(String url) {
        return url.startsWith(PAGE_PREFIX) || url.startsWith("data:") || url.equals("about:blank");
    }

    private static boolean isRemoteAsset(CefRequest request) {
        String url = request.getURL().toLowerCase(java.util.Locale.ROOT);
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return false;
        }
        CefRequest.ResourceType type = request.getResourceType();
        return type == CefRequest.ResourceType.RT_IMAGE || type == CefRequest.ResourceType.RT_STYLESHEET
                || type == CefRequest.ResourceType.RT_FONT_RESOURCE || type == CefRequest.ResourceType.RT_MEDIA
                || type == CefRequest.ResourceType.RT_FAVICON;
    }

    @Override
    public JComponent component() {
        return browser.getComponent();
    }

    @Override
    public void load(String html) {
        latestHtml = html;
        LOG.debug(created ? "loading page (" : "browser not created yet; keeping page (", html.length(), " chars)");
        if (created) {
            loadNow(html);
        }
    }

    private void loadNow(String html) {
        pageIndex ^= 1;
        browser.loadHTML(html, PAGE_URLS[pageIndex]);
    }

    @Override
    public void setRemoteResourcesAllowed(boolean allowed) {
        remoteAllowed = allowed;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    private static final class RequestGuard extends CefRequestHandlerAdapter {
        private final JcefPreviewBrowser owner;
        private final CefResourceRequestHandler resources = new CefResourceRequestHandlerAdapter() {
            @Override
            public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
                boolean allowed = isAllowed(request.getURL()) || owner.remoteAllowed && isRemoteAsset(request);
                return !allowed; // true cancels the request
            }
        };

        RequestGuard(JcefPreviewBrowser owner) {
            this.owner = owner;
        }

        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean userGesture,
                                      boolean isRedirect) {
            String url = request.getURL();
            if (frame.isMain() && isAllowed(url) && !url.startsWith("data:")) {
                return false;
            }
            if (userGesture && (url.startsWith("https://") || url.startsWith("http://"))) {
                BrowserUtil.browse(url);
            }
            return true;
        }

        @Override
        public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl, boolean userGesture) {
            return true;
        }

        @Override
        public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame,
                                                                   CefRequest request, boolean isNavigation,
                                                                   boolean isDownload, String requestInitiator,
                                                                   BoolRef disableDefaultHandling) {
            return resources;
        }
    }
}

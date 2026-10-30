package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * IDE-wide preview settings.
 */
@Service(Service.Level.APP)
@State(name = "FreeMarkerPreviewSettings", storages = @Storage("freemarkerPreview.xml"))
public final class PreviewSettings implements PersistentStateComponent<PreviewSettings.SettingsState> {

    private volatile SettingsState state = new SettingsState();

    public static PreviewSettings getInstance() {
        return ApplicationManager.getApplication().getService(PreviewSettings.class);
    }

    public boolean isLoadRemoteResources() {
        return state.loadRemoteResources;
    }

    public void setLoadRemoteResources(boolean load) {
        state.loadRemoteResources = load;
    }

    @Override
    public @NotNull SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState loaded) {
        state = loaded;
    }

    public static final class SettingsState {
        /**
         * Load images, stylesheets and fonts referenced by http(s) URLs (e.g. logos, CDN icon fonts).
         */
        public boolean loadRemoteResources = true;
    }
}

package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * Remembers user-edited sample data per template between IDE sessions. Stored in the workspace file
 * ({@code .idea/workspace.xml}), which is personal and normally not committed.
 */
@Service(Service.Level.PROJECT)
@State(name = "FreeMarkerPreviewSampleData", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SampleDataService implements PersistentStateComponent<SampleDataService.PersistedState> {

    /** Larger edits are kept for the session but not persisted, to keep the workspace file small. */
    static final int MAX_PERSISTED_CHARS = 200_000;

    public static final class PersistedState {
        public Map<String, String> customSampleData = new TreeMap<>();
    }

    private PersistedState state = new PersistedState();

    public static SampleDataService getInstance(Project project) {
        return project.getService(SampleDataService.class);
    }

    @Override
    public synchronized @NotNull PersistedState getState() {
        return state;
    }

    @Override
    public synchronized void loadState(@NotNull PersistedState loaded) {
        state = loaded;
    }

    public synchronized @Nullable String get(VirtualFile file) {
        return state.customSampleData.get(file.getUrl());
    }

    public synchronized void put(VirtualFile file, String json) {
        if (json.length() <= MAX_PERSISTED_CHARS) {
            state.customSampleData.put(file.getUrl(), json);
        } else {
            state.customSampleData.remove(file.getUrl());
        }
    }

    public synchronized void remove(VirtualFile file) {
        state.customSampleData.remove(file.getUrl());
    }
}

package ke.co.scedar.plugins.freemarker.preview.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies user-edited sample data on top of the generated model, so variables added to the template later still get
 * generated values while every value the user changed is kept.
 * <p>
 * Objects merge recursively; any other override value (including arrays) replaces the generated one; an explicit
 * {@code null} makes the variable missing, which is how users preview {@code x!"default"} and {@code x??} branches.
 */
public final class ModelMerger {

    private ModelMerger() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> generated, Map<String, Object> overrides) {
        Map<String, Object> result = new LinkedHashMap<>(generated);
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            Object override = entry.getValue();
            Object base = result.get(entry.getKey());
            if (override instanceof Map<?, ?> overrideMap && base instanceof Map<?, ?> baseMap) {
                result.put(entry.getKey(), merge((Map<String, Object>) baseMap, (Map<String, Object>) overrideMap));
            } else {
                result.put(entry.getKey(), override);
            }
        }
        return result;
    }
}

package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;

final class CoreAddonStateJson {
    private CoreAddonStateJson() {
    }

    static Map<String, String> values(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> source = CoreJson.objectValue(root, "values");
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = CoreJson.textValue(key).trim();
            if (!safeKey.isBlank() && value != null) {
                values.put(safeKey, CoreJson.textValue(value));
            }
        });
        return Map.copyOf(values);
    }
}

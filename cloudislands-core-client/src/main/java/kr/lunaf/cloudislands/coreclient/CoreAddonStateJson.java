package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreAddonStateJson {
    private CoreAddonStateJson() {
    }

    static Map<String, String> values(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> source = SimpleJson.object(root.get("values"));
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = SimpleJson.text(key).trim();
            if (!safeKey.isBlank() && value != null) {
                values.put(safeKey, SimpleJson.text(value));
            }
        });
        return Map.copyOf(values);
    }
}

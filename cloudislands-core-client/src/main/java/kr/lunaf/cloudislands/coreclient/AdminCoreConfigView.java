package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public record AdminCoreConfigView(Map<String, Object> values, String code) {
    public AdminCoreConfigView {
        values = values == null ? Map.of() : Map.copyOf(values);
        code = code == null ? "" : code;
    }

    public String text(String key) {
        return SimpleJson.text(values.get(key));
    }

    public boolean bool(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(SimpleJson.text(value));
    }

    public long number(String key) {
        return SimpleJson.number(values.get(key));
    }

    static AdminCoreConfigView parse(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body == null || body.isBlank() ? "{}" : body));
        Map<String, Object> values = new LinkedHashMap<>();
        root.forEach((key, value) -> values.put(SimpleJson.text(key), value));
        Map<?, ?> error = SimpleJson.object(root.get("error"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = SimpleJson.text(error.get("code"));
        }
        return new AdminCoreConfigView(values, code);
    }
}

package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;

public record AdminCoreConfigView(Map<String, Object> values, String code) {
    public AdminCoreConfigView {
        values = values == null ? Map.of() : Map.copyOf(values);
        code = code == null ? "" : code;
    }

    public String text(String key) {
        return CoreJson.text(values, key);
    }

    public boolean bool(String key) {
        return CoreJson.bool(values, key);
    }

    public long number(String key) {
        return CoreJson.number(values, key);
    }

    static AdminCoreConfigView parse(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<String, Object> values = new LinkedHashMap<>();
        root.forEach((key, value) -> values.put(CoreJson.textValue(key), value));
        Map<?, ?> error = CoreJson.objectValue(root, "error");
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = CoreJson.text(error, "code");
        }
        return new AdminCoreConfigView(values, code);
    }
}

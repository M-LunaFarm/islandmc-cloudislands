package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreJson {
    private CoreJson() {
    }

    static Map<?, ?> object(String body) {
        return SimpleJson.object(value(body));
    }

    static Object value(String body) {
        return SimpleJson.parse(body == null || body.isBlank() ? "{}" : body);
    }

    static List<Map<?, ?>> entries(String body) {
        Object parsed = value(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
    }

    static boolean accepted(Map<?, ?> root) {
        return root != null
            && !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("ok"))
            && !Boolean.FALSE.equals(root.get("applied"));
    }

    static boolean acceptedWithCode(Map<?, ?> root, String successCode) {
        String code = text(root, "code");
        return accepted(root) && (code.isBlank() || code.equals(successCode));
    }

    static String code(Map<?, ?> root, String successCode) {
        return code(root, successCode, accepted(root));
    }

    static String code(Map<?, ?> root, String successCode, boolean accepted) {
        String code = text(root, "code");
        if (!code.isBlank()) {
            return code;
        }
        return accepted ? successCode : "FAILED";
    }

    static String text(Map<?, ?> root, String key) {
        return root == null ? "" : SimpleJson.text(root.get(key));
    }

    static String firstText(Map<?, ?> root, String... keys) {
        for (String key : keys) {
            String value = text(root, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static long number(Map<?, ?> root, String key) {
        return root == null ? 0L : SimpleJson.number(root.get(key));
    }

    static List<String> strings(Map<?, ?> root, String key) {
        return SimpleJson.list(root == null ? null : root.get(key)).stream()
            .map(SimpleJson::text)
            .filter(text -> !text.isBlank())
            .toList();
    }
}

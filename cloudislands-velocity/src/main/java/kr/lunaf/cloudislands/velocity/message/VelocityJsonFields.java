package kr.lunaf.cloudislands.velocity.message;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class VelocityJsonFields {
    private VelocityJsonFields() {
    }

    public static String jsonValue(String json, String key) {
        Object value = root(json).get(key);
        return value instanceof Map<?, ?> || value instanceof List<?> ? "" : SimpleJson.text(value);
    }

    public static String arrayValue(String body, String field) {
        Object value = root(body).get(field);
        return value instanceof List<?> ? SimpleJson.stringify(value) : "";
    }

    public static String objectValue(String body, String field) {
        Object value = root(body).get(field);
        return value instanceof Map<?, ?> ? SimpleJson.stringify(value) : "";
    }

    public static List<String> objects(String body, String field) {
        return objectsFromArray(arrayValue(body, field));
    }

    public static List<String> objectsFromArray(String array) {
        if (array == null || array.isBlank()) {
            return List.of();
        }
        Object parsed = value(array);
        if (parsed instanceof List<?> list) {
            return list.stream()
                .map(SimpleJson::object)
                .filter(object -> !object.isEmpty())
                .map(SimpleJson::stringify)
                .toList();
        }
        java.util.ArrayList<String> objects = new java.util.ArrayList<>();
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            objects.add(array.substring(objectStart, objectEnd + 1));
            index = objectEnd + 1;
        }
        return List.copyOf(objects);
    }

    public static boolean boolValue(String body, String field) {
        Object value = root(body).get(field);
        return value instanceof Boolean booleanValue ? booleanValue : Boolean.parseBoolean(SimpleJson.text(value));
    }

    public static long longValue(String body, String field) {
        return SimpleJson.number(root(body).get(field));
    }

    public static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public static double doubleValue(String body, String field) {
        Object value = root(body).get(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(SimpleJson.text(value));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    public static int countObjects(String array) {
        return objectsFromArray(array).size();
    }

    public static int matchingObjectEnd(String value, int objectStart) {
        if (value == null || objectStart < 0 || objectStart >= value.length()) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Map<?, ?> root(String json) {
        return SimpleJson.object(value(json));
    }

    private static Object value(String json) {
        if (!balanced(json)) {
            return null;
        }
        try {
            return SimpleJson.parse(json);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean balanced(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
            } else if (current == '[') {
                arrayDepth++;
            } else if (current == ']') {
                arrayDepth--;
            }
            if (objectDepth < 0 || arrayDepth < 0) {
                return false;
            }
        }
        return !inString && objectDepth == 0 && arrayDepth == 0;
    }

}

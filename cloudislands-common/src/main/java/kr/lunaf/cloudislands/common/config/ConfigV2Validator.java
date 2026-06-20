package kr.lunaf.cloudislands.common.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigV2Validator {
    private ConfigV2Validator() {
    }

    public static ConfigValidationResult validateYaml(String sourceName, String yaml) {
        List<ConfigIssue> issues = new ArrayList<>();
        if (yaml == null || yaml.isBlank()) {
            issues.add(new ConfigIssue("EMPTY_CONFIG", sourceName, "config source is empty"));
            return new ConfigValidationResult(issues);
        }
        duplicateKeyIssues(sourceName, yaml, issues);
        plaintextSecretIssues(sourceName, yaml, issues);
        return new ConfigValidationResult(issues);
    }

    public static ConfigValidationResult validateMenuYaml(String sourceName, String yaml, Set<String> registeredActions) {
        List<ConfigIssue> issues = new ArrayList<>(validateYaml(sourceName, yaml).issues());
        if (yaml == null || yaml.isBlank()) {
            return new ConfigValidationResult(issues);
        }
        MenuYaml menu = MenuYaml.parse(yaml);
        if (menu.rows() < 1 || menu.rows() > 6) {
            issues.add(new ConfigIssue("MENU_ROWS_RANGE", "rows", sourceName));
        }
        if (!menu.layout().isEmpty() && menu.layout().size() > menu.rows()) {
            issues.add(new ConfigIssue("MENU_LAYOUT_ROW_COUNT", "layout", sourceName));
        }
        for (int index = 0; index < menu.layout().size(); index++) {
            String row = menu.layout().get(index);
            if (row.length() != 9) {
                issues.add(new ConfigIssue("MENU_LAYOUT_ROW_WIDTH", "layout[" + index + "]", sourceName + ":" + row.length()));
            }
            for (int column = 0; column < row.length(); column++) {
                char symbol = row.charAt(column);
                if (symbol != '.' && !menu.itemSymbols().contains(String.valueOf(symbol))) {
                    issues.add(new ConfigIssue("MENU_LAYOUT_UNKNOWN_SYMBOL", "layout[" + index + "][" + column + "]", sourceName + ":" + symbol));
                }
            }
        }
        Set<String> allowed = registeredActions == null ? Set.of() : registeredActions;
        for (Map.Entry<String, String> action : menu.actions().entrySet()) {
            if (action.getValue().isBlank()) {
                issues.add(new ConfigIssue("MENU_ACTION_EMPTY", action.getKey(), sourceName));
            } else if (!allowed.isEmpty() && !allowed.contains(action.getValue())) {
                issues.add(new ConfigIssue("MENU_ACTION_UNREGISTERED", action.getKey(), sourceName + ":" + action.getValue()));
            }
        }
        return new ConfigValidationResult(issues);
    }

    public static String redactYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return "";
        }
        List<String> output = new ArrayList<>();
        for (String line : yaml.split("\\R", -1)) {
            String trimmed = line.trim();
            int separator = trimmed.indexOf(':');
            if (separator > 0 && !trimmed.startsWith("#") && secretKey(trimmed.substring(0, separator))) {
                String indent = line.substring(0, line.indexOf(trimmed));
                output.add(indent + trimmed.substring(0, separator).trim() + ": <redacted>");
            } else {
                output.add(line);
            }
        }
        return String.join(System.lineSeparator(), output);
    }

    public static boolean secretKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("password")
            || normalized.endsWith("-password")
            || normalized.endsWith("_password")
            || normalized.equals("token")
            || normalized.endsWith("-token")
            || normalized.endsWith("_token")
            || normalized.equals("secret")
            || normalized.endsWith("-secret")
            || normalized.endsWith("_secret")
            || normalized.equals("access-key")
            || normalized.equals("secret-key")
            || normalized.equals("bearer-token")
            || normalized.equals("auth-token")
            || normalized.equals("admin-token");
    }

    private static void duplicateKeyIssues(String sourceName, String yaml, List<ConfigIssue> issues) {
        Map<String, Set<String>> seenByParent = new HashMap<>();
        List<Frame> stack = new ArrayList<>();
        String[] lines = yaml.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            int indent = leadingSpaces(raw);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                stack.remove(stack.size() - 1);
            }
            String key = trimmed.substring(0, separator).trim();
            String parent = stackPath(stack);
            Set<String> seen = seenByParent.computeIfAbsent(parent, ignored -> new HashSet<>());
            if (!seen.add(key)) {
                issues.add(new ConfigIssue("DUPLICATE_KEY", path(parent, key), sourceName + ":" + (index + 1)));
            }
            String value = trimmed.substring(separator + 1).trim();
            if (value.isBlank()) {
                stack.add(new Frame(indent, key));
            }
        }
    }

    private static void plaintextSecretIssues(String sourceName, String yaml, List<ConfigIssue> issues) {
        String[] lines = yaml.split("\\R");
        List<Frame> stack = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            int indent = leadingSpaces(raw);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                stack.remove(stack.size() - 1);
            }
            String key = trimmed.substring(0, separator).trim();
            String value = cleanScalar(trimmed.substring(separator + 1).trim());
            String currentPath = path(stackPath(stack), key);
            if (secretKey(key) && !safeSecretReference(value)) {
                issues.add(new ConfigIssue("PLAINTEXT_SECRET", currentPath, sourceName + ":" + (index + 1)));
            }
            if (trimmed.substring(separator + 1).trim().isBlank()) {
                stack.add(new Frame(indent, key));
            }
        }
    }

    private static boolean safeSecretReference(String value) {
        return value.isBlank()
            || value.equals("<redacted>")
            || value.startsWith("${env:")
            || value.startsWith("${file:")
            || value.startsWith("<");
    }

    static String cleanScalar(String value) {
        String cleaned = value == null ? "" : value.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String stackPath(List<Frame> stack) {
        return stack.stream().map(Frame::key).reduce((left, right) -> left + "." + right).orElse("");
    }

    private static String path(String parent, String key) {
        return parent == null || parent.isBlank() ? key : parent + "." + key;
    }

    private record Frame(int indent, String key) {
    }

    private record MenuYaml(int rows, List<String> layout, Set<String> itemSymbols, Map<String, String> actions) {
        private static MenuYaml parse(String yaml) {
            int rows = 0;
            List<String> layout = new ArrayList<>();
            Set<String> itemSymbols = new HashSet<>();
            Map<String, String> actions = new HashMap<>();
            List<Frame> stack = new ArrayList<>();
            String currentItem = "";
            String[] lines = yaml.split("\\R");
            for (String raw : lines) {
                String trimmed = raw.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                int indent = leadingSpaces(raw);
                while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                    Frame removed = stack.remove(stack.size() - 1);
                    if (removed.key().equals(currentItem)) {
                        currentItem = "";
                    }
                }
                String parent = stackPath(stack);
                if (trimmed.startsWith("-") && parent.endsWith("layout")) {
                    String value = listValue(trimmed.substring(1).trim());
                    if (!value.isBlank()) {
                        layout.add(value);
                    }
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String rawValue = trimmed.substring(separator + 1).trim();
                String value = cleanScalar(rawValue);
                String path = path(parent, key);
                if (parent.isBlank() && key.equals("rows")) {
                    rows = integer(value, 0);
                } else if (parent.equals("items") && rawValue.isBlank()) {
                    currentItem = key;
                    itemSymbols.add(key);
                } else if (path.equals("items." + currentItem + ".action")) {
                    actions.put(path, value);
                } else if (path.endsWith(".actions." + key) || path.endsWith(".footer-actions." + key) || path.startsWith("actions.") || path.startsWith("footer-actions.")) {
                    actions.put(path, value);
                }
                if (rawValue.isBlank()) {
                    stack.add(new Frame(indent, key));
                }
            }
            return new MenuYaml(rows, List.copyOf(layout), Set.copyOf(itemSymbols), Map.copyOf(actions));
        }

        private static String listValue(String value) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                return "";
            }
            char first = trimmed.charAt(0);
            if (first == '"' || first == '\'') {
                int end = quotedEnd(trimmed, first);
                return end > 0 ? trimmed.substring(1, end) : "";
            }
            int end = 0;
            while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end)) && trimmed.charAt(end) != '#') {
                end++;
            }
            return trimmed.substring(0, end);
        }

        private static int quotedEnd(String value, char quote) {
            for (int index = 1; index < value.length(); index++) {
                if (value.charAt(index) == quote && value.charAt(index - 1) != '\\') {
                    return index;
                }
            }
            return -1;
        }

        private static int integer(String value, int fallback) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }
}

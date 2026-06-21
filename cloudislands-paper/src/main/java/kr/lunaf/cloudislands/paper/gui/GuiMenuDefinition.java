package kr.lunaf.cloudislands.paper.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public record GuiMenuDefinition(String id, int rows, String titleKey, Map<String, String> actions) {
    public GuiMenuDefinition {
        id = id == null || id.isBlank() ? "cloudislands.menu" : id;
        rows = Math.max(1, Math.min(rows, 6));
        titleKey = titleKey == null ? "" : titleKey;
        actions = actions == null ? Map.of() : Map.copyOf(actions);
    }

    public int size() {
        return rows * 9;
    }

    public String action(String key, String fallback) {
        String value = actions.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static GuiMenuDefinition bundled(String resourcePath, GuiMenuDefinition fallback) {
        try (InputStream input = GuiMenuDefinition.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return fallback;
            }
            return parse(input, fallback);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read menu definition " + resourcePath, exception);
        }
    }

    static GuiMenuDefinition parse(InputStream input, GuiMenuDefinition fallback) throws IOException {
        String id = fallback == null ? "cloudislands.menu" : fallback.id();
        int rows = fallback == null ? 3 : fallback.rows();
        String titleKey = fallback == null ? "" : fallback.titleKey();
        LinkedHashMap<String, String> actions = new LinkedHashMap<>(fallback == null ? Map.of() : fallback.actions());
        boolean inActions = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                int indent = leadingSpaces(line);
                String key = trimmed.substring(0, separator).trim();
                String value = clean(trimmed.substring(separator + 1).trim());
                if (indent == 0) {
                    inActions = key.equals("actions") && value.isBlank();
                    if (key.equals("id")) {
                        id = value;
                    } else if (key.equals("rows")) {
                        rows = integer(value, rows);
                    } else if (key.equals("title-key")) {
                        titleKey = value;
                    }
                    continue;
                }
                if (inActions && indent > 0 && !value.isBlank()) {
                    actions.put(key, value);
                }
            }
        }
        return new GuiMenuDefinition(id, rows, titleKey, actions);
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}

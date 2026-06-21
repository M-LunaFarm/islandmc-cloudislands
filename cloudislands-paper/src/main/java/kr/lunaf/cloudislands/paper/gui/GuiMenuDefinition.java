package kr.lunaf.cloudislands.paper.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record GuiMenuDefinition(String id, int rows, String titleKey, List<String> layout, Map<String, MenuItem> items, Map<String, String> actions) {
    public GuiMenuDefinition {
        id = id == null || id.isBlank() ? "cloudislands.menu" : id;
        rows = Math.max(1, Math.min(rows, 6));
        titleKey = titleKey == null ? "" : titleKey;
        layout = layout == null ? List.of() : List.copyOf(layout);
        items = items == null ? Map.of() : Map.copyOf(items);
        actions = actions == null ? Map.of() : Map.copyOf(actions);
    }

    public GuiMenuDefinition(String id, int rows, String titleKey, Map<String, String> actions) {
        this(id, rows, titleKey, List.of(), Map.of(), actions);
    }

    public int size() {
        return rows * 9;
    }

    public String action(String key, String fallback) {
        String value = actions.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public Optional<MenuItem> itemAt(int slot) {
        if (slot < 0 || layout.isEmpty()) {
            return Optional.empty();
        }
        int row = slot / 9;
        int column = slot % 9;
        if (row >= layout.size() || column >= layout.get(row).length()) {
            return Optional.empty();
        }
        String symbol = String.valueOf(layout.get(row).charAt(column));
        if (symbol.equals(".")) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(symbol));
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
        ArrayList<String> layout = new ArrayList<>(fallback == null ? List.of() : fallback.layout());
        LinkedHashMap<String, MenuItem.Builder> itemBuilders = new LinkedHashMap<>();
        if (fallback != null) {
            fallback.items().forEach((symbol, item) -> itemBuilders.put(symbol, item.toBuilder()));
        }
        LinkedHashMap<String, String> actions = new LinkedHashMap<>(fallback == null ? Map.of() : fallback.actions());
        boolean inActions = false;
        boolean inLayout = false;
        boolean inItems = false;
        String currentItem = "";
        String currentList = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                int indent = leadingSpaces(line);
                if (trimmed.startsWith("-")) {
                    String value = clean(trimmed.substring(1).trim());
                    if (inLayout && indent > 0) {
                        layout.add(value);
                    } else if (inItems && !currentItem.isBlank() && currentList.equals("lore-keys")) {
                        itemBuilders.computeIfAbsent(currentItem, MenuItem.Builder::new).loreKey(value);
                    } else if (inItems && !currentItem.isBlank() && currentList.equals("fallback-lore")) {
                        itemBuilders.computeIfAbsent(currentItem, MenuItem.Builder::new).fallbackLore(value);
                    }
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = clean(trimmed.substring(separator + 1).trim());
                if (indent == 0) {
                    inActions = key.equals("actions") && value.isBlank();
                    inLayout = key.equals("layout") && value.isBlank();
                    inItems = key.equals("items") && value.isBlank();
                    currentItem = "";
                    currentList = "";
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
                    continue;
                }
                if (!inItems || indent <= 0) {
                    continue;
                }
                if (indent == 2 && value.isBlank()) {
                    currentItem = key;
                    currentList = "";
                    itemBuilders.computeIfAbsent(currentItem, MenuItem.Builder::new);
                    continue;
                }
                if (currentItem.isBlank()) {
                    continue;
                }
                MenuItem.Builder builder = itemBuilders.computeIfAbsent(currentItem, MenuItem.Builder::new);
                if (value.isBlank() && (key.equals("lore-keys") || key.equals("fallback-lore"))) {
                    currentList = key;
                    continue;
                }
                currentList = "";
                switch (key) {
                    case "material" -> builder.material(value);
                    case "name-key" -> builder.nameKey(value);
                    case "fallback-name" -> builder.fallbackName(value);
                    case "action" -> builder.actionKey(value);
                    default -> {
                    }
                }
            }
        }
        LinkedHashMap<String, MenuItem> parsedItems = new LinkedHashMap<>();
        itemBuilders.forEach((symbol, builder) -> parsedItems.put(symbol, builder.build()));
        return new GuiMenuDefinition(id, rows, titleKey, layout, parsedItems, actions);
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

    public record MenuItem(
        String symbol,
        String materialKey,
        String nameKey,
        String fallbackName,
        List<String> loreKeys,
        List<String> fallbackLore,
        String actionKey
    ) {
        public MenuItem {
            symbol = symbol == null ? "" : symbol;
            materialKey = materialKey == null || materialKey.isBlank() ? "STONE" : materialKey;
            nameKey = nameKey == null ? "" : nameKey;
            fallbackName = fallbackName == null ? "" : fallbackName;
            loreKeys = loreKeys == null ? List.of() : List.copyOf(loreKeys);
            fallbackLore = fallbackLore == null ? List.of() : List.copyOf(fallbackLore);
            actionKey = actionKey == null ? "" : actionKey;
        }

        private Builder toBuilder() {
            Builder builder = new Builder(symbol);
            builder.materialKey = materialKey;
            builder.nameKey = nameKey;
            builder.fallbackName = fallbackName;
            builder.loreKeys.addAll(loreKeys);
            builder.fallbackLore.addAll(fallbackLore);
            builder.actionKey = actionKey;
            return builder;
        }

        private static final class Builder {
            private final String symbol;
            private String materialKey = "STONE";
            private String nameKey = "";
            private String fallbackName = "";
            private final ArrayList<String> loreKeys = new ArrayList<>();
            private final ArrayList<String> fallbackLore = new ArrayList<>();
            private String actionKey = "";

            private Builder(String symbol) {
                this.symbol = symbol == null ? "" : symbol;
            }

            private void material(String value) {
                materialKey = value == null || value.isBlank() ? "STONE" : value;
            }

            private void nameKey(String value) {
                nameKey = value == null ? "" : value;
            }

            private void fallbackName(String value) {
                fallbackName = value == null ? "" : value;
            }

            private void loreKey(String value) {
                loreKeys.add(value == null ? "" : value);
            }

            private void fallbackLore(String value) {
                fallbackLore.add(value == null ? "" : value);
            }

            private void actionKey(String value) {
                actionKey = value == null ? "" : value;
            }

            private MenuItem build() {
                return new MenuItem(symbol, materialKey, nameKey, fallbackName, loreKeys, fallbackLore, actionKey);
            }
        }
    }
}

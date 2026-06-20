package kr.lunaf.cloudislands.paper.message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

final class LocaleMessageCatalog {
    private LocaleMessageCatalog() {
    }

    static Map<String, Map<String, String>> bundledTranslations() {
        Map<String, Map<String, String>> locales = new HashMap<>();
        loadBundledLocale(locales, "ko_kr");
        loadBundledLocale(locales, "en_us");
        return locales;
    }

    private static void loadBundledLocale(Map<String, Map<String, String>> locales, String locale) {
        String resource = "config-v2/ui/messages/" + locale + ".yml";
        try (java.io.InputStream input = LocaleMessageCatalog.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return;
            }
            Map<String, String> values = parseFlatYaml(input);
            locales.put(PlayerIslandProfile.normalizeLocale(locale), values);
        } catch (IOException ignored) {
            // Built-in locale files are optional at runtime; default translations remain available.
        }
    }

    static Map<String, String> parseFlatYaml(java.io.InputStream input) throws IOException {
        Map<String, String> values = new HashMap<>();
        Map<Integer, String> parents = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                int indent = leadingSpaces(line);
                String trimmed = line.trim();
                int separator = trimmed.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                String parent = parentFor(parents, indent);
                String path = parent.isBlank() ? key : parent + "." + key;
                if (value.isBlank()) {
                    parents.entrySet().removeIf(entry -> entry.getKey() >= indent);
                    parents.put(indent, path);
                    continue;
                }
                values.put(normalize(path), unquote(value));
            }
        }
        return values;
    }

    private static String parentFor(Map<Integer, String> parents, int indent) {
        int best = -1;
        for (Integer candidate : parents.keySet()) {
            if (candidate < indent && candidate > best) {
                best = candidate;
            }
        }
        return best < 0 ? "" : parents.get(best);
    }

    private static int leadingSpaces(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

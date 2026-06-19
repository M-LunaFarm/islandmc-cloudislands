package kr.lunaf.cloudislands.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

public final class VelocityConfigLoader {
    private static final List<String> DEFAULT_ALIASES = List.of("is", "island", "섬");

    private VelocityConfigLoader() {
    }

    public static VelocityConfig load(Path dataDirectory, Logger logger) {
        Map<String, String> values = new HashMap<>();
        List<String> aliases = new ArrayList<>();
        Path configPath = dataDirectory.resolve("config.yaml");
        try {
            if (Files.notExists(configPath)) {
                Files.createDirectories(dataDirectory);
                try (InputStream defaults = VelocityConfigLoader.class.getClassLoader().getResourceAsStream("config.yaml")) {
                    if (defaults != null) {
                        Files.copy(defaults, configPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (Files.exists(configPath)) {
                readConfig(configPath, values, aliases);
            }
        } catch (IOException exception) {
            logger.warn("Failed to load CloudIslands Velocity config, using defaults", exception);
        }
        return new VelocityConfig(
            values.getOrDefault("plugin.language", "ko_kr"),
            bool(values.get("plugin.debug"), false),
            value(values, "setup.core-api.base-url", value(values, "setup-core-api.base-url", value(values, "core-api.base-url", "https://core-api.internal:8443"))),
            value(values, "setup.core-api.auth-token", value(values, "setup-core-api.auth-token", value(values, "core-api.auth-token", ""))),
            value(values, "setup.core-api.admin-token", value(values, "setup-core-api.admin-token", value(values, "core-api.admin-token", ""))),
            positiveInteger(values.get("setup.core-api.timeout-ms"), positiveInteger(values.get("setup-core-api.timeout-ms"), integer(values.get("core-api.timeout-ms"), 3000))),
            values.getOrDefault("routing.fallback-on-failure", values.getOrDefault("routing.default-lobby", "Lobby")),
            integer(values.get("routing.wait-for-activation-timeout-seconds"), 20),
            values.getOrDefault("routing.island-pool", "island"),
            integer(values.get("routing.route-ticket-ttl-seconds"), 30),
            bool(values.get("routing.hide-node-names"), true),
            bool(values.get("messages.use-actionbar"), true),
            bool(values.get("messages.use-bossbar-loading"), true),
            bool(values.get("security.require-modern-forwarding"), true),
            values.getOrDefault("security.forwarding-secret", ""),
            bool(values.get("security.block-cloudislands-plugin-messages"), true),
            bool(values.get("health.enabled"), false),
            values.getOrDefault("health.bind-host", "127.0.0.1"),
            integer(values.get("health.port"), 8788),
            bool(values.get("migration.superiorskyblock2-enabled"), bool(values.get("migration.enabled"), true)),
            messageValues(values),
            aliases.isEmpty() ? DEFAULT_ALIASES : List.copyOf(aliases)
        );
    }

    private static void readConfig(Path configPath, Map<String, String> values, List<String> aliases) throws IOException {
        Map<Integer, String> sections = new HashMap<>();
        boolean readingAliases = false;
        for (String rawLine : Files.readAllLines(configPath)) {
            String line = rawLine.strip();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (readingAliases && line.startsWith("-")) {
                String alias = unquote(line.substring(1).strip());
                if (!alias.isBlank()) {
                    aliases.add(alias);
                }
                continue;
            }
            readingAliases = false;
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            int indent = leadingSpaces(rawLine);
            String parent = parentSection(sections, indent);
            String key = line.substring(0, colon).strip();
            String rawValue = line.substring(colon + 1).strip();
            String value = unquote(rawValue);
            String fullKey = parent.isBlank() ? key : parent + "." + key;
            if (fullKey.equals("commands.aliases") && value.isBlank()) {
                readingAliases = true;
                continue;
            }
            if (rawValue.isBlank()) {
                sections.entrySet().removeIf(entry -> entry.getKey() >= indent);
                sections.put(indent, fullKey);
                continue;
            }
            values.put(fullKey, resolveEnv(value));
        }
    }

    private static int leadingSpaces(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    private static String parentSection(Map<Integer, String> sections, int indent) {
        String parent = "";
        int parentIndent = -1;
        for (Map.Entry<Integer, String> entry : sections.entrySet()) {
            if (entry.getKey() < indent && entry.getKey() > parentIndent) {
                parent = entry.getValue();
                parentIndent = entry.getKey();
            }
        }
        return parent;
    }

    private static Map<String, String> messageValues(Map<String, String> values) {
        Map<String, String> messages = new HashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("messages.") && !key.equals("messages.use-actionbar") && !key.equals("messages.use-bossbar-loading")) {
                messages.put(key.substring("messages.".length()), entry.getValue());
            }
        }
        return Map.copyOf(messages);
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String resolveEnv(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int positiveInteger(String value, int fallback) {
        int parsed = integer(value, fallback);
        return parsed <= 0 ? fallback : parsed;
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("켜기") || normalized.equals("허용") || normalized.equals("활성")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("끄기") || normalized.equals("거부") || normalized.equals("비활성")) {
            return false;
        }
        return fallback;
    }
}

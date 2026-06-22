package kr.lunaf.cloudislands.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import org.slf4j.Logger;

public final class VelocityConfigLoader {
    private static final List<String> DEFAULT_ALIASES = List.of("is", "island", "섬");

    private VelocityConfigLoader() {
    }

    public static VelocityConfig load(Path dataDirectory, Logger logger) {
        Map<String, String> values = new HashMap<>();
        List<String> aliases = new ArrayList<>();
        try {
            Path configRoot = dataDirectory.resolve("config-v2");
            saveBundledConfigV2Defaults(configRoot);
            for (Path configPath : dataConfigV2Files(configRoot)) {
                readConfig(configPath, values, aliases);
            }
        } catch (IOException | RuntimeException exception) {
            logger.warn("Failed to load CloudIslands Velocity config, using defaults", exception);
        }
        return new VelocityConfig(
            values.getOrDefault("language", "ko_kr"),
            bool(values.get("debug"), false),
            value(values, "base-url", "https://core-api.internal:8443"),
            value(values, "core-api.auth-token", ""),
            value(values, "core-api.admin-token", ""),
            durationMillis(values.get("timeout.request"), 3000),
            values.getOrDefault("failure.fallback-server", values.getOrDefault("default-lobby", "Lobby")),
            durationSeconds(values.get("ticket.wait-timeout"), 20),
            values.getOrDefault("island-pool", "island"),
            durationSeconds(values.get("ticket.ttl"), 30),
            bool(values.get("failure.hide-backend-node-names"), true),
            bool(values.get("messages.use-actionbar"), true),
            bool(values.get("messages.use-bossbar-loading"), true),
            bool(values.get("forwarding.require-modern"), true),
            values.getOrDefault("forwarding.secret", ""),
            bool(values.get("plugin-message.block-cloudislands-channel"), true),
            bool(values.get("enabled"), false),
            values.getOrDefault("bind-host", "127.0.0.1"),
            integer(values.get("port"), 8788),
            bool(values.get("migration.superiorskyblock2-enabled"), true),
            messageValues(values),
            aliases.isEmpty() ? DEFAULT_ALIASES : List.copyOf(aliases)
        );
    }

    private static void saveBundledConfigV2Defaults(Path configRoot) throws IOException {
        Files.createDirectories(configRoot);
        for (String file : configV2ResourceNames()) {
            Path target = configRoot.resolve(file);
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = VelocityConfigLoader.class.getClassLoader().getResourceAsStream("config-v2/" + file)) {
                if (input != null) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static List<Path> dataConfigV2Files(Path configRoot) throws IOException {
        if (Files.notExists(configRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(configRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".yml"))
                .sorted()
                .toList();
        }
    }

    private static List<String> configV2ResourceNames() throws IOException {
        URL root = VelocityConfigLoader.class.getClassLoader().getResource("config-v2");
        if (root == null) {
            return List.of();
        }
        if (root.getProtocol().equals("file")) {
            try {
                Path rootPath = Paths.get(root.toURI());
                try (var paths = Files.walk(rootPath)) {
                    return paths
                        .filter(Files::isRegularFile)
                        .map(rootPath::relativize)
                        .map(path -> path.toString().replace('\\', '/'))
                        .filter(name -> name.endsWith(".yml"))
                        .sorted()
                        .toList();
                }
            } catch (URISyntaxException exception) {
                throw new IOException("Invalid Velocity config-v2 resource path", exception);
            }
        }
        if (root.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            String prefix = connection.getEntryName() + "/";
            try (JarFile jar = connection.getJarFile()) {
                return jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(prefix))
                    .filter(name -> name.endsWith(".yml"))
                    .map(name -> name.substring(prefix.length()))
                    .sorted()
                    .toList();
            }
        }
        return List.of();
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
            if ((fullKey.equals("commands.aliases") || fullKey.equals("root.aliases")) && value.isBlank()) {
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
            } else if (key.startsWith("route.") || key.startsWith("errors.")) {
                messages.put(key.replace('.', '-'), entry.getValue());
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
            String expression = trimmed.substring(2, trimmed.length() - 1);
            if (expression.startsWith("file:")) {
                try {
                    Path path = Path.of(expression.substring("file:".length()));
                    return Files.exists(path) ? Files.readString(path).trim() : "";
                } catch (IOException exception) {
                    return "";
                }
            }
            return System.getenv().getOrDefault(expression, "");
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

    private static int durationSeconds(String value, int fallback) {
        return Math.max(1, durationMillis(value, fallback * 1000) / 1000);
    }

    private static int durationMillis(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Math.max(1, Integer.parseInt(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Math.max(1, Integer.parseInt(normalized.substring(0, normalized.length() - 1)) * 1000);
            }
            return positiveInteger(normalized, fallback);
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

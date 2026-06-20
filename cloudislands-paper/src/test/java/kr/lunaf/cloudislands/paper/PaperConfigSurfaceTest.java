package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.config.ConfigSurfacePolicy;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperConfigSurfaceTest {
    @Test
    void defaultConfigKeepsGoalPaperAgentSurface() throws Exception {
        try (InputStream input = PaperConfigSurfaceTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            for (String key : ConfigSurfacePolicy.paperRequiredKeys()) {
                assertTrue(containsPath(config, key), key);
            }
            assertTrue(config.contains("id: \"island-1\""));
            assertTrue(config.contains("role: \"ISLAND_NODE\""));
            assertTrue(config.contains("pool: \"island\""));
            assertTrue(config.contains("shard-world-prefix: \"ci_shard_\""));
            assertTrue(config.contains("cell-size: 1024"));
            assertTrue(config.contains("default-island-size: 300"));
        }
    }

    @Test
    void configV2LocaleFilesKeepTheSameKeysAndNoBlankTranslations() throws Exception {
        Path messages = Path.of("src/main/resources/config-v2/ui/messages");
        Map<String, String> korean = flattenYaml(Files.readAllLines(messages.resolve("ko_kr.yml"), StandardCharsets.UTF_8));
        Map<String, String> english = flattenYaml(Files.readAllLines(messages.resolve("en_us.yml"), StandardCharsets.UTF_8));
        String rootConfig = Files.readString(Path.of("src/main/resources/config-v2/config.yml"), StandardCharsets.UTF_8);

        assertTrue(rootConfig.contains("locale: ui/messages/ko_kr.yml"), "config-v2 root must point at a locale file");
        assertFalse(korean.isEmpty(), "ko_kr locale must define message keys");
        assertEquals(korean.keySet(), english.keySet(), "locale files must expose the same message keys");
        assertTrue(korean.values().stream().noneMatch(String::isBlank), "ko_kr locale values must not be blank");
        assertTrue(english.values().stream().noneMatch(String::isBlank), "en_us locale values must not be blank");
        assertTrue(korean.containsKey("errors.PERMISSION_VERSION_CONFLICT"), "permission conflict message must be localized");
        assertTrue(english.containsKey("errors.PERMISSION_VERSION_CONFLICT"), "permission conflict message must be localized");
    }

    private boolean containsPath(String config, String path) {
        String[] parts = path.split("\\.");
        return config.contains(parts[parts.length - 1] + ":");
    }

    private Map<String, String> flattenYaml(java.util.List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        ArrayDeque<Integer> indents = new ArrayDeque<>();
        for (String rawLine : lines) {
            String line = rawLine.replace("\t", "    ");
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            int indent = countIndent(line);
            String trimmed = line.trim();
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            while (!indents.isEmpty() && indents.peekLast() >= indent) {
                indents.removeLast();
                path.removeLast();
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (value.isEmpty()) {
                path.addLast(key);
                indents.addLast(indent);
                continue;
            }
            String fullKey = String.join(".", path);
            values.put(fullKey.isBlank() ? key : fullKey + "." + key, unquote(value));
        }
        return values;
    }

    private int countIndent(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

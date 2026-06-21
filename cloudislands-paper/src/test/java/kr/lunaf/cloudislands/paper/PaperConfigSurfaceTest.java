package kr.lunaf.cloudislands.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperConfigSurfaceTest {
    @Test
    void configV2DefaultsKeepGoalPaperAgentSurface() throws Exception {
        String runtime = Files.readString(Path.of("src/main/resources/config-v2/runtime.yml"), StandardCharsets.UTF_8);
        String integrations = Files.readString(Path.of("src/main/resources/config-v2/integrations.yml"), StandardCharsets.UTF_8);
        String security = Files.readString(Path.of("src/main/resources/config-v2/security.yml"), StandardCharsets.UTF_8);
        String gameplay = Files.readString(Path.of("src/main/resources/config-v2/gameplay.yml"), StandardCharsets.UTF_8);
        String features = Files.readString(Path.of("src/main/resources/config-v2/features.yml"), StandardCharsets.UTF_8);

        assertTrue(runtime.contains("id: island-node-01"));
        assertTrue(runtime.contains("role: ISLAND_NODE"));
        assertTrue(runtime.contains("pool: island"));
        assertTrue(runtime.contains("capacity:"), "Paper config-v2 runtime must define capacity values");
        assertTrue(integrations.contains("core-api:"), "Paper config-v2 integrations must define Core API values");
        assertTrue(integrations.contains("redis:"), "Paper config-v2 integrations must define Redis values");
        assertTrue(gameplay.contains("shard-world-prefix: \"ci_shard_\""));
        assertTrue(gameplay.contains("shard-count: 16"));
        assertTrue(gameplay.contains("cell-size: 1024"));
        assertTrue(gameplay.contains("default-island-size: 300"));
        assertTrue(gameplay.contains("periodic-save: 10m"));
        assertTrue(security.contains("forwarding:"), "Paper config-v2 security must define forwarding values");
        assertFalse(features.contains("cloudislands-satis:"), "Paper config-v2 must not duplicate Satis under addons.cloudislands-satis");
        assertTrue(features.contains("cloudislands:"), "Paper config-v2 must keep CloudIslands feature gates");
        assertTrue(features.contains("satis:"), "Paper config-v2 must keep one canonical Satis root");
        assertTrue(features.contains("route-events: true"), "Paper config-v2 must keep canonical Satis feature gates");
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

    @Test
    void paperBootstrapDelegatesRuntimeConfigPathsToSnapshotLoader() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"), StandardCharsets.UTF_8);
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"), StandardCharsets.UTF_8);
        String commands = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"), StandardCharsets.UTF_8);
        String api = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"), StandardCharsets.UTF_8);
        String loader = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfigLoader.java"), StandardCharsets.UTF_8);
        String snapshot = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfig.java"), StandardCharsets.UTF_8);
        String addonStore = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperAddonConfigStore.java"), StandardCharsets.UTF_8);
        String addonFile = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperAddonConfigFile.java"), StandardCharsets.UTF_8);
        String agent = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperAgent.java"), StandardCharsets.UTF_8);
        String admin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"), StandardCharsets.UTF_8);

        assertTrue(bootstrap.contains("PaperRuntimeConfigLoader.load"), "Paper bootstrap must create a runtime config snapshot");
        assertTrue(snapshot.contains("record PaperRuntimeConfig"), "Paper runtime config must be immutable snapshot data");
        assertTrue(snapshot.contains("ConfigSnapshot sourceConfig"), "Paper runtime config must retain the effective Config v2 snapshot");
        assertTrue(snapshot.contains("record Node"), "Paper runtime config must expose typed node settings");
        assertTrue(snapshot.contains("record CoreApi"), "Paper runtime config must expose typed Core API settings");
        assertTrue(snapshot.contains("record Redis"), "Paper runtime config must expose typed Redis settings");
        assertTrue(snapshot.contains("record Security"), "Paper runtime config must expose typed security settings");
        assertTrue(snapshot.contains("record Routing"), "Paper runtime config must expose typed routing settings");
        assertTrue(snapshot.contains("record Messages"), "Paper runtime config must expose typed message settings");
        assertTrue(snapshot.contains("record Storage"), "Paper runtime config must expose typed storage settings");
        assertTrue(snapshot.contains("record Migration"), "Paper runtime config must expose typed migration settings");
        assertTrue(snapshot.contains("return new Migration(false)"), "old plugin migration must be opt-in only");
        assertTrue(snapshot.contains("record Worker"), "Paper runtime config must expose typed island worker settings");
        assertTrue(loader.contains("node.id"), "Paper runtime config loader must own node paths");
        assertTrue(loader.contains("redis.uri"), "Paper runtime config loader must own Redis paths");
        assertTrue(loader.contains("routing.wait-for-activation-timeout-seconds"), "Paper runtime config loader must own routing paths");
        assertTrue(loader.contains("routing.hide-node-names"), "Paper runtime config loader must own topology exposure paths");
        assertTrue(loader.contains("messages.translations"), "Paper runtime config loader must own message paths");
        assertTrue(loader.contains("setup.storage.type"), "Paper runtime config loader must own storage paths");
        assertTrue(loader.contains("migration.superiorskyblock2.enabled"), "Paper runtime config loader must own migration paths");
        assertTrue(loader.contains("booleanValue(config, \"migration.superiorskyblock2.enabled\", false)"), "missing migration.yml must not enable old plugin migration");
        assertTrue(loader.contains("heartbeat.interval-ticks"), "Paper runtime config loader must own heartbeat paths");
        assertTrue(loader.contains("health.enabled"), "Paper runtime config loader must own health paths");
        assertTrue(loader.contains("island-node.shard-count"), "Paper runtime config loader must own island worker paths");
        assertTrue(loader.contains("paperConfigV2Sources"), "Paper runtime config loader must discover Config v2 sources");
        assertTrue(loader.contains("saveBundledConfigV2Defaults"), "Paper runtime config loader must save Config v2 defaults into the data folder");
        assertTrue(loader.contains("configV2ResourceNames"), "Paper runtime config loader must discover Config v2 files dynamically");
        assertTrue(loader.contains("loadV2"), "Paper runtime config loader must expose a Config v2 runtime path");
        assertFalse(loader.contains("PAPER_CONFIG_V2_FILES"), "Paper runtime config loader must not rely on a hardcoded Config v2 file list");
        assertFalse(bootstrap.contains("saveDefaultConfig()"), "Paper bootstrap must not create legacy config.yml as a runtime input");
        assertEquals(0, countOccurrences(bootstrap, "plugin.getConfig()"), "Paper bootstrap must not read Bukkit config directly");
        assertFalse(bootstrap.contains("getString(\"node.id\""), "node identity path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("getString(\"redis.uri\""), "Redis path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("getInt(\"routing.wait-for-activation-timeout-seconds\""), "routing wait path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("routing.hide-node-names"), "topology exposure path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("TranslationManager.fromConfig"), "message paths must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("PaperStorageFactory.create(plugin, plugin.getConfig())"), "storage paths must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("getLong(\"heartbeat.interval-ticks\""), "heartbeat path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("getBoolean(\"health.enabled\""), "health path must live in PaperRuntimeConfigLoader");
        assertFalse(bootstrap.contains("getInt(\"island-node.shard-count\""), "island worker paths must live in PaperRuntimeConfigLoader");
        assertTrue(plugin.contains("PaperRuntimeConfig runtimeConfig"), "Paper plugin must retain the active runtime config snapshot");
        assertFalse(plugin.contains("boolean configBoolean("), "Paper plugin helpers must not parse runtime booleans from Bukkit config");
        assertFalse(commands.contains("plugin.getConfig().getString(\"node.id\""), "commands must use the runtime snapshot for node identity");
        assertFalse(admin.contains("agent.getConfig()"), "admin commands must not read Bukkit config through the agent");
        assertFalse(admin.contains("agent.plugin().reloadConfig()"), "admin commands must refresh the runtime config snapshot instead of only reloading Bukkit config");
        assertTrue(admin.contains("plugin.reloadRuntimeConfig()"), "admin config reload must refresh the active runtime config snapshot");
        assertFalse(api.contains("new StatusService(agent)"), "status API must receive the runtime config snapshot");
        assertFalse(api.contains("boolean configBoolean("), "Paper API services must not keep duplicate runtime boolean parsers");
        assertFalse(api.contains("config.getString(\"node.id\""), "status API must use the runtime snapshot for node identity");
        assertFalse(api.contains("plugin.getConfig()"), "Paper API services must use runtime config snapshots or dedicated config adapters");
        assertTrue(api.contains("PaperAddonConfigFile.fromPlugin(plugin)"), "Paper API addon service must use the dedicated addon config adapter");
        assertFalse(addonStore.contains("plugin.getConfig()"), "Paper addon config store must read addon settings from a snapshot");
        assertTrue(addonStore.contains("PaperAddonConfigSnapshot"), "Paper addon config store must keep addon settings snapshot-backed");
        assertFalse(addonFile.contains("plugin.getConfig()"), "Paper addon config adapter must not read legacy Bukkit config");
        assertTrue(addonFile.contains("config-v2"), "Paper addon config adapter must use the Config v2 addon file");
        assertTrue(addonFile.contains("addons.yml"), "Paper addon config adapter must persist addon settings under config-v2/addons.yml");
        assertFalse(agent.contains("getConfig()"), "Paper agent must not expose a Bukkit config accessor");
        assertTrue(plugin.contains("reloadRuntimeConfig()"), "Paper plugin must expose one runtime snapshot reload boundary");
        assertFalse(plugin.contains("reloadConfig()"), "runtime reload must not reload legacy Bukkit config");
        assertTrue(plugin.contains("PaperRuntimeConfigLoader.load(this, this::resolveEnv)"), "runtime reload must use the same Config v2 loader as bootstrap");
        assertTrue(plugin.contains("ConfigSecretResolver.resolve"), "Paper runtime config resolver must handle Config v2 env/file secret references");
        assertTrue(plugin.contains("${env:"), "Paper runtime config resolver must recognize Config v2 env references");
        assertTrue(plugin.contains("${file:"), "Paper runtime config resolver must recognize Config v2 file secret references");
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int offset = 0;
        while (true) {
            int next = text.indexOf(pattern, offset);
            if (next < 0) {
                return count;
            }
            count++;
            offset = next + pattern.length();
        }
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

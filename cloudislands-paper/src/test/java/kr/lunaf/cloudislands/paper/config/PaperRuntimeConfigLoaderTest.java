package kr.lunaf.cloudislands.paper.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PaperRuntimeConfigLoaderTest {
    @Test
    void loaderMapsConfigV2RuntimeSourcesIntoTypedPaperConfigPaths() throws Exception {
        Path root = Path.of("");
        String loader = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfigLoader.java"), StandardCharsets.UTF_8);
        String runtime = Files.readString(root.resolve("src/main/resources/config-v2/runtime.yml"), StandardCharsets.UTF_8);
        String integrations = Files.readString(root.resolve("src/main/resources/config-v2/integrations.yml"), StandardCharsets.UTF_8);
        String security = Files.readString(root.resolve("src/main/resources/config-v2/security.yml"), StandardCharsets.UTF_8);
        String features = Files.readString(root.resolve("src/main/resources/config-v2/features.yml"), StandardCharsets.UTF_8);
        String gameplay = Files.readString(root.resolve("src/main/resources/config-v2/gameplay.yml"), StandardCharsets.UTF_8);

        assertTrue(loader.contains("public static PaperRuntimeConfig load(JavaPlugin plugin"), "Paper runtime loader must accept the plugin as the config composition root");
        assertFalse(loader.contains("public static PaperRuntimeConfig load(FileConfiguration"), "Paper runtime loader must not expose a legacy Bukkit config entrypoint");
        assertTrue(loader.contains("paperConfigV2Sources"), "Paper runtime loader must discover bundled or data-folder config-v2 files");
        assertTrue(loader.contains("saveBundledConfigV2Defaults"), "Paper runtime loader must copy bundled config-v2 defaults without legacy config.yml");
        assertTrue(loader.contains("configV2ResourceNames"), "Paper runtime loader must compose config-v2 source names dynamically");
        assertTrue(loader.contains("dataConfigV2ResourceNames"), "Paper runtime loader must scan data-folder config-v2 files dynamically");
        assertTrue(loader.contains("bundledConfigV2ResourceNames"), "Paper runtime loader must scan bundled config-v2 resources dynamically");
        assertTrue(loader.contains("JarFile"), "Paper runtime loader must scan packaged plugin jars for config-v2 resources");
        assertTrue(loader.contains("Files.walk"), "Paper runtime loader must use directory discovery instead of hardcoded config-v2 file lists");
        assertTrue(loader.contains("Files.copy(input, target)"), "Paper runtime loader must materialize missing bundled config-v2 defaults");
        assertFalse(loader.contains("plugin.getConfig()"), "Paper runtime loader must not read legacy Bukkit config");
        assertTrue(loader.contains("paper/config-v2/empty"), "Paper runtime loader must use an empty Config v2 source when no files are present");
        assertFalse(loader.contains("copyScalars(legacy"), "Config v2 runtime mapping must not merge legacy config values into the authoritative snapshot");
        assertTrue(loader.contains("validateV2Sources"), "Paper runtime loader must validate raw config-v2 sources before mapping");
        assertTrue(loader.contains("ConfigV2Validator.validateYaml"), "Paper runtime loader must run schema and secret validation on config-v2 sources");
        assertTrue(loader.contains("ConfigV2Validator.validateMenuYaml"), "Paper runtime loader must validate config-v2 menu action schemas");
        assertTrue(loader.contains("GuiActionSchema.registeredActionIds()"), "Paper runtime menu validation must use the runtime GUI action registry");
        assertFalse(loader.contains("PAPER_CONFIG_V2_FILES"), "Paper runtime loader must not keep a hardcoded config-v2 file list");
        assertTrue(loader.contains("knownPaperConfigV2Source"), "Paper runtime loader must maintain an explicit consumed-source policy");
        assertTrue(loader.contains("configV2RelativeName"), "Paper runtime loader must validate config-v2 sources by relative resource path");
        assertTrue(loader.contains("UNSUPPORTED_CONFIG_V2_SOURCE"), "Paper runtime loader must reject unknown config-v2 yaml instead of silently ignoring it");
        assertTrue(loader.contains("\"ui/theme.yml\""), "Paper runtime loader must allow the declared theme config boundary");
        assertTrue(loader.contains("\"addons.yml\""), "Paper runtime loader must allow addon config-v2 as the extension boundary");
        assertTrue(loader.contains("mapMessagesV2"), "Paper runtime loader must map active locale messages into the runtime snapshot");
        assertTrue(loader.contains("localeFromMessageSource"), "Paper runtime loader must only apply the active locale message file");
        assertTrue(loader.contains("target.set(\"messages.translations.\" + key"), "Config v2 locale messages must feed runtime translations");
        assertTrue(loader.contains("section.getKeys(true)"), "runtime message snapshots must support nested locale yaml keys");
        assertTrue(loader.contains("requireValidSnapshot"), "Paper runtime loader must reject invalid effective runtime snapshots");
        assertTrue(loader.contains("ConfigV2Loader.load"), "Paper runtime loader must create a ConfigSnapshot from mapped config-v2 sources");
        assertTrue(loader.contains("\"node.id\", \"node.id\""), "Config v2 node id must feed runtime node identity");
        assertTrue(loader.contains("\"node.reject-default-identity\", \"node.reject-default-identity\""), "Config v2 node identity guard must feed runtime startup policy");
        assertTrue(loader.contains("\"capacity.max-active-islands\", \"node.max-active-islands\""), "Config v2 capacity must feed runtime node capacity");
        assertTrue(loader.contains("\"core-api.base-url\", \"setup.core-api.base-url\""), "Config v2 Core API endpoint must feed setup-owned runtime Core API config");
        assertTrue(loader.contains("\"redis.uri\", \"redis.uri\""), "Config v2 Redis URI must feed runtime Redis config");
        assertTrue(loader.contains("\"storage.type\", \"setup.storage.type\""), "Config v2 storage type must feed setup-owned runtime storage config");
        assertTrue(loader.contains("\"storage.bearer-token\", \"setup.storage.auth-token\""), "Config v2 storage bearer token must feed setup-owned runtime storage auth config");
        assertFalse(loader.contains("String legacyPath"), "Config v2 setup values must not fall back to legacy runtime paths");
        assertFalse(loader.contains("string(config, legacyPath"), "Config v2 setup values must not read legacy runtime paths");
        assertTrue(loader.contains("\"forwarding.secret\", \"security.forwarding-secret\""), "Config v2 forwarding secret must feed runtime security config");
        assertTrue(loader.contains("\"forwarding.required\", \"security.require-velocity-forwarding\""), "Config v2 forwarding requirement must feed runtime security config");
        assertTrue(loader.contains("\"route-session.enforce\", \"security.enforce-route-session\""), "Config v2 route session enforcement must feed runtime security config");
        assertTrue(loader.contains("\"proxy-source-allowlist.required\", \"security.require-proxy-source-allowlist\""), "Config v2 proxy allowlist requirement must feed runtime security config");
        assertTrue(loader.contains("\"cloudislands.gui\", \"paper-gui.enabled\""), "Config v2 GUI feature flag must feed runtime GUI config");
        assertTrue(loader.contains("\"generator.default-profile\", \"generators.default-key\""), "Config v2 generator profile must feed runtime generator config");
        assertTrue(loader.contains("durationTicks"), "Config v2 duration values must be converted before entering the runtime snapshot");
        assertTrue(runtime.contains("capacity:"), "Bundled runtime config-v2 must define capacity values");
        assertTrue(runtime.contains("reject-default-identity: true"), "Bundled runtime config-v2 must make the production identity guard explicit");
        assertTrue(integrations.contains("core-api:"), "Bundled integrations config-v2 must define Core API values");
        assertTrue(security.contains("forwarding:"), "Bundled security config-v2 must define forwarding values");
        assertTrue(security.contains("route-session:"), "Bundled security config-v2 must define route-session values");
        assertTrue(features.contains("cloudislands:"), "Bundled features config-v2 must define CloudIslands feature flags");
        assertTrue(gameplay.contains("generator:"), "Bundled gameplay config-v2 must define generator values");
        try (Stream<Path> menuFiles = Files.walk(root.resolve("src/main/resources/config-v2/ui/menus"))) {
            assertTrue(menuFiles.filter(path -> path.toString().endsWith(".yml")).count() >= 20, "Bundled config-v2 must expose the expanded GUI menu surface");
        }
    }
}

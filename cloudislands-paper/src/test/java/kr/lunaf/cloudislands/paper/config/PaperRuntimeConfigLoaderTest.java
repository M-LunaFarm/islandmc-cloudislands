package kr.lunaf.cloudislands.paper.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(loader.contains("paperConfigV2Sources"), "Paper runtime loader must discover bundled or data-folder config-v2 files");
        assertTrue(loader.contains("return load(plugin.getConfig(), envResolver);"), "Paper runtime loader may read legacy Bukkit config only when config-v2 sources are absent");
        assertFalse(loader.contains("FileConfiguration legacyConfig = plugin.getConfig()"), "Paper runtime loader must not read legacy config before deciding config-v2 is absent");
        assertFalse(loader.contains("copyScalars(legacy"), "Config v2 runtime mapping must not merge legacy config values into the authoritative snapshot");
        assertTrue(loader.contains("validateV2Sources"), "Paper runtime loader must validate raw config-v2 sources before mapping");
        assertTrue(loader.contains("ConfigV2Validator.validateYaml"), "Paper runtime loader must run schema and secret validation on config-v2 sources");
        assertTrue(loader.contains("ConfigV2Validator.validateMenuYaml"), "Paper runtime loader must validate config-v2 menu action schemas");
        assertTrue(loader.contains("GuiActionSchema.registeredActionIds()"), "Paper runtime menu validation must use the runtime GUI action registry");
        assertTrue(loader.contains("\"ui/menus/main.yml\""), "Paper runtime loader must discover bundled config-v2 menu files");
        assertTrue(loader.contains("\"ui/menus/bank.yml\""), "Paper runtime loader must discover expanded config-v2 menu files");
        assertTrue(loader.contains("\"ui/menus/warps.yml\""), "Paper runtime loader must discover expanded config-v2 menu files");
        assertTrue(loader.contains("requireValidSnapshot"), "Paper runtime loader must reject invalid effective runtime snapshots");
        assertTrue(loader.contains("ConfigV2Loader.load"), "Paper runtime loader must create a ConfigSnapshot from mapped config-v2 sources");
        assertTrue(loader.contains("\"node.id\", \"node.id\""), "Config v2 node id must feed runtime node identity");
        assertTrue(loader.contains("\"node.reject-default-identity\", \"node.reject-default-identity\""), "Config v2 node identity guard must feed runtime startup policy");
        assertTrue(loader.contains("\"capacity.max-active-islands\", \"node.max-active-islands\""), "Config v2 capacity must feed runtime node capacity");
        assertTrue(loader.contains("\"core-api.base-url\", \"core-api.base-url\""), "Config v2 Core API endpoint must feed runtime Core API config");
        assertTrue(loader.contains("\"redis.uri\", \"redis.uri\""), "Config v2 Redis URI must feed runtime Redis config");
        assertTrue(loader.contains("\"storage.bearer-token\", \"storage.auth-token\""), "Config v2 storage bearer token must feed runtime storage auth config");
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
    }
}

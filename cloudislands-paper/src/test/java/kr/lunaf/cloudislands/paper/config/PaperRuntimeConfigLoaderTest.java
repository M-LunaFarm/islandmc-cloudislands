package kr.lunaf.cloudislands.paper.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(loader.contains("ConfigV2Loader.load"), "Paper runtime loader must create a ConfigSnapshot from mapped config-v2 sources");
        assertTrue(loader.contains("\"node.id\", \"node.id\""), "Config v2 node id must feed runtime node identity");
        assertTrue(loader.contains("\"capacity.max-active-islands\", \"node.max-active-islands\""), "Config v2 capacity must feed runtime node capacity");
        assertTrue(loader.contains("\"core-api.base-url\", \"core-api.base-url\""), "Config v2 Core API endpoint must feed runtime Core API config");
        assertTrue(loader.contains("\"redis.uri\", \"redis.uri\""), "Config v2 Redis URI must feed runtime Redis config");
        assertTrue(loader.contains("\"storage.bearer-token\", \"storage.auth-token\""), "Config v2 storage bearer token must feed runtime storage auth config");
        assertTrue(loader.contains("\"forwarding.secret\", \"security.forwarding-secret\""), "Config v2 forwarding secret must feed runtime security config");
        assertTrue(loader.contains("\"cloudislands.gui\", \"paper-gui.enabled\""), "Config v2 GUI feature flag must feed runtime GUI config");
        assertTrue(loader.contains("\"generator.default-profile\", \"generators.default-key\""), "Config v2 generator profile must feed runtime generator config");
        assertTrue(loader.contains("durationTicks"), "Config v2 duration values must be converted before entering the runtime snapshot");
        assertTrue(runtime.contains("capacity:"), "Bundled runtime config-v2 must define capacity values");
        assertTrue(integrations.contains("core-api:"), "Bundled integrations config-v2 must define Core API values");
        assertTrue(security.contains("forwarding:"), "Bundled security config-v2 must define forwarding values");
        assertTrue(features.contains("cloudislands:"), "Bundled features config-v2 must define CloudIslands feature flags");
        assertTrue(gameplay.contains("generator:"), "Bundled gameplay config-v2 must define generator values");
    }
}

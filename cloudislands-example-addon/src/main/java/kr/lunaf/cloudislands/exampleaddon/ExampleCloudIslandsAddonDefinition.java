package kr.lunaf.cloudislands.exampleaddon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.model.AddonMenuButtonSnapshot;
import kr.lunaf.cloudislands.api.model.AddonPlaceholderSnapshot;
import kr.lunaf.cloudislands.api.model.BlockValueSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public final class ExampleCloudIslandsAddonDefinition implements CloudIslandsAddon {
    public static final String ADDON_ID = "cloudislands-example-addon";
    public static final String DISPLAY_NAME = "CloudIslands Example Addon";

    private final String version;

    public ExampleCloudIslandsAddonDefinition() {
        this("dev");
    }

    public ExampleCloudIslandsAddonDefinition(String version) {
        this.version = version == null || version.isBlank() ? "dev" : version;
    }

    @Override
    public String addonId() {
        return ADDON_ID;
    }

    @Override
    public String addonDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String addonVersion() {
        return version;
    }

    @Override
    public Map<String, Boolean> addonFeatures() {
        return Map.of(
            "lifecycle", true,
            "route-events", true,
            "addon-state", true,
            "custom-missions", true,
            "placeholders", true,
            "custom-menu-buttons", true,
            "custom-block-values", true
        );
    }

    @Override
    public Map<String, String> addonMetadata() {
        return Map.of(
            "cloudislands-api-requested-version", CloudIslandsApiContract.RUNTIME_API_VERSION,
            "example-addon-role", "developer-kit-reference-implementation",
            "example-addon-boundary", "uses-cloudislands-api-and-addon-spi-without-core-internals",
            "example-addon-certification", "run-ApiContractVerifier-in-addon-ci",
            "feature-dependencies", "route-events:addon-state",
            "feature-aliases", "events:lifecycle"
        );
    }

    @Override
    public List<MissionProviderDefinitionSnapshot> addonMissions() {
        return List.of(new MissionProviderDefinitionSnapshot(
            ADDON_ID,
            "example-harvest",
            "MISSION",
            "developer",
            "Harvest Starter",
            "Break 64 wheat crops on your island.",
            "BLOCK_BREAK",
            "minecraft:wheat",
            64L,
            "COMMAND",
            "eco give %player% 100",
            false,
            false,
            true,
            Instant.EPOCH
        ));
    }

    @Override
    public List<AddonPlaceholderSnapshot> addonPlaceholders() {
        return List.of(new AddonPlaceholderSnapshot(
            "example_level_goal",
            "Shows the next example mission target for a player island.",
            "64 wheat",
            true,
            true
        ));
    }

    @Override
    public List<AddonMenuButtonSnapshot> addonMenuButtons() {
        return List.of(new AddonMenuButtonSnapshot(
            "island.main",
            "example.open",
            "WHEAT",
            "Example Mission",
            "/island mission example-harvest",
            true
        ));
    }

    @Override
    public List<BlockValueSnapshot> addonBlockValues() {
        return List.of(new BlockValueSnapshot("minecraft:wheat", "1.00", 1L, 4096L));
    }
}

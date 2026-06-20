package kr.lunaf.cloudislands.exampleaddon;

import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;

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
            "placeholders", false
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
}

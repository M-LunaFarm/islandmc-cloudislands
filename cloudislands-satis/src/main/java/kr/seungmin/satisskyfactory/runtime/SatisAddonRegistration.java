package kr.seungmin.satisskyfactory.runtime;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddonBootstrap;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.seungmin.satisskyfactory.integration.SatisAddonIntegrationPolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;

public final class SatisAddonRegistration {
    private final JavaPlugin plugin;

    public SatisAddonRegistration(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CloudIslandsApi resolveApi() {
        CloudIslandsApi api = CloudIslandsAddonBootstrap.findApi().orElse(null);
        if (api != null) {
            return api;
        }
        return plugin.getServer().getServicesManager().load(CloudIslandsApi.class);
    }

    public static RuntimeState missingApiState() {
        return RuntimeState.disabled(true);
    }

    public static RuntimeState failedRegistrationState() {
        return RuntimeState.disabled(false);
    }

    public static RuntimeState registeredState(
            CloudIslandsAddonSnapshot addon,
            String integrationMode,
            boolean enabledByDefault,
            boolean addonStateReportingEnabled
    ) {
        var activationDecision = SatisAddonIntegrationPolicy.activationDecision(
                integrationMode,
                enabledByDefault,
                addon.enabled(),
                true,
                true
        );
        if (!activationDecision.runtimeEnabled()) {
            return new RuntimeState(false, false, Map.of(), addonStateReportingEnabled, activationDecision.blockReason());
        }
        return new RuntimeState(true, false, addon.features(), addonStateReportingEnabled, "none");
    }

    public static RuntimeState unregisteredState() {
        return RuntimeState.disabled(false);
    }

    public record RuntimeState(
            boolean runtimeEnabled,
            boolean cloudIslandsApiMissing,
            Map<String, Boolean> features,
            boolean addonStateReportingEnabled,
            String blockReason
    ) {
        public static RuntimeState disabled(boolean apiMissing) {
            return new RuntimeState(false, apiMissing, Map.of(), false, apiMissing ? "cloudislands-api-missing" : "disabled");
        }
    }
}

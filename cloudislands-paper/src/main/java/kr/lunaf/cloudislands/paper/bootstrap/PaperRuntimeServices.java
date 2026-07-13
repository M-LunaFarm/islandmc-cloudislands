package kr.lunaf.cloudislands.paper.bootstrap;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.economy.EconomyProviderState;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.api.PaperCloudIslandsApi;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.economy.VaultEconomyBridge;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanAnalyticsRuntime;
import kr.lunaf.cloudislands.paper.placeholder.CloudIslandsPlaceholderExpansion;
import org.bukkit.plugin.ServicePriority;

public final class PaperRuntimeServices implements RuntimeComponent {
    private final CloudIslandsPaperPlugin plugin;
    private CloudIslandsApi api;
    private EconomyBridge economyBridge;
    private Object placeholderExpansion;
    private RuntimeComponent planAnalytics;

    private PaperRuntimeServices(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public static PaperRuntimeServices register(CloudIslandsPaperPlugin plugin, CoreApiClient client, CloudIslandsPaperAgent agent, PaperRuntimeConfig config) {
        PaperRuntimeServices services = new PaperRuntimeServices(plugin);
        services.registerApi(client, agent, config);
        services.registerEconomy();
        services.registerPlaceholderExpansion(client);
        services.registerPlanAnalytics(client);
        return services;
    }

    public EconomyBridge economyBridge() {
        return economyBridge;
    }

    @Override
    public void stop() {
        unregisterPlanAnalytics();
        unregisterPlaceholderExpansion();
        plugin.integrationRegistry().clearRuntimeService("Vault");
        if (api != null) {
            CloudIslandsProvider.clear(api);
            plugin.getServer().getServicesManager().unregister(CloudIslandsApi.class, api);
            api = null;
        }
        if (economyBridge != null) {
            plugin.getServer().getServicesManager().unregister(EconomyBridge.class, economyBridge);
            economyBridge = null;
        }
    }

    private void registerApi(CoreApiClient client, CloudIslandsPaperAgent agent, PaperRuntimeConfig config) {
        this.api = new PaperCloudIslandsApi(client, agent, config);
        CloudIslandsProvider.set(api);
        plugin.getServer().getServicesManager().register(CloudIslandsApi.class, api, plugin, ServicePriority.Normal);
    }

    private void registerEconomy() {
        this.economyBridge = new VaultEconomyBridge(plugin, state -> {
            if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
                reportVaultState(state);
            }
        });
        plugin.getServer().getServicesManager().register(EconomyBridge.class, economyBridge, plugin, ServicePriority.Normal);
        EconomyProviderState state = economyBridge.providerState();
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            reportVaultState(state);
        }
    }

    private void reportVaultState(EconomyProviderState state) {
        plugin.integrationRegistry().reportRuntimeService(
            "Vault",
            state == EconomyProviderState.ACTIVE,
            "economy-provider-runtime-readiness",
            java.util.Map.of("providerState", state.name())
        );
    }

    private void registerPlaceholderExpansion(CoreApiClient client) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            CloudIslandsPlaceholderExpansion expansion = new CloudIslandsPlaceholderExpansion(plugin, client);
            if (expansion.register()) {
                this.placeholderExpansion = expansion;
                plugin.integrationRegistry().reportRuntimeService(
                    "PlaceholderAPI",
                    true,
                    "cloudislands-expansion-registration",
                    java.util.Map.of("identifier", expansion.getIdentifier())
                );
                plugin.getLogger().info("Registered PlaceholderAPI expansion: cloudislands");
            } else {
                plugin.integrationRegistry().reportRuntimeService(
                    "PlaceholderAPI",
                    false,
                    "cloudislands-expansion-registration",
                    java.util.Map.of("reason", "register-returned-false")
                );
            }
        } catch (LinkageError | RuntimeException error) {
            plugin.integrationRegistry().reportRuntimeService(
                "PlaceholderAPI",
                false,
                "cloudislands-expansion-registration",
                java.util.Map.of("reason", error.getClass().getSimpleName())
            );
            plugin.getLogger().warning("PlaceholderAPI was detected but the CloudIslands expansion could not be registered: " + error.getMessage());
        }
    }

    private void unregisterPlaceholderExpansion() {
        plugin.integrationRegistry().clearRuntimeService("PlaceholderAPI");
        Object expansion = placeholderExpansion;
        placeholderExpansion = null;
        if (expansion == null) {
            return;
        }
        try {
            expansion.getClass().getMethod("unregister").invoke(expansion);
        } catch (ReflectiveOperationException ignored) {
            // PlaceholderAPI handles plugin-disable cleanup when explicit unregister is unavailable.
        }
    }

    private void registerPlanAnalytics(CoreApiClient client) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Plan")) {
            return;
        }
        try {
            this.planAnalytics = PlanAnalyticsRuntime.start(plugin, client);
            plugin.integrationRegistry().reportRuntimeService(
                "Plan",
                true,
                "cloudislands-data-extension-registration",
                java.util.Map.of("identifier", "CloudIslands", "refreshSeconds", "60")
            );
            plugin.getLogger().info("Registered Plan data extension: CloudIslands");
        } catch (LinkageError | RuntimeException error) {
            plugin.integrationRegistry().reportRuntimeService(
                "Plan",
                false,
                "cloudislands-data-extension-registration",
                java.util.Map.of("reason", error.getClass().getSimpleName())
            );
            plugin.getLogger().warning("Plan was detected but the CloudIslands data extension could not be registered: " + error.getMessage());
        }
    }

    private void unregisterPlanAnalytics() {
        plugin.integrationRegistry().clearRuntimeService("Plan");
        RuntimeComponent analytics = planAnalytics;
        planAnalytics = null;
        if (analytics != null) {
            try {
                analytics.stop();
            } catch (LinkageError | RuntimeException error) {
                plugin.getLogger().warning("Plan analytics cleanup failed during plugin shutdown: " + error.getMessage());
            }
        }
    }
}

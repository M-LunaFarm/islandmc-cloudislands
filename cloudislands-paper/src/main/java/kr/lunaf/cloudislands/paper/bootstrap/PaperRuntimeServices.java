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
import kr.lunaf.cloudislands.paper.integration.customitem.CustomBlockKeyService;
import kr.lunaf.cloudislands.paper.integration.vanish.PlayerVisibilityService;
import kr.lunaf.cloudislands.paper.placeholder.CloudIslandsPlaceholderExpansion;
import org.bukkit.plugin.ServicePriority;

public final class PaperRuntimeServices implements RuntimeComponent {
    private final CloudIslandsPaperPlugin plugin;
    private CloudIslandsApi api;
    private EconomyBridge economyBridge;
    private Object placeholderExpansion;
    private RuntimeComponent planAnalytics;
    private PlayerVisibilityService playerVisibility;
    private CustomBlockKeyService customBlockKeys;

    private PaperRuntimeServices(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public static PaperRuntimeServices register(CloudIslandsPaperPlugin plugin, CoreApiClient client, CloudIslandsPaperAgent agent, PaperRuntimeConfig config) {
        PaperRuntimeServices services = new PaperRuntimeServices(plugin);
        services.registerApi(client, agent, config);
        services.registerEconomy();
        services.registerPlaceholderExpansion(client);
        services.registerPlanAnalytics(client);
        services.registerPlayerVisibility();
        services.registerCustomBlockKeys();
        return services;
    }

    public EconomyBridge economyBridge() {
        return economyBridge;
    }

    public PlayerVisibilityService playerVisibility() {
        return playerVisibility == null ? PlayerVisibilityService.metadataOnly() : playerVisibility;
    }

    public CustomBlockKeyService customBlockKeys() {
        return customBlockKeys == null ? CustomBlockKeyService.vanillaOnly() : customBlockKeys;
    }

    @Override
    public void stop() {
        unregisterCustomBlockKeys();
        unregisterPlayerVisibility();
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

    private void registerPlayerVisibility() {
        this.playerVisibility = PlayerVisibilityService.discover(plugin.getServer());
        for (String pluginName : PlayerVisibilityService.supportedPlugins()) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled(pluginName)) {
                continue;
            }
            boolean supported = playerVisibility.supports(pluginName);
            plugin.integrationRegistry().reportRuntimeService(
                pluginName,
                supported,
                "vanish-presence-filter-registration",
                playerVisibility.runtimeDetails(pluginName)
            );
            if (supported) {
                plugin.getLogger().info("Registered vanished-player suggestion filter for " + pluginName);
            } else {
                plugin.getLogger().warning(pluginName + " was detected but its vanish API was unavailable; only metadata and Bukkit visibility fallbacks are active");
            }
        }
    }

    private void unregisterPlayerVisibility() {
        for (String pluginName : PlayerVisibilityService.supportedPlugins()) {
            plugin.integrationRegistry().clearRuntimeService(pluginName);
        }
        playerVisibility = null;
    }

    private void registerCustomBlockKeys() {
        this.customBlockKeys = CustomBlockKeyService.discover(plugin.getServer());
        for (String pluginName : CustomBlockKeyService.supportedPlugins()) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled(pluginName)) {
                continue;
            }
            boolean supported = customBlockKeys.supports(pluginName);
            plugin.integrationRegistry().reportRuntimeService(
                pluginName,
                supported,
                "custom-block-key-registration",
                customBlockKeys.runtimeDetails(pluginName)
            );
            if (supported) {
                plugin.getLogger().info("Registered custom block value resolver for " + pluginName);
            } else {
                plugin.getLogger().warning(pluginName + " was detected but its custom block lookup API was unavailable; vanilla material counting remains active");
            }
        }
    }

    private void unregisterCustomBlockKeys() {
        for (String pluginName : CustomBlockKeyService.supportedPlugins()) {
            plugin.integrationRegistry().clearRuntimeService(pluginName);
        }
        customBlockKeys = null;
    }
}

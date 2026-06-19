package kr.lunaf.cloudislands.paper.bootstrap;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.api.PaperCloudIslandsApi;
import kr.lunaf.cloudislands.paper.economy.VaultEconomyBridge;
import kr.lunaf.cloudislands.paper.placeholder.CloudIslandsPlaceholderExpansion;
import org.bukkit.plugin.ServicePriority;

public final class PaperRuntimeServices implements RuntimeComponent {
    private final CloudIslandsPaperPlugin plugin;
    private CloudIslandsApi api;
    private EconomyBridge economyBridge;
    private Object placeholderExpansion;

    private PaperRuntimeServices(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    public static PaperRuntimeServices register(CloudIslandsPaperPlugin plugin, CoreApiClient client, CloudIslandsPaperAgent agent) {
        PaperRuntimeServices services = new PaperRuntimeServices(plugin);
        services.registerApi(client, agent);
        services.registerEconomy();
        services.registerPlaceholderExpansion(client);
        return services;
    }

    public EconomyBridge economyBridge() {
        return economyBridge;
    }

    @Override
    public void stop() {
        unregisterPlaceholderExpansion();
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

    private void registerApi(CoreApiClient client, CloudIslandsPaperAgent agent) {
        this.api = new PaperCloudIslandsApi(client, agent);
        CloudIslandsProvider.set(api);
        plugin.getServer().getServicesManager().register(CloudIslandsApi.class, api, plugin, ServicePriority.Normal);
    }

    private void registerEconomy() {
        this.economyBridge = new VaultEconomyBridge(plugin.getServer());
        plugin.getServer().getServicesManager().register(EconomyBridge.class, economyBridge, plugin, ServicePriority.Normal);
    }

    private void registerPlaceholderExpansion(CoreApiClient client) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            CloudIslandsPlaceholderExpansion expansion = new CloudIslandsPlaceholderExpansion(plugin, client);
            if (expansion.register()) {
                this.placeholderExpansion = expansion;
                plugin.getLogger().info("Registered PlaceholderAPI expansion: cloudislands");
            }
        } catch (LinkageError error) {
            plugin.getLogger().warning("PlaceholderAPI was detected but the CloudIslands expansion could not be registered: " + error.getMessage());
        }
    }

    private void unregisterPlaceholderExpansion() {
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
}

package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.cache.GlobalEventCacheInvalidator;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.bukkit.plugin.Plugin;

public final class CloudIslandsPaperAgent {
    private final Plugin plugin;
    private final AgentRole role;
    private final RouteTicketConsumer routeTicketConsumer;
    private final ProtectionController protectionController;
    private final LocalIslandPermissionCache permissionCache;
    private final GlobalEventCacheInvalidator cacheInvalidator;

    public CloudIslandsPaperAgent(Plugin plugin, AgentRole role, CoreApiClient coreApiClient, String nodeId) {
        this.plugin = plugin;
        this.role = role;
        this.permissionCache = new LocalIslandPermissionCache();
        this.routeTicketConsumer = new RouteTicketConsumer(plugin, coreApiClient, nodeId);
        this.protectionController = new ProtectionController(new RegionIndex(), permissionCache);
        this.cacheInvalidator = new GlobalEventCacheInvalidator(permissionCache);
    }

    public Plugin plugin() {
        return plugin;
    }

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            return javaPlugin.getConfig();
        }
        return new org.bukkit.configuration.file.YamlConfiguration();
    }

    public AgentRole role() {
        return role;
    }

    public RouteTicketConsumer routeTickets() {
        return routeTicketConsumer;
    }

    public ProtectionController protection() {
        return protectionController;
    }

    public LocalIslandPermissionCache permissionCache() {
        return permissionCache;
    }

    public GlobalEventCacheInvalidator cacheInvalidator() {
        return cacheInvalidator;
    }
}

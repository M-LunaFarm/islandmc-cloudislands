package kr.lunaf.cloudislands.paper.integration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationOperationPlan;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

final class BukkitIntegrationExternalRuntime implements IntegrationExternalRuntime {
    private final Server server;

    private BukkitIntegrationExternalRuntime(Server server) {
        this.server = server;
    }

    static IntegrationExternalRuntime create(Server server) {
        return new BukkitIntegrationExternalRuntime(server);
    }

    @Override
    public IntegrationResult invoke(String pluginName, String category, String operation, IntegrationContext context, IntegrationOperationPlan plan) {
        if (server == null) {
            return IntegrationResult.skipped(pluginName + " external runtime has no Bukkit server");
        }
        Plugin plugin = server.getPluginManager().getPlugin(pluginName);
        if (plugin == null || !server.getPluginManager().isPluginEnabled(pluginName)) {
            return IntegrationResult.skipped(pluginName + " is not enabled in Bukkit");
        }
        return IntegrationResult.success(pluginName + " Bukkit adapter accepted " + operation, details(pluginName, plugin, category, operation, plan));
    }

    private Map<String, String> details(String pluginName, Plugin plugin, String category, String operation, IntegrationOperationPlan plan) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("runtime", "bukkit");
        details.put("adapter", "reflective-plugin-api");
        details.put("pluginName", pluginName == null ? "" : pluginName);
        details.put("pluginClass", plugin.getClass().getName());
        details.put("pluginVersion", pluginVersion(plugin));
        details.put("pluginEnabled", Boolean.toString(server.getPluginManager().isPluginEnabled(pluginName)));
        details.put("category", category == null ? "" : category);
        details.put("operation", operation == null ? "" : operation);
        if (plan != null) {
            details.put("externalApi", plan.externalApi());
            details.put("stateChanging", Boolean.toString(plan.stateChanging()));
            details.put("requiredMetadata", String.join(",", plan.requiredMetadata()));
            details.put("artifactMode", plan.stateChanging() ? "state-transfer-manifest" : "observation");
        }
        details.putAll(apiProbe(pluginName, plugin));
        return Map.copyOf(details);
    }

    private Map<String, String> apiProbe(String pluginName, Plugin plugin) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        switch (pluginName) {
            case "CoreProtect" -> {
                details.put("apiProbe.method.getAPI", Boolean.toString(hasMethod(plugin, "getAPI")));
                details.put("apiProbe.class.CoreProtectAPI", Boolean.toString(hasClass("net.coreprotect.CoreProtectAPI")));
            }
            case "WorldEdit", "FastAsyncWorldEdit" -> {
                details.put("apiProbe.class.WorldEdit", Boolean.toString(hasClass("com.sk89q.worldedit.WorldEdit")));
                details.put("apiProbe.class.EditSession", Boolean.toString(hasClass("com.sk89q.worldedit.EditSession")));
            }
            case "ItemsAdder" -> details.put("apiProbe.class.CustomBlock", Boolean.toString(hasClass("dev.lone.itemsadder.api.CustomBlock")));
            case "Oraxen" -> details.put("apiProbe.class.OraxenItems", Boolean.toString(hasClass("io.th0rgal.oraxen.api.OraxenItems")));
            case "Nexo" -> details.put("apiProbe.class.NexoItems", Boolean.toString(hasClass("com.nexomc.nexo.api.NexoItems")));
            case "RoseStacker" -> details.put("apiProbe.class.RoseStackerAPI", Boolean.toString(hasClass("dev.rosewood.rosestacker.api.RoseStackerAPI")));
            case "LuckPerms" -> details.put("apiProbe.bukkitService.LuckPerms", Boolean.toString(hasBukkitService("net.luckperms.api.LuckPerms")));
            case "Plan" -> details.put("apiProbe.class.ExtensionService", Boolean.toString(hasClass("com.djrapitops.plan.extension.ExtensionService")));
            default -> details.put("apiProbe.pluginClass", plugin.getClass().getName());
        }
        return details;
    }

    private static boolean hasMethod(Plugin plugin, String methodName) {
        if (plugin == null || methodName == null || methodName.isBlank()) {
            return false;
        }
        for (Method method : plugin.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hasBukkitService(String className) {
        try {
            Class serviceClass = Class.forName(className);
            return server.getServicesManager().load(serviceClass) != null;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static String pluginVersion(Plugin plugin) {
        return plugin == null || plugin.getDescription() == null || plugin.getDescription().getVersion() == null
            ? ""
            : plugin.getDescription().getVersion();
    }
}

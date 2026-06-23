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
        Map<String, String> details = details(pluginName, plugin, category, operation, plan);
        if (!externalApiAvailable(pluginName, details, plan)) {
            return IntegrationResult.failed(pluginName + " Bukkit adapter cannot execute " + operation + ": external API unavailable", details);
        }
        return IntegrationResult.skipped(pluginName + " Bukkit adapter verified API for " + operation + " but no operation executor is implemented", details);
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
                Object api = invokeNoArg(plugin, "getAPI");
                details.put("apiProbe.invoke.getAPI", Boolean.toString(api != null));
                details.put("apiProbe.object.CoreProtectAPI", className(api));
            }
            case "WorldEdit", "FastAsyncWorldEdit" -> {
                details.put("apiProbe.class.WorldEdit", Boolean.toString(hasClass("com.sk89q.worldedit.WorldEdit")));
                details.put("apiProbe.class.EditSession", Boolean.toString(hasClass("com.sk89q.worldedit.EditSession")));
                Object worldEdit = invokeStaticNoArg("com.sk89q.worldedit.WorldEdit", "getInstance");
                details.put("apiProbe.invoke.WorldEdit.getInstance", Boolean.toString(worldEdit != null));
                details.put("apiProbe.object.WorldEdit", className(worldEdit));
            }
            case "ItemsAdder" -> details.put("apiProbe.class.CustomBlock", Boolean.toString(hasClass("dev.lone.itemsadder.api.CustomBlock")));
            case "Oraxen" -> details.put("apiProbe.class.OraxenItems", Boolean.toString(hasClass("io.th0rgal.oraxen.api.OraxenItems")));
            case "Nexo" -> details.put("apiProbe.class.NexoItems", Boolean.toString(hasClass("com.nexomc.nexo.api.NexoItems")));
            case "RoseStacker" -> {
                details.put("apiProbe.class.RoseStackerAPI", Boolean.toString(hasClass("dev.rosewood.rosestacker.api.RoseStackerAPI")));
                Object api = invokeStaticNoArg("dev.rosewood.rosestacker.api.RoseStackerAPI", "getInstance");
                details.put("apiProbe.invoke.RoseStackerAPI.getInstance", Boolean.toString(api != null));
                details.put("apiProbe.object.RoseStackerAPI", className(api));
            }
            case "LuckPerms" -> {
                Object service = bukkitService("net.luckperms.api.LuckPerms");
                details.put("apiProbe.bukkitService.LuckPerms", Boolean.toString(service != null));
                details.put("apiProbe.object.LuckPerms", className(service));
            }
            case "Plan" -> details.put("apiProbe.class.ExtensionService", Boolean.toString(hasClass("com.djrapitops.plan.extension.ExtensionService")));
            default -> details.put("apiProbe.pluginClass", plugin.getClass().getName());
        }
        return details;
    }

    private boolean externalApiAvailable(String pluginName, Map<String, String> details, IntegrationOperationPlan plan) {
        if (plan == null || plan.externalApi().isBlank()) {
            return true;
        }
        return switch (pluginName) {
            case "CoreProtect" ->
                bool(details, "apiProbe.method.getAPI")
                    && bool(details, "apiProbe.invoke.getAPI")
                    && bool(details, "apiProbe.class.CoreProtectAPI");
            case "WorldEdit", "FastAsyncWorldEdit" ->
                bool(details, "apiProbe.class.WorldEdit")
                    && bool(details, "apiProbe.class.EditSession")
                    && bool(details, "apiProbe.invoke.WorldEdit.getInstance");
            case "ItemsAdder" -> bool(details, "apiProbe.class.CustomBlock");
            case "Oraxen" -> bool(details, "apiProbe.class.OraxenItems");
            case "Nexo" -> bool(details, "apiProbe.class.NexoItems");
            case "RoseStacker" ->
                bool(details, "apiProbe.class.RoseStackerAPI")
                    && bool(details, "apiProbe.invoke.RoseStackerAPI.getInstance");
            case "LuckPerms" -> bool(details, "apiProbe.bukkitService.LuckPerms");
            case "Plan" -> bool(details, "apiProbe.class.ExtensionService");
            default -> true;
        };
    }

    private boolean bool(Map<String, String> details, String key) {
        return Boolean.parseBoolean(details.getOrDefault(key, "false"));
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

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Object invokeStaticNoArg(String className, String methodName) {
        if (className == null || className.isBlank() || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = Class.forName(className).getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static String className(Object value) {
        return value == null ? "" : value.getClass().getName();
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
    private Object bukkitService(String className) {
        try {
            Class serviceClass = Class.forName(className);
            return server.getServicesManager().load(serviceClass);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static String pluginVersion(Plugin plugin) {
        return plugin == null || plugin.getDescription() == null || plugin.getDescription().getVersion() == null
            ? ""
            : plugin.getDescription().getVersion();
    }
}

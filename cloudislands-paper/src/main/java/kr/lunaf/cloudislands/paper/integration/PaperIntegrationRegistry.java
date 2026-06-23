package kr.lunaf.cloudislands.paper.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanIntegration;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomItemIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.stacker.StackerIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.CloudIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

public final class PaperIntegrationRegistry {
    private final Server server;
    private final Map<String, CloudIntegration> integrations;

    private PaperIntegrationRegistry(Server server, List<CloudIntegration> integrations) {
        this.server = server;
        this.integrations = integrations.stream().collect(Collectors.toUnmodifiableMap(CloudIntegration::pluginName, Function.identity()));
    }

    public static PaperIntegrationRegistry discover(Server server) {
        return new PaperIntegrationRegistry(server, defaultIntegrations(bukkitExternalRuntime(server)));
    }

    public List<IntegrationStatus> snapshot() {
        List<IntegrationStatus> statuses = new ArrayList<>();
        for (String pluginName : CloudIntegrationPolicy.knownPlugins()) {
            statuses.add(status(pluginName));
        }
        return List.copyOf(statuses);
    }

    public IntegrationStatus status(String pluginName) {
        CloudIntegration integration = integrations.getOrDefault(pluginName, genericIntegration(pluginName));
        boolean enabled = pluginEnabled(pluginName);
        Plugin plugin = plugin(integration.pluginName());
        IntegrationSupportState discoveryState = enabled ? IntegrationSupportState.DETECTED : IntegrationSupportState.NOT_INSTALLED;
        IntegrationSupportState apiState = apiState(integration, enabled, plugin);
        IntegrationSupportState adapterState = adapterState(integration, enabled, apiState);
        return new IntegrationStatus(
            pluginName,
            integration.category(),
            integration.detect(enabled),
            summarizeState(discoveryState, apiState, adapterState, null),
            discoveryState,
            apiState,
            adapterState,
            null,
            CloudIntegrationPolicy.requiresRuntimeAuthority(pluginName, false),
            CloudIntegrationPolicy.requiredRuntimeClaims(),
            integration.capabilities()
        );
    }

    public CloudIntegration integration(String pluginName) {
        return integrations.getOrDefault(pluginName, genericIntegration(pluginName));
    }

    public IntegrationResult validateVersion(String pluginName, IntegrationContext context) {
        return execute(pluginName, CloudIntegration::validateVersion, context, false);
    }

    public IntegrationResult onIslandActivate(String pluginName, IntegrationContext context) {
        return execute(pluginName, CloudIntegration::onIslandActivate, context);
    }

    public IntegrationResult onIslandDeactivate(String pluginName, IntegrationContext context) {
        return execute(pluginName, CloudIntegration::onIslandDeactivate, context);
    }

    public IntegrationResult exportState(String pluginName, IntegrationContext context) {
        return execute(pluginName, CloudIntegration::exportState, context);
    }

    public IntegrationResult restoreState(String pluginName, IntegrationContext context) {
        return execute(pluginName, CloudIntegration::restoreState, context);
    }

    public CloudIntegrationPolicy.HookDecision validateHookContext(CloudIntegrationPolicy.HookContext context) {
        return CloudIntegrationPolicy.validateHookContext(context);
    }

    public String statusLine() {
        List<String> entries = new ArrayList<>();
        for (IntegrationStatus status : snapshot()) {
            entries.add(status.pluginName() + "=" + status.state() + ":" + status.category());
        }
        return String.join(", ", entries);
    }

    public String diagnosticsSection() {
        StringBuilder builder = new StringBuilder();
        builder.append("## integrations\n");
        builder.append("policy=").append(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY).append('\n');
        builder.append("runtimeClaims=").append(String.join(",", CloudIntegrationPolicy.requiredRuntimeClaims())).append('\n');
        for (IntegrationStatus status : snapshot()) {
            builder.append(status.pluginName())
                .append(":enabled=").append(status.enabled())
                .append(",state=").append(status.state())
                .append(",discoveryState=").append(status.discoveryState())
                .append(",apiState=").append(status.apiState())
                .append(",adapterState=").append(status.adapterState())
                .append(",category=").append(status.category())
                .append(",runtimeAuthorityRequired=").append(status.runtimeAuthorityRequired())
                .append(",capabilities=").append(status.capabilities())
                .append('\n');
        }
        return builder.toString();
    }

    private static List<CloudIntegration> defaultIntegrations(IntegrationExternalRuntime externalRuntime) {
        List<CloudIntegration> integrations = new ArrayList<>();
        for (String pluginName : CloudIntegrationPolicy.knownPlugins()) {
            integrations.add(specificIntegration(pluginName, externalRuntime));
        }
        return List.copyOf(integrations);
    }

    private static CloudIntegration specificIntegration(String pluginName, IntegrationExternalRuntime externalRuntime) {
        return switch (pluginName) {
            case "CoreProtect" -> new CoreProtectIntegration(externalRuntime);
            case "WorldEdit", "FastAsyncWorldEdit" -> new WorldEditIntegration(pluginName, externalRuntime);
            case "ItemsAdder", "Oraxen", "Nexo", "Slimefun" -> new CustomItemIntegration(pluginName, externalRuntime);
            case "RoseStacker", "WildStacker", "AdvancedSpawners" -> new StackerIntegration(pluginName, externalRuntime);
            case "LuckPerms" -> new LuckPermsIntegration(externalRuntime);
            case "Plan" -> new PlanIntegration(externalRuntime);
            default -> genericIntegration(pluginName);
        };
    }

    private static IntegrationExternalRuntime bukkitExternalRuntime(Server server) {
        return BukkitIntegrationExternalRuntime.create(server);
    }

    private static CloudIntegration genericIntegration(String pluginName) {
        Set<IntegrationCapability> capabilities = CloudIntegrationPolicy.requiresRuntimeAuthority(pluginName, false)
            ? Set.of(IntegrationCapability.DETECT, IntegrationCapability.RUNTIME_AUTHORITY)
            : Set.of(IntegrationCapability.DETECT);
        return new PolicyBackedCloudIntegration(pluginName, capabilities);
    }

    private IntegrationResult execute(String pluginName, BiFunction<CloudIntegration, IntegrationContext, IntegrationResult> operation, IntegrationContext context) {
        return execute(pluginName, operation, context, true);
    }

    private IntegrationResult execute(String pluginName, BiFunction<CloudIntegration, IntegrationContext, IntegrationResult> operation, IntegrationContext context, boolean validateVersion) {
        CloudIntegration integration = integration(pluginName);
        Plugin plugin = plugin(integration.pluginName());
        if (!integration.detect(pluginEnabled(integration.pluginName()))) {
            return IntegrationResult.skipped(integration.pluginName() + " is not enabled");
        }
        IntegrationContext enrichedContext = withPluginRuntimeMetadata(integration.pluginName(), context, plugin);
        if (validateVersion && integration.capabilities().contains(IntegrationCapability.VALIDATE_VERSION)) {
            IntegrationResult version = integration.validateVersion(enrichedContext);
            if (version.status() == IntegrationResult.Status.FAILED) {
                return version;
            }
        }
        return operation.apply(integration, enrichedContext);
    }

    private IntegrationSupportState apiState(CloudIntegration integration, boolean enabled, Plugin plugin) {
        if (!enabled) {
            return IntegrationSupportState.NOT_INSTALLED;
        }
        if (!specificAdapter(integration)) {
            return IntegrationSupportState.UNSUPPORTED;
        }
        if (!integration.capabilities().contains(IntegrationCapability.VALIDATE_VERSION)) {
            return IntegrationSupportState.API_COMPATIBLE;
        }
        IntegrationContext context = plugin == null
            ? new IntegrationContext(null, "", 0L, false, "", Map.of())
            : withPluginRuntimeMetadata(integration.pluginName(), new IntegrationContext(null, "", 0L, false, "", Map.of()), plugin);
        IntegrationResult result = integration.validateVersion(context);
        return result.status() == IntegrationResult.Status.FAILED
            ? IntegrationSupportState.API_INCOMPATIBLE
            : IntegrationSupportState.API_COMPATIBLE;
    }

    private IntegrationSupportState adapterState(CloudIntegration integration, boolean enabled, IntegrationSupportState apiState) {
        if (!enabled || !specificAdapter(integration) || apiState != IntegrationSupportState.API_COMPATIBLE) {
            return IntegrationSupportState.ADAPTER_INACTIVE;
        }
        return IntegrationSupportState.ACTIVE;
    }

    private static boolean specificAdapter(CloudIntegration integration) {
        return integration != null && integration.getClass() != PolicyBackedCloudIntegration.class;
    }

    public static IntegrationSupportState operationState(IntegrationResult result) {
        return IntegrationSupportState.operationState(result);
    }

    private static IntegrationSupportState summarizeState(
            IntegrationSupportState discoveryState,
            IntegrationSupportState apiState,
            IntegrationSupportState adapterState,
            IntegrationSupportState operationState) {
        if (operationState == IntegrationSupportState.OPERATION_FAILED || operationState == IntegrationSupportState.OPERATION_SUCCEEDED) {
            return operationState;
        }
        if (operationState == IntegrationSupportState.ADAPTER_INACTIVE) {
            return IntegrationSupportState.ADAPTER_INACTIVE;
        }
        if (discoveryState == IntegrationSupportState.NOT_INSTALLED) {
            return IntegrationSupportState.NOT_INSTALLED;
        }
        if (apiState == IntegrationSupportState.UNSUPPORTED || apiState == IntegrationSupportState.API_INCOMPATIBLE) {
            return apiState;
        }
        if (adapterState == IntegrationSupportState.ACTIVE) {
            return IntegrationSupportState.ACTIVE;
        }
        if (apiState == IntegrationSupportState.API_COMPATIBLE) {
            return IntegrationSupportState.API_COMPATIBLE;
        }
        return IntegrationSupportState.DETECTED;
    }

    private IntegrationContext withPluginRuntimeMetadata(String pluginName, IntegrationContext context, Plugin plugin) {
        if (context == null || plugin == null) {
            return context;
        }
        LinkedHashMap<String, String> runtimeMetadata = new LinkedHashMap<>();
        runtimeMetadata.put("pluginName", pluginName == null || pluginName.isBlank() ? plugin.getName() : pluginName);
        runtimeMetadata.put("pluginClass", plugin.getClass().getName());
        runtimeMetadata.put("pluginEnabled", Boolean.toString(pluginEnabled(pluginName)));
        String version = pluginVersion(plugin);
        if (!version.isBlank()) {
            runtimeMetadata.put("pluginVersion", version);
        }
        return context.withMetadata(runtimeMetadata);
    }

    @SuppressWarnings("deprecation")
    private static String pluginVersion(Plugin plugin) {
        return plugin == null || plugin.getDescription() == null || plugin.getDescription().getVersion() == null
            ? ""
            : plugin.getDescription().getVersion();
    }

    private boolean pluginEnabled(String pluginName) {
        return server != null && server.getPluginManager().isPluginEnabled(pluginName);
    }

    private Plugin plugin(String pluginName) {
        return server == null ? null : server.getPluginManager().getPlugin(pluginName);
    }

    public record IntegrationStatus(
        String pluginName,
        String category,
        boolean enabled,
        IntegrationSupportState state,
        IntegrationSupportState discoveryState,
        IntegrationSupportState apiState,
        IntegrationSupportState adapterState,
        IntegrationSupportState lastOperationState,
        boolean runtimeAuthorityRequired,
        List<String> requiredRuntimeClaims,
        Set<IntegrationCapability> capabilities
    ) {
        public IntegrationStatus {
            state = state == null ? IntegrationSupportState.NOT_INSTALLED : state;
            discoveryState = discoveryState == null ? IntegrationSupportState.NOT_INSTALLED : discoveryState;
            apiState = apiState == null ? IntegrationSupportState.NOT_INSTALLED : apiState;
            adapterState = adapterState == null ? IntegrationSupportState.ADAPTER_INACTIVE : adapterState;
            requiredRuntimeClaims = List.copyOf(requiredRuntimeClaims);
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }
}

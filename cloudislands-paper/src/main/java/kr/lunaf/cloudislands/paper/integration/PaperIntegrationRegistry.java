package kr.lunaf.cloudislands.paper.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import org.bukkit.Server;

public final class PaperIntegrationRegistry {
    private final Server server;
    private final Map<String, CloudIntegration> integrations;

    private PaperIntegrationRegistry(Server server, List<CloudIntegration> integrations) {
        this.server = server;
        this.integrations = integrations.stream().collect(Collectors.toUnmodifiableMap(CloudIntegration::pluginName, Function.identity()));
    }

    public static PaperIntegrationRegistry discover(Server server) {
        return new PaperIntegrationRegistry(server, defaultIntegrations());
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
        boolean enabled = server.getPluginManager().isPluginEnabled(pluginName);
        return new IntegrationStatus(
            pluginName,
            integration.category(),
            integration.detect(enabled),
            CloudIntegrationPolicy.requiresRuntimeAuthority(pluginName, false),
            CloudIntegrationPolicy.requiredRuntimeClaims(),
            integration.capabilities()
        );
    }

    public CloudIntegration integration(String pluginName) {
        return integrations.getOrDefault(pluginName, genericIntegration(pluginName));
    }

    public CloudIntegrationPolicy.HookDecision validateHookContext(CloudIntegrationPolicy.HookContext context) {
        return CloudIntegrationPolicy.validateHookContext(context);
    }

    public String statusLine() {
        List<String> entries = new ArrayList<>();
        for (IntegrationStatus status : snapshot()) {
            entries.add(status.pluginName() + "=" + (status.enabled() ? "enabled" : "missing") + ":" + status.category());
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
                .append(",category=").append(status.category())
                .append(",runtimeAuthorityRequired=").append(status.runtimeAuthorityRequired())
                .append(",capabilities=").append(status.capabilities())
                .append('\n');
        }
        return builder.toString();
    }

    private static List<CloudIntegration> defaultIntegrations() {
        List<CloudIntegration> integrations = new ArrayList<>();
        for (String pluginName : CloudIntegrationPolicy.knownPlugins()) {
            integrations.add(specificIntegration(pluginName));
        }
        return List.copyOf(integrations);
    }

    private static CloudIntegration specificIntegration(String pluginName) {
        return switch (pluginName) {
            case "CoreProtect" -> new CoreProtectIntegration();
            case "WorldEdit", "FastAsyncWorldEdit" -> new WorldEditIntegration(pluginName);
            case "ItemsAdder", "Oraxen", "Nexo", "Slimefun" -> new CustomItemIntegration(pluginName);
            case "RoseStacker", "WildStacker", "AdvancedSpawners" -> new StackerIntegration(pluginName);
            case "LuckPerms" -> new LuckPermsIntegration();
            case "Plan" -> new PlanIntegration();
            default -> genericIntegration(pluginName);
        };
    }

    private static CloudIntegration genericIntegration(String pluginName) {
        Set<IntegrationCapability> capabilities = CloudIntegrationPolicy.requiresRuntimeAuthority(pluginName, false)
            ? Set.of(IntegrationCapability.DETECT, IntegrationCapability.RUNTIME_AUTHORITY)
            : Set.of(IntegrationCapability.DETECT);
        return new PolicyBackedCloudIntegration(pluginName, capabilities);
    }

    public record IntegrationStatus(
        String pluginName,
        String category,
        boolean enabled,
        boolean runtimeAuthorityRequired,
        List<String> requiredRuntimeClaims,
        Set<IntegrationCapability> capabilities
    ) {
        public IntegrationStatus {
            requiredRuntimeClaims = List.copyOf(requiredRuntimeClaims);
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }
}

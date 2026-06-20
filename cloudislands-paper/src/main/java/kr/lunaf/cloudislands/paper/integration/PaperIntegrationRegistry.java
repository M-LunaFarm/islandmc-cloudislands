package kr.lunaf.cloudislands.paper.integration;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import org.bukkit.Server;

public final class PaperIntegrationRegistry {
    private final Server server;

    private PaperIntegrationRegistry(Server server) {
        this.server = server;
    }

    public static PaperIntegrationRegistry discover(Server server) {
        return new PaperIntegrationRegistry(server);
    }

    public List<IntegrationStatus> snapshot() {
        List<IntegrationStatus> statuses = new ArrayList<>();
        for (String pluginName : CloudIntegrationPolicy.knownPlugins()) {
            statuses.add(status(pluginName));
        }
        return List.copyOf(statuses);
    }

    public IntegrationStatus status(String pluginName) {
        boolean enabled = server.getPluginManager().isPluginEnabled(pluginName);
        String category = CloudIntegrationPolicy.category(pluginName);
        return new IntegrationStatus(
            pluginName,
            category,
            enabled,
            CloudIntegrationPolicy.requiresRuntimeAuthority(pluginName, false),
            CloudIntegrationPolicy.requiredRuntimeClaims()
        );
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
                .append('\n');
        }
        return builder.toString();
    }

    public record IntegrationStatus(
        String pluginName,
        String category,
        boolean enabled,
        boolean runtimeAuthorityRequired,
        List<String> requiredRuntimeClaims
    ) {
        public IntegrationStatus {
            requiredRuntimeClaims = List.copyOf(requiredRuntimeClaims);
        }
    }
}

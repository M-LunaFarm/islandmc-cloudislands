package kr.lunaf.cloudislands.paper.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.economy.VaultIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.placeholder.PlaceholderApiIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.CloudIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;

public final class IntegrationRuntimeCertification {
    private static final List<String> PRIORITY_PLUGINS = List.of(
        "Vault",
        "LuckPerms",
        "PlaceholderAPI",
        "WorldEdit",
        "CoreProtect"
    );

    private IntegrationRuntimeCertification() {
    }

    public static List<String> priorityPlugins() {
        return PRIORITY_PLUGINS;
    }

    public static List<CertificationResult> certifyPriorityPlugins(IntegrationExternalRuntime runtime) {
        IntegrationExternalRuntime externalRuntime = runtime == null ? IntegrationExternalRuntime.noop() : runtime;
        return List.of(
            certify(new VaultIntegration(externalRuntime), Operation.ACTIVATE, context("vault:operation-smoke")),
            certify(new LuckPermsIntegration(externalRuntime), Operation.EXPORT, context("luckperms:permission-smoke")),
            certify(new PlaceholderApiIntegration(externalRuntime), Operation.ACTIVATE, context("placeholderapi:render-smoke")),
            certify(new WorldEditIntegration("WorldEdit", externalRuntime), Operation.EXPORT, context("worldedit:region-smoke")),
            certify(new CoreProtectIntegration(externalRuntime), Operation.EXPORT, context("coreprotect:audit-smoke"))
        );
    }

    private static CertificationResult certify(CloudIntegration integration, Operation operation, IntegrationContext context) {
        IntegrationResult result = switch (operation) {
            case ACTIVATE -> integration.onIslandActivate(context);
            case EXPORT -> integration.exportState(context);
        };
        IntegrationSupportState operationState = PaperIntegrationRegistry.operationState(result);
        return new CertificationResult(
            integration.pluginName(),
            operation.name(),
            operationState,
            result.status(),
            CloudIntegrationPolicy.requiresRuntimeAuthority(integration.pluginName(), false),
            CloudIntegrationPolicy.requiredRuntimeClaims(),
            result.details()
        );
    }

    private static IntegrationContext context(String idempotencyKey) {
        return new IntegrationContext(
            UUID.fromString("00000000-0000-0000-0000-000000000911"),
            "island-node-certification",
            911L,
            true,
            idempotencyKey,
            metadata()
        );
    }

    private static Map<String, String> metadata() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("world", "cloudislands_cert_world");
        metadata.put("cell", "4,-2");
        metadata.put("region", "64,0,-32..127,319,31");
        metadata.put("bundleKey", "certification/island-000000000911.tar.zst");
        metadata.put("activeOperationsDrained", "true");
        metadata.put("editSessionFlushed", "true");
        metadata.put("permissionNode", "cloudislands.island.member");
        metadata.put("bypassScope", "island");
        metadata.put("contextKey", "cloudislands:island");
        metadata.put("providerName", "Vault");
        metadata.put("currency", "coins");
        metadata.put("testAccount", "00000000-0000-0000-0000-000000000911");
        metadata.put("economyTransactionId", "vault-certification-000000000911");
        metadata.put("placeholderKeys", "%cloudislands_island_level%,%cloudislands_island_bank%");
        metadata.put("renderTarget", "certification-player");
        return Map.copyOf(metadata);
    }

    private enum Operation {
        ACTIVATE,
        EXPORT
    }

    public record CertificationResult(
        String pluginName,
        String operation,
        IntegrationSupportState operationState,
        IntegrationResult.Status resultStatus,
        boolean runtimeAuthorityRequired,
        List<String> requiredRuntimeClaims,
        Map<String, String> details
    ) {
        public boolean certified() {
            return operationState == IntegrationSupportState.OPERATION_SUCCEEDED && resultStatus == IntegrationResult.Status.SUCCESS;
        }
    }
}

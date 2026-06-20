package kr.lunaf.cloudislands.common.integration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CloudIntegrationPolicy {
    public static final String DISTRIBUTED_HOOK_POLICY =
        "paper-hooks-must-tag-island-uuid-runtime-fencing-token-node-id-and-node-ownership-before-core-state-changes";
    public static final String COREPROTECT_POLICY =
        "coreprotect-queries-and-rollbacks-are-scoped-by-island-region-and-audit-logged-with-island-uuid";
    public static final String WORLDEDIT_POLICY =
        "worldedit-and-fawe-operations-require-active-node-ownership-and-runtime-fencing-before-template-or-repair-writes";
    public static final String CUSTOM_ITEM_POLICY =
        "custom-item-values-and-limits-map-external-block-ids-to-core-block-value-and-limit-keys";
    public static final String STACKER_POLICY =
        "stacker-and-spawner-hooks-report-effective-entity-and-spawner-counts-to-core-limit-and-worth-surfaces";
    public static final String PERMISSION_POLICY =
        "luckperms-hook-may-map-network-permissions-to-admin-bypass-but-core-island-roles-remain-authoritative";
    public static final String ACTIVITY_POLICY =
        "plan-and-vanish-hooks-influence-presence-and-analytics-only-not-core-membership-authority";
    public static final String ECONOMY_POLICY =
        "economy-and-shop-hooks-use-idempotent-core-bank-and-worth-operations";

    private static final List<String> KNOWN_PLUGINS = List.of(
        "Vault",
        "PlaceholderAPI",
        "LuckPerms",
        "CoreProtect",
        "WorldEdit",
        "FastAsyncWorldEdit",
        "ItemsAdder",
        "Oraxen",
        "Nexo",
        "RoseStacker",
        "WildStacker",
        "AdvancedSpawners",
        "Plan",
        "ProtocolLib",
        "SkinsRestorer",
        "SuperVanish",
        "PremiumVanish",
        "SlimeWorldManager",
        "Slimefun",
        "CMI"
    );

    private static final List<String> REQUIRED_RUNTIME_CLAIMS = List.of(
        "island-uuid",
        "node-id",
        "runtime-fencing-token",
        "node-ownership",
        "core-idempotency-key"
    );

    private static final Set<String> CORE_STATE_CHANGING_CATEGORIES = Set.of(
        "audit-rollback",
        "world-edit",
        "custom-items",
        "stacker",
        "spawner",
        "economy"
    );

    private static final Map<String, String> CATEGORIES = Map.ofEntries(
        Map.entry("Vault", "economy"),
        Map.entry("PlaceholderAPI", "placeholder"),
        Map.entry("LuckPerms", "permission"),
        Map.entry("CoreProtect", "audit-rollback"),
        Map.entry("WorldEdit", "world-edit"),
        Map.entry("FastAsyncWorldEdit", "world-edit"),
        Map.entry("ItemsAdder", "custom-items"),
        Map.entry("Oraxen", "custom-items"),
        Map.entry("Nexo", "custom-items"),
        Map.entry("RoseStacker", "stacker"),
        Map.entry("WildStacker", "stacker"),
        Map.entry("AdvancedSpawners", "spawner"),
        Map.entry("Plan", "analytics"),
        Map.entry("ProtocolLib", "protocol"),
        Map.entry("SkinsRestorer", "identity"),
        Map.entry("SuperVanish", "presence"),
        Map.entry("PremiumVanish", "presence"),
        Map.entry("SlimeWorldManager", "world-storage"),
        Map.entry("Slimefun", "custom-items"),
        Map.entry("CMI", "server-tools")
    );

    private CloudIntegrationPolicy() {
    }

    public static List<String> knownPlugins() {
        return KNOWN_PLUGINS;
    }

    public static boolean knownPlugin(String pluginName) {
        return pluginName != null && KNOWN_PLUGINS.contains(pluginName);
    }

    public static String category(String pluginName) {
        return pluginName == null ? "" : CATEGORIES.getOrDefault(pluginName, "unknown");
    }

    public static List<String> requiredRuntimeClaims() {
        return REQUIRED_RUNTIME_CLAIMS;
    }

    public static boolean requiresRuntimeAuthority(String pluginName, boolean coreStateMutation) {
        return coreStateMutation || CORE_STATE_CHANGING_CATEGORIES.contains(category(pluginName));
    }

    public static HookDecision validateHookContext(HookContext context) {
        if (context == null) {
            return HookDecision.deny(List.of("context-missing"));
        }
        List<String> violations = new java.util.ArrayList<>();
        if (!knownPlugin(context.pluginName())) {
            violations.add("plugin-unknown");
        }
        if (requiresRuntimeAuthority(context.pluginName(), context.coreStateMutation())) {
            if (context.islandId() == null) {
                violations.add("island-uuid-missing");
            }
            if (blank(context.nodeId())) {
                violations.add("node-id-missing");
            }
            if (context.fencingToken() <= 0L) {
                violations.add("runtime-fencing-token-missing");
            }
            if (!context.nodeOwnsIsland()) {
                violations.add("node-ownership-missing");
            }
            if (blank(context.idempotencyKey())) {
                violations.add("core-idempotency-key-missing");
            }
        }
        return violations.isEmpty() ? HookDecision.allow() : HookDecision.deny(violations);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record HookContext(
        String pluginName,
        UUID islandId,
        String nodeId,
        long fencingToken,
        boolean nodeOwnsIsland,
        String idempotencyKey,
        boolean coreStateMutation
    ) {}

    public record HookDecision(boolean allowed, List<String> violations) {
        static HookDecision allow() {
            return new HookDecision(true, List.of());
        }

        static HookDecision deny(List<String> violations) {
            return new HookDecision(false, List.copyOf(violations));
        }
    }
}

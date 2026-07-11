package kr.seungmin.satisskyfactory.command;

import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.config.SatisFeatureGateResolver;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AdminFactoryCommand {
    private static final List<String> FEATURE_KEYS = SatisFeatureGateResolver.featureKeys();
    private static final List<String> HELP_COMMANDS = helpCommands();

    private static List<String> helpCommands() {
        List<String> commands = new ArrayList<>(List.of(
                "factory admin help [page]",
                "factory admin list [page]",
                "factory admin command list [page]",
                "factory admin reload",
                "factory admin features",
                "factory admin integration",
                "factory admin doctor",
                "factory admin database",
                "factory admin runtime",
                "factory admin routes",
                "factory admin support"
        ));
        commands.addAll(List.of(
                "factory admin state",
                "factory admin give <player> <machineType> [amount]",
                "factory admin giveitem <player> <itemId> <amount>",
                "factory admin addresearch <player> <amount>",
                "factory admin setdebt <player> <amount>",
                "factory admin charge <player>",
                "factory admin gennodes <player>",
                "factory admin debug island",
                "factory admin debug networks",
                "factory admin removehere",
                "factory admin repairhere"
        ));
        return List.copyOf(commands);
    }
    private final FactoryIslandService islands;
    private final MessageService messages;
    private final DatabaseService database;
    private final Predicate<String> featureEnabled;
    private final Supplier<Map<String, String>> integrationMetadata;
    private final Supplier<Map<String, String>> addonState;
    private final Function<UUID, Map<String, String>> addonIslandState;
    private final Runnable reload;
    private final int commandListPageSize;
    private final AdminDiagnosticCommands diagnostics;
    private final AdminGiveCommands giveCommands;
    private final AdminIslandOperations islandOperations;
    private final AdminMachineCommands machineCommands;

    public AdminFactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                               StorageService storage, ResourceNodeService nodes, SkyblockProvider skyblock,
                               MaintenanceService maintenance, ResearchService research, PowerNetworkService power,
                               CustomItemFactory itemFactory, ItemRegistry items,
                               MessageService messages, DatabaseService database, Predicate<String> featureEnabled,
                               Supplier<Map<String, String>> integrationMetadata,
                               Supplier<Map<String, String>> addonState,
                               Function<UUID, Map<String, String>> addonIslandState,
                               int commandListPageSize,
                               Runnable reload) {
        this.islands = islands;
        this.messages = messages;
        this.database = database;
        this.featureEnabled = featureEnabled;
        this.integrationMetadata = integrationMetadata;
        this.addonState = addonState;
        this.addonIslandState = addonIslandState;
        this.commandListPageSize = Math.max(1, commandListPageSize);
        this.reload = reload;
        this.diagnostics = new AdminDiagnosticCommands(messages, database, integrationMetadata, addonState);
        this.giveCommands = new AdminGiveCommands(islands, definitions, storage, itemFactory, items, messages, featureEnabled);
        this.islandOperations = new AdminIslandOperations(islands, nodes, skyblock, maintenance, research, messages, featureEnabled);
        this.machineCommands = new AdminMachineCommands(islands, machines, power, messages, featureEnabled);
    }

    public boolean execute(CommandSender sender, String[] args) {
        return execute(sender, args, "factory");
    }

    public boolean execute(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            if (!sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            help(sender, label, 1);
            return true;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (subcommand.equals("debug")) {
            if (!sender.hasPermission("satisskyfactory.admin.debug") && !sender.hasPermission("satisskyfactory.debug") && !sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
        } else if (!hasAdminPermission(sender, subcommand)) {
            messages.send(sender, "no-permission");
            return true;
        }
        String disabledFeature = disabledFeatureFor(subcommand);
        if (disabledFeature != null) {
            messages.send(sender, "feature-disabled", Map.of("feature", disabledFeature));
            return true;
        }
        switch (subcommand) {
            case "help", "list", "commands", "command", "command-list", "명령어", "명령어목록" -> help(sender, label, helpPage(args));
            case "reload" -> {
                reload.run();
                messages.send(sender, "reloaded");
            }
            case "features" -> showFeatures(sender);
            case "integration" -> showIntegration(sender);
            case "doctor" -> diagnostics.showDoctor(sender);
            case "database" -> diagnostics.showDatabase(sender);
            case "runtime" -> diagnostics.showRuntime(sender);
            case "routes" -> diagnostics.showRoutes(sender);
            case "support" -> diagnostics.showSupport(sender);
            case "state" -> {
                if (requireFeature(sender, "addon-state")) {
                    showAddonState(sender);
                }
            }
            case "give" -> {
                if (requireFeature(sender, "machines")) {
                    giveCommands.giveMachine(sender, args);
                }
            }
            case "giveitem" -> {
                if (requireFeature(sender, "machines")) {
                    giveCommands.giveItem(sender, args);
                }
            }
            case "addresearch" -> islandOperations.addResearch(sender, args);
            case "setdebt" -> islandOperations.setDebt(sender, args);
            case "charge" -> islandOperations.charge(sender, args);
            case "gennodes" -> islandOperations.generateNodes(sender, args);
            case "debug" -> machineCommands.debug(sender, args);
            case "removehere" -> {
                if (requireFeature(sender, "machines")) {
                    machineCommands.removeHere(sender);
                }
            }
            case "repairhere" -> {
                if (requireFeature(sender, "maintenance")) {
                    machineCommands.repairHere(sender);
                }
            }
            default -> messages.send(sender, "unknown-admin-command");
        }
        return true;
    }

    private boolean hasAdminPermission(CommandSender sender, String subcommand) {
        if (sender == null) {
            return false;
        }
        if (sender.hasPermission("satisskyfactory.admin")) {
            return true;
        }
        return sender.hasPermission(adminPermission(subcommand));
    }

    private String adminPermission(String subcommand) {
        return switch (subcommand) {
            case "reload" -> "satisskyfactory.admin.reload";
            case "features" -> "satisskyfactory.admin.features";
            case "integration" -> "satisskyfactory.admin.integration";
            case "doctor" -> "satisskyfactory.admin.doctor";
            case "database" -> "satisskyfactory.admin.database";
            case "runtime" -> "satisskyfactory.admin.runtime";
            case "routes" -> "satisskyfactory.admin.routes";
            case "support" -> "satisskyfactory.admin.support";
            case "state" -> "satisskyfactory.admin.state";
            case "give" -> "satisskyfactory.admin.give";
            case "giveitem" -> "satisskyfactory.admin.giveitem";
            case "addresearch" -> "satisskyfactory.admin.research";
            case "setdebt", "charge", "repairhere" -> "satisskyfactory.admin.maintenance";
            case "gennodes" -> "satisskyfactory.admin.nodes";
            case "debug" -> "satisskyfactory.admin.debug";
            default -> "satisskyfactory.admin";
        };
    }

    private String disabledFeatureFor(String subcommand) {
        return switch (subcommand) {
            case "state" -> enabled("addon-state") ? null : "addon-state";
            case "give", "giveitem", "removehere" -> enabled("machines") ? null : "machines";
            case "addresearch" -> enabled("research") ? null : "research";
            case "setdebt", "charge" -> enabled("maintenance") ? null : "maintenance";
            case "repairhere" -> !enabled("maintenance") ? "maintenance" : (!enabled("machines") ? "machines" : null);
            case "gennodes" -> enabled("resource-nodes") ? null : "resource-nodes";
            case "debug" -> debugCommandsVisible() ? null : "debug";
            default -> null;
        };
    }


    private boolean debugCommandsVisible() {
        return enabled("machines")
                || enabled("storage")
                || enabled("resource-nodes")
                || enabled("maintenance")
                || enabled("addon-state");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> values = new ArrayList<>();
            values.add("reload");
            values.add("help");
            values.add("list");
            values.add("commands");
            values.add("command");
            values.add("command-list");
            values.add("명령어");
            values.add("명령어목록");
            if (debugCommandsVisible()) {
                values.add("debug");
            }
            values.add("features");
            values.add("integration");
            values.add("doctor");
            values.add("database");
            values.add("runtime");
            values.add("routes");
            values.add("support");
            if (enabled("addon-state")) {
                values.add("state");
            }
            if (enabled("machines")) {
                values.add("give");
                values.add("giveitem");
                values.add("removehere");
            }
            if (enabled("research")) {
                values.add("addresearch");
            }
            if (enabled("maintenance")) {
                values.add("setdebt");
                values.add("charge");
                if (enabled("machines")) {
                    values.add("repairhere");
                }
            }
            if (enabled("resource-nodes")) {
                values.add("gennodes");
            }
            if (sender != null) {
                values = values.stream().filter(value -> hasAdminPermission(sender, value)).toList();
            }
            return filter(values, args[1]);
        }
        if (args.length == 3 && isCommandListRoot(args[1])) {
            List<String> values = new ArrayList<>();
            values.add("list");
            values.addAll(helpPageSuggestions());
            return filter(values, args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("help")) {
            return filter(helpPageSuggestions(), args[2]);
        }
        if (args.length == 4 && isCommandListRoot(args[1]) && (args[2].equalsIgnoreCase("list") || args[2].equals("목록"))) {
            return filter(helpPageSuggestions(), args[3]);
        }
        if ((args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem") || args[1].equalsIgnoreCase("removehere")) && !enabled("machines")) {
            return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("addresearch") && !enabled("research")) {
            return new ArrayList<>();
        }
        if ((args[1].equalsIgnoreCase("setdebt") || args[1].equalsIgnoreCase("charge") || args[1].equalsIgnoreCase("repairhere")) && !enabled("maintenance")) {
            return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("repairhere") && !enabled("machines")) {
            return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("gennodes") && !enabled("resource-nodes")) {
            return new ArrayList<>();
        }
        if (args.length == 3 && needsPlayer(args[1])) {
            return filter(onlinePlayerNames(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            return filter(giveCommands.machineTypeIds(), args[3]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("giveitem")) {
            return filter(giveCommands.itemIds(), args[3]);
        }
        if (args.length == 4 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem")
                || args[1].equalsIgnoreCase("addresearch") || args[1].equalsIgnoreCase("setdebt"))) {
            return filter(amountSuggestions(), args[3]);
        }
        if (args.length == 5 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem"))) {
            return filter(amountSuggestions(), args[4]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("debug")) {
            return filter(debugTargets(), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> helpPageSuggestions() {
        int maxPage = CommandListPolicy.pages(visibleHelpCommands("factory").size(), commandListPageSize);
        List<String> values = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            values.add(String.valueOf(page));
        }
        return values;
    }

    private List<String> debugTargets() {
        List<String> values = new ArrayList<>();
        values.add("island");
        if (enabled("machines")) {
            values.add("networks");
        }
        return values;
    }

    private boolean requireFeature(CommandSender sender, String feature) {
        if (enabled(feature)) {
            return true;
        }
        messages.send(sender, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private boolean enabled(String feature) {
        if (featureEnabled == null) {
            return true;
        }
        try {
            return featureEnabled.test(feature);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void showFeatures(CommandSender sender) {
        messages.sendRaw(sender, "admin-features-title");
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-catalog", "value", SatisFeatureGateResolver.featureKeysMetadata()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-root-gates", "value", SatisFeatureGateResolver.rootGateMetadata()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-config-roots", "value", SatisFeatureGateResolver.featureRootMetadata()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-aliases", "value", SatisFeatureGateResolver.aliasMetadata()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-dependencies", "value", SatisFeatureGateResolver.dependencyMetadata()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-config-gate-policy", "value", SatisFeatureGateResolver.configGatePolicy()));
        messages.sendRaw(sender, "admin-integration-entry", Map.of("key", "feature-disable-policy", "value", SatisFeatureGateResolver.disablePolicy()));
        for (String feature : FEATURE_KEYS) {
            messages.sendRaw(sender, "admin-features-entry", Map.of(
                    "feature", feature,
                    "enabled", Boolean.toString(enabled(feature))
            ));
        }
        Map<String, String> details = featureDetails();
        List.of(
                "configured-features",
                "effective-features",
                "operational-features",
                "dependency-disabled-features",
                "runtime-feature-dependency-policy",
                "runtime-feature-dependency-blocks",
                "feature-warnings",
                "last-core-hydrate-status",
                "last-core-hydrate-island",
                "last-core-hydrate-key",
                "last-core-hydrate-at",
                "core-hydrated-activation-count",
                "last-node-id",
                "last-node-state",
                "last-node-operation",
                "last-node-reason",
                "last-node-recovery-required",
                "last-node-cleared-sessions",
                "last-node-cleared-tickets",
                "last-node-at",
                "last-lifecycle-source-node",
                "last-lifecycle-target-node",
                "last-lifecycle-node-move",
                "last-lifecycle-node-move-policy",
                "island-state-node-move-state-keys",
                "last-core-bulk-publish-pending-retries",
                "last-core-global-bulk-publish-pending-retries",
                "addon-state-sync-island-bulk-retries-queued",
                "addon-state-sync-global-bulk-retries-queued",
                "addon-state-sync-island-bulk-retries-drained",
                "addon-state-sync-global-bulk-retries-drained",
                "write-gate-machines",
                "write-gate-storage",
                "write-gate-resource-nodes",
                "write-gate-market",
                "write-gate-contracts",
                "write-gate-research",
                "write-gate-members",
                "write-gate-permissions",
                "write-gate-level-values",
                "write-gate-warps",
                "write-gate-biomes",
                "write-gate-chat",
                "write-gate-templates",
                "runtime-biomes-status",
                "runtime-chat-status",
                "runtime-templates-status",
                "write-gate-lifecycle-subfeatures",
                "runtime-commands-status",
                "runtime-gui-status",
                "runtime-placeholder-status",
                "runtime-placeholder-exposure-policy",
                "runtime-placeholder-exposed-keys",
                "runtime-placeholder-denied-internal-fields",
                "runtime-placeholder-topology-privacy-policy",
                "runtime-placeholder-internal-placement-exposure"
        ).forEach(key -> {
            String value = details.get(key);
            if (value != null && !value.isBlank()) {
                messages.sendRaw(sender, "admin-integration-entry", Map.of(
                        "key", key,
                        "value", value
                ));
            }
        });
    }

    private Map<String, String> featureDetails() {
        try {
            Map<String, String> state = addonState == null ? Map.of() : addonState.get();
            if (state != null && (!blank(state.get("configured-features")) || !blank(state.get("effective-features")) || !blank(state.get("operational-features")) || !blank(state.get("feature-warnings")))) {
                return state;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Map<String, String> metadata = integrationMetadata == null ? Map.of() : integrationMetadata.get();
            return metadata == null ? Map.of() : metadata;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void showIntegration(CommandSender sender) {
        messages.sendRaw(sender, "admin-integration-title");
        Map<String, String> metadata;
        try {
            metadata = integrationMetadata == null ? Map.of() : integrationMetadata.get();
        } catch (RuntimeException exception) {
            metadata = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
        }
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> messages.sendRaw(sender, "admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                )));
    }

    private void showAddonState(CommandSender sender) {
        messages.sendRaw(sender, "admin-integration-title");
        Map<String, String> state;
        try {
            state = addonState == null ? Map.of() : addonState.get();
        } catch (RuntimeException exception) {
            state = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
        }
        Map<String, String> visible = new LinkedHashMap<>(state);
        if (integrationMetadata != null) {
            try {
                Map<String, String> metadata = integrationMetadata.get();
                List.of(
                        "addon-descriptor-resource",
                        "addon-descriptor-format",
                        "addon-packaging",
                        "addon-removal-safe",
                        "addon-removal-core-impact",
                        "addon-removal-runtime-action",
                        "cloudislands-lifecycle-depends-on-satis",
                        "addon-data-retention",
                        "addon-runtime-owns-islands",
                        "addon-default-database-mode",
                        "database-configured-backend",
                        "database-setup-sections",
                        "database-setup-choices",
                        "database-setup-shared-choices",
                        "database-setup-local-choices",
                        "database-setup-local-warning",
                        "database-setup-multi-node-safe",
                        "database-setup-auto-selected",
                        "database-setup-selected-backend",
                        "database-setup-selected-source",
                        "database-setup-warning",
                        "database-setup-jdbc-aliases",
                        "database-setup-selection-policy",
                        "database-setup-backend-priority",
                        "database-setup-source-precedence",
                        "database-setup-core-api-readiness-fields",
                        "database-setup-postgresql-readiness-fields",
                        "database-setup-mysql-readiness-fields",
                        "database-setup-mariadb-readiness-fields",
                        "database-setup-sqlite-readiness-fields",
                        "database-setup-jdbc-readiness-policy",
                        "database-setup-core-api-local-cache-write-policy",
            "database-setup-readiness-state-keys",
                        "database-setup-source-policy",
                        "database-setup-path",
                        "database-setup-fallback-precedence",
                        "database-setup-core-api-fallback",
                        "database-jdbc-inferred",
                        "database-jdbc-inferred-backend",
                        "database-active-backend",
                        "database-cache-backend",
                        "database-cache-description",
                        "database-active-shared",
                        "database-active-authority",
                        "database-core-api-authority-status",
                        "database-core-api-authority-ready",
                        "database-core-api-local-cache-writes-enabled",
                        "database-core-api-local-cache-write-policy",
                        "database-configured-backend-active",
                        "database-effective-backend-status",
                        "database-attempted-backends",
                        "database-attempt-order",
                        "database-jdbc-target",
                        "database-fallback-jdbc-target",
                        "database-fallback-jdbc-targets",
                        "database-supported-backends",
                        "database-fallback-reason",
                        "database-fallback-active",
                        "database-fallback-status",
                        "database-fallback-enabled",
                        "database-fallback-order",
                        "database-fallback-effective-order",
                        "database-fallback-configured-order",
                        "database-fallback-order-policy",
                        "database-fallback-shared-backends",
                        "database-fallback-ready-backends",
                        "database-fallback-ready-chain",
                        "database-fallback-not-ready-backends",
                        "database-fallback-readiness-summary",
            "database-fallback-operator-remediation",
                        "database-fallback-ready-chain-risk",
                        "database-fallback-ready-chain-production-safe",
                        "database-fallback-first-shared-backend",
                        "database-fallback-local-position",
                        "database-fallback-shared-safe",
                        "database-fallback-risk",
                        "database-fallback-production-safe",
                        "database-fallback-warning",
                        "database-fallback-authority",
                        "database-fallback-split-brain-risk",
                        "database-fallback-read-write-policy",
                        "database-fallback-chain-policy",
                        "database-fallback-readiness-policy",
                        "database-fallback-ready-chain-policy",
                        "database-setup-selected-source",
                        "database-setup-path",
                        "database-config-source",
                        "database-shared-state-safe-backends",
                        "database-local-fallback-backend",
                        "database-recommended-fallback-order",
                        "database-multi-node-warning",
                        "database-core-api-marker",
                        "database-core-api-available",
                        "database-core-api-requires",
                        "database-core-api-mode",
                        "database-core-api-endpoint",
                        "database-core-api-island-endpoint",
                        "database-core-api-local-cache",
                        "database-node-local-cache-active",
                        "database-core-api-fallback-target",
                        "database-core-api-fallback-target-ready",
                        "database-core-api-fallback-policy",
                        "database-core-api-fallback-active",
                        "database-core-api-fallback-reason",
                        "database-core-api-flattened-fallback-enabled",
                        "database-core-api-write-fallback",
                        "database-core-api-write-fallback-policy",
                        "database-config-env",
                        "database-jdbc-source",
                        "database-jdbc-env",
                        "database-credentials-source",
                        "database-credentials-env",
                        "database-pool-source",
                        "database-pool-env",
                        "database-fallback-source",
                        "database-fallback-env",
                        "database-scope",
                        "database-shared",
                        "runtime-addon-gate",
                        "runtime-addon-status",
                        "runtime-feature-pack-activation-policy",
                        "runtime-feature-pack-activation-mode",
                        "runtime-feature-pack-runtime-enabled",
                        "runtime-feature-pack-runtime-shape",
                        "runtime-feature-pack-block-reason",
                        "runtime-disable-activation-block-reason",
                        "last-preflush-activation-block-reason",
                        "preflush-activation-block-reason",
                        "runtime-addon-policy",
                        "runtime-cloudislands-api-required",
                        "runtime-standalone-island-management",
                        "runtime-standalone-island-runtime-policy",
                        "runtime-island-runtime-authority",
                        "runtime-skyblock-provider-policy",
                        "runtime-cloudislands-api-surface-policy",
                        "runtime-cloudislands-direct-access-policy",
                        "runtime-cloudislands-forbidden-direct-access-targets",
                        "runtime-cloudislands-core-internal-access",
                        "runtime-tick-authority-policy",
                        "runtime-tick-authority-core-api-policy",
                        "runtime-tick-authority-shared-sql-policy",
                        "runtime-tick-authority-local-fallback-policy",
                        "runtime-tick-authority-core-hydrated-islands",
                        "runtime-write-authority-policy",
                        "runtime-write-authority-local-fallback-policy",
                        "runtime-authoritative-store-policy",
                        "runtime-redis-advisory-policy",
                        "runtime-redis-failure-policy",
                        "runtime-addon-state-gate",
                        "runtime-addon-state-status",
                        "runtime-addon-state-policy",
                        "runtime-route-events-gate",
                        "runtime-route-events-status",
                        "runtime-route-events-policy",
                "runtime-route-authority-policy",
                "runtime-route-ticket-privacy-policy",
                "runtime-player-surface-policy",
                "runtime-player-surface-hide-policy",
                "runtime-player-surface-command-owner-policy",
                "runtime-velocity-forwarding-policy",
                        "runtime-paper-backend-access-policy",
                        "runtime-plugin-message-security-policy",
                        "runtime-route-events-handled",
                        "runtime-route-events-blocked",
                        "runtime-route-events-publish-failures",
                        "runtime-route-events-last-block-reason",
                        "runtime-commands-registered",
                        "runtime-commands-gate",
                        "runtime-commands-status",
                        "runtime-commands-policy",
                        "runtime-machine-listener-registered",
                        "runtime-gui-listener-registered",
                        "runtime-lifecycle-listener-registered",
                        "runtime-placeholder-registered",
                        "runtime-placeholder-gate",
                        "runtime-placeholder-status",
                        "runtime-placeholder-policy",
                        "runtime-placeholder-exposure-policy",
                        "runtime-placeholder-exposed-keys",
                        "runtime-placeholder-denied-internal-fields",
                        "runtime-placeholder-topology-privacy-policy",
                        "runtime-placeholder-internal-placement-exposure",
                        "placeholder-exposure-policy",
                        "placeholder-exposed-keys",
                        "placeholder-denied-internal-fields",
                        "placeholder-internal-placement-exposure",
                        "runtime-topology-privacy-policy",
                        "runtime-player-visible-topology-policy",
                        "runtime-internal-topology-fields",
                "route-authority-policy",
                "route-ticket-privacy-policy",
                "player-surface-policy",
                "player-surface-hide-policy",
                "player-surface-command-owner-policy",
                "velocity-forwarding-policy",
                        "paper-backend-access-policy",
                        "plugin-message-security-policy",
                        "runtime-storage-gate",
                        "runtime-storage-status",
                        "runtime-storage-policy",
                        "runtime-storage-save-result-policy",
                        "runtime-market-gate",
                        "runtime-market-status",
                        "runtime-market-policy",
                        "runtime-market-storage-save-policy",
                        "runtime-contracts-gate",
                        "runtime-contracts-status",
                        "runtime-contracts-policy",
                        "runtime-contract-storage-save-policy",
                        "runtime-research-gate",
                        "runtime-research-status",
                        "runtime-research-policy",
                        "runtime-research-unlock-save-policy",
                        "runtime-admin-research-save-policy",
                        "runtime-machines-gate",
                        "runtime-machines-status",
                        "runtime-machines-policy",
                        "runtime-machine-gui-service-policy",
                        "runtime-machine-gui-storage-action-policy",
                        "runtime-machine-create-storage-save-policy",
                        "runtime-admin-machine-helper-policy",
                        "runtime-machine-remove-write-policy",
                        "runtime-machine-remove-storage-save-policy",
                        "runtime-machine-break-storage-gate",
                        "runtime-machine-break-policy",
                        "runtime-admin-virtual-item-storage-gate",
                        "runtime-admin-virtual-item-save-policy",
                        "runtime-player-storage-command-service-gate",
                        "runtime-player-market-command-service-gate",
                        "runtime-gui-market-save-policy",
                        "runtime-player-repair-command-service-gate",
                        "runtime-player-repair-storage-save-policy",
                        "runtime-resource-nodes-gate",
                        "runtime-resource-nodes-status",
                        "runtime-resource-nodes-policy",
                        "runtime-gui-gate",
                        "runtime-gui-status",
                        "runtime-gui-policy",
                        "runtime-gui-service-policy",
                        "runtime-menus-gate",
                        "runtime-menus-status",
                        "runtime-lifecycle-gate",
                        "runtime-lifecycle-status",
                        "runtime-lifecycle-policy",
                        "runtime-maintenance-gate",
                        "runtime-maintenance-status",
                        "runtime-maintenance-policy",
                        "runtime-admin-maintenance-save-policy",
                        "runtime-factories-gate",
                        "runtime-factories-status",
                        "runtime-generators-gate",
                        "runtime-generators-status",
                        "runtime-upgrades-gate",
                        "runtime-upgrades-status",
                        "runtime-missions-gate",
                        "runtime-missions-status",
                        "runtime-alias-policy",
                        "runtime-machine-ticker-running",
                        "runtime-maintenance-ticker-running",
                        "dirty-save-state-keys",
                        "addon-removal-dirty-save-detach-policy",
                        "addon-removal-dirty-save-reattach-policy",
                        "addon-reload-runtime-restart-policy",
                        "addon-core-refresh-reapply-policy",
                        "runtime-core-refresh-reapply-policy",
                        "core-refresh-reapply-state-keys",
                        "last-core-refresh-reason",
                        "last-core-refresh-result",
                        "last-core-refresh-policy",
                        "last-core-refresh-database-open",
                        "last-core-refresh-runtime-enabled",
                        "last-core-refresh-at",
                        "runtime-dirty-save-running",
                        "runtime-dirty-save-pending-writes",
                        "runtime-dirty-save-pending-machines",
                        "runtime-dirty-save-pending-inventories",
                        "runtime-dirty-save-pending-nodes",
                        "runtime-dirty-save-pending-islands",
                        "runtime-machine-tick-fuel-save-policy",
                        "runtime-machine-tick-harvest-save-policy",
                        "runtime-machine-tick-planter-save-policy",
                        "runtime-machine-tick-fertilizer-save-policy",
                        "runtime-machine-tick-quality-bonus-save-policy",
                        "runtime-machine-tick-node-producer-save-policy",
                        "runtime-machine-tick-recipe-save-policy",
                        "runtime-machine-tick-logistics-save-policy",
                        "runtime-power-battery-save-policy",
                        "runtime-island-save-result-policy",
                        "runtime-admin-island-save-policy",
                        "runtime-player-maintenance-status-save-policy",
                        "runtime-gui-maintenance-status-save-policy",
                        "runtime-machine-save-result-policy",
                        "runtime-machine-placement-link-save-policy",
                        "runtime-research-unlock-save-policy",
                        "runtime-contract-emergency-usage-save-policy",
                        "runtime-contract-island-reward-save-policy",
                        "runtime-market-debt-save-policy",
                        "runtime-island-create-save-policy",
                        "runtime-emergency-contract-command-save-policy",
                        "runtime-admin-maintenance-charge-save-policy",
                        "runtime-maintenance-tick-save-policy",
                        "runtime-lifecycle-island-save-policy",
                        "runtime-machine-placement-island-save-policy",
                        "runtime-player-maintenance-charge-save-policy",
                        "runtime-core-lifecycle-save-policy",
                        "runtime-machine-save-later-policy",
                        "runtime-power-network-machine-save-policy",
                        "runtime-item-network-machine-save-policy",
                        "runtime-machine-region-remap-save-policy",
                        "runtime-machine-tick-save-policy",
                        "runtime-inventory-cache-write-policy",
                        "runtime-generator-fuel-save-policy",
                        "runtime-contract-inventory-rollback-policy",
                        "runtime-market-inventory-rollback-policy",
                        "runtime-player-return-inventory-save-policy",
                        "runtime-logistics-inventory-rollback-policy",
                        "runtime-recipe-inventory-rollback-policy",
                        "runtime-node-producer-rollback-policy",
                        "runtime-power-battery-inventory-rollback-policy",
                        "runtime-generator-fuel-rollback-policy",
                        "runtime-harvester-output-full-save-policy",
                        "runtime-machine-flush-inventory-rollback-policy",
                        "runtime-machine-clear-inventory-rollback-policy",
                        "runtime-machine-delete-inventory-policy",
                        "runtime-machine-delete-inventory-rollback-policy",
                        "runtime-machine-create-cleanup-policy",
                        "runtime-inventory-delete-gate-policy",
                        "runtime-inventory-save-now-policy",
                        "runtime-inventory-save-api-policy",
                        "runtime-island-storage-create-policy",
                        "runtime-island-storage-optional-create-policy",
                        "runtime-player-command-storage-create-policy",
                        "runtime-gui-storage-create-policy",
                        "runtime-market-storage-create-policy",
                        "runtime-contract-storage-create-policy",
                        "runtime-power-storage-create-policy",
                        "runtime-machine-flush-storage-create-policy",
                        "runtime-direct-island-storage-create-policy",
                        "runtime-dirty-save-last-flush-status",
                        "runtime-dirty-save-last-flush-at",
                        "runtime-dirty-save-last-flush-writes",
                        "runtime-dirty-save-last-flush-failures",
                        "runtime-dirty-save-flush-attempts",
                        "runtime-machine-ticker-gate",
                        "runtime-maintenance-ticker-gate",
                        "runtime-dirty-save-gate",
                        "runtime-dirty-save-stop-policy",
                        "runtime-duplicate-tick-guard",
                        "runtime-core-api-state-writer",
                        "runtime-core-api-state-bulk-writer",
                        "runtime-core-api-state-writer-gate",
                        "runtime-core-api-state-writer-block-reason",
                        "runtime-core-api-state-readiness",
                        "runtime-core-api-state-transport",
                        "runtime-core-api-state-fallback-policy",
                        "runtime-core-api-state-flattened-fallback-enabled",
                        "runtime-core-api-state-pending-retries",
                        "runtime-core-api-state-failures",
                        "runtime-core-api-state-last-failure",
                        "runtime-core-api-state-last-failure-at",
                        "runtime-command-handler-mode",
                        "runtime-command-block-reason",
                        "runtime-active-components",
                        "runtime-skipped-components",
                        "runtime-blocked-components",
                        "runtime-feature-block-reasons",
                        "runtime-component-audit",
                        "runtime-disabled-component-policy",
                        "runtime-addon-removal-safe",
                        "runtime-addon-removal-policy",
                        "runtime-addon-removal-core-impact",
                        "runtime-addon-removal-action",
                        "runtime-addon-removal-data-retention",
                        "runtime-addon-reenable-policy",
                        "runtime-addon-disable-preflush-policy",
                        "runtime-cloudislands-lifecycle-depends-on-satis",
                        "runtime-readonly-command-write-policy",
                        "legacy-satismc-import-status",
                        "legacy-satismc-import-scan",
                        "legacy-satismc-import-dryrun",
                        "legacy-satismc-import-verify",
                        "legacy-satismc-import-import",
                        "legacy-satismc-import-approval-token",
                        "legacy-satismc-import-approval-fingerprint-token",
                        "legacy-satismc-import-read-only-actions",
                        "legacy-satismc-import-write-actions",
                        "legacy-satismc-import-mode",
                        "legacy-satismc-import-conflict-policy",
                        "legacy-satismc-rollback-mode",
                        "legacy-satismc-rollback-command",
                        "command-list-format",
                        "command-list-header-suffix",
                        "command-list-entry-prefix",
                        "command-list-navigation-policy",
                        "command-list-paging",
                        "command-list-page-size",
                        "command-list-disabled-policy",
                        "data-write-mode",
                        "write-gate-machines",
                        "write-gate-machines-direct",
                        "write-gate-machine-ticker-storage",
                        "write-gate-item-networks-direct",
                        "write-gate-power-direct",
                        "write-gate-storage",
                        "write-gate-storage-direct",
                        "write-gate-storage-direct-policy",
                        "write-gate-resource-nodes",
                        "write-gate-resource-nodes-direct",
                        "write-gate-island-direct",
                        "write-gate-direct-policy",
                        "write-gate-market",
                        "write-gate-market-direct",
                        "write-gate-contracts",
                        "write-gate-contracts-direct",
                        "write-gate-research",
                        "write-gate-research-direct",
                        "write-gate-maintenance",
                        "write-gate-maintenance-direct",
                        "write-gate-lifecycle-state",
                        "write-gate-lifecycle-listener",
                        "write-gate-lifecycle-direct",
                        "write-gate-members",
                        "write-gate-permissions",
                        "write-gate-level-values",
                        "write-gate-warps",
                        "write-gate-biomes",
                        "write-gate-chat",
                        "write-gate-templates",
                        "runtime-biomes-status",
                        "runtime-biomes-policy",
                        "runtime-chat-status",
                        "runtime-chat-policy",
                        "runtime-templates-status",
                        "runtime-templates-policy",
                        "write-gate-lifecycle-subfeatures",
                        "write-gate-addon-state",
                        "write-gate-route-events",
                        "write-gate-dirty-save",
                        "lifecycle-event-source",
                        "lifecycle-event-coverage",
                        "lifecycle-event-actions",
                        "lifecycle-event-storage-policy",
                        "lifecycle-state-machine",
                        "lifecycle-authority-policy",
                        "lifecycle-error-policy",
                        "lifecycle-recovery-policy",
                        "lifecycle-placement-source-policy",
                        "lifecycle-placement-source-state-key",
                        "relocation-state-keys",
                        "last-relocation-island",
                        "last-relocation-operation",
                        "last-relocation-source-node",
                        "last-relocation-target-node",
                        "last-relocation-previous-world",
                        "last-relocation-previous-center",
                        "last-relocation-target-world",
                        "last-relocation-target-center",
                        "last-relocation-delta",
                        "last-relocation-machine-delta",
                        "last-relocation-resource-node-delta",
                        "last-relocation-placement-changed",
                        "last-relocation-machines-remapped",
                        "last-relocation-resource-nodes-remapped",
                        "last-relocation-machine-remap-deferred",
                        "last-relocation-resource-node-remap-deferred",
                        "last-relocation-remap-source",
                        "last-relocation-policy",
                        "last-relocation-state-authority",
                        "last-relocation-write-fence",
                        "last-relocation-duplicate-tick-policy",
                        "last-relocation-confirmed-state-policy",
                        "last-relocation-handoff-audit-key",
                        "last-relocation-heartbeat-expiry-policy",
                        "last-relocation-fencing-token-policy",
                        "last-relocation-stale-write-policy",
                        "last-relocation-at",
                        "island-state-object-storage-active-policy",
                        "island-state-object-storage-save-failure-policy",
                        "island-state-object-storage-retry-policy",
                        "island-state-object-storage-access-policy",
                        "island-state-object-storage-queue-key",
                        "island-state-bundle-manifest-policy",
                        "island-state-bundle-checksum-policy",
                        "island-state-bundle-restore-policy",
                        "island-state-bundle-quarantine-policy",
                        "island-state-lifecycle-state-machine",
                        "island-state-lifecycle-authority-policy",
                        "island-state-lifecycle-error-policy",
                        "island-state-lifecycle-recovery-policy",
                        "object-storage-active-island-policy",
                        "object-storage-save-failure-policy",
                        "object-storage-retry-policy",
                        "object-storage-access-policy",
                        "object-storage-queue-key",
                        "bundle-manifest-policy",
                        "bundle-checksum-policy",
                        "bundle-restore-policy",
                        "bundle-quarantine-policy",
                        "route-event-player-visible-policy",
                        "route-event-player-visible-state-keys",
                        "last-route-player-visible-destination",
                        "last-route-player-visible-action",
                        "last-route-player-visible-status",
                        "last-route-player-visible-topology",
                        "last-route-ticket-player-visible",
                        "last-route-player-visible-policy",
                        "route-event-handled-count",
                        "route-event-blocked-count",
                        "route-event-publish-failures",
                        "route-event-last-block-reason",
                        "last-lifecycle-remap-delta",
                        "last-lifecycle-machines-remapped",
                        "last-lifecycle-resource-nodes-remapped",
                        "last-lifecycle-remap-source",
                        "island-state-key",
                        "island-state-node-bound",
                        "island-state-mobility",
                        "island-state-migration-policy",
                        "island-state-authority",
                        "island-state-active-world-source",
                        "island-state-active-runtime-source",
                        "island-state-location-remap",
                        "island-state-remap-key",
                        "island-state-failover-policy",
                        "island-state-ab-node-scenario",
                        "island-state-ab-server-new-island-scenario",
                        "island-state-ab-server-existing-island-scenario",
                        "island-state-reload-reenable-scenario",
                        "satis-operation-scenarios",
                        "satis-completion-criteria",
                        "island-state-multi-node-scenario",
                        "island-state-node-count-policy",
                        "island-state-node-identity-policy",
                        "island-state-five-six-node-policy",
                        "island-state-seven-plus-node-policy",
                        "island-state-scale-policy",
                        "island-state-scale-risk",
                        "island-state-storage-authority",
                        "island-state-authoritative-store-policy",
                        "island-state-redis-advisory-policy",
                        "island-state-redis-failure-policy",
                        "island-state-write-fence",
                        "island-state-duplicate-tick-policy",
                        "island-state-reconnect-policy",
                        "addon-state-sync",
                        "addon-state-sync-configured",
                        "addon-state-sync-effective",
                        "addon-state-sync-available",
                        "addon-state-sync-policy",
                        "addon-state-sync-endpoint",
                        "addon-state-sync-island-endpoint",
                        "addon-state-sync-runtime-source",
                        "addon-state-sync-remap-policy",
                        "addon-state-sync-node-bound",
                        "addon-state-sync-core-api-mode",
                        "addon-state-sync-core-api-fallback-target",
                        "addon-state-sync-flattened-fallback-enabled",
                        "addon-state-sync-bulk-status-keys",
                        "addon-state-sync-write-fallback",
                        "last-core-bulk-publish-status",
                        "last-core-bulk-publish-mode",
                        "last-core-bulk-publish-write-path",
                        "last-core-bulk-publish-primary-endpoint",
                        "last-core-bulk-publish-fallback-endpoint",
                        "last-core-bulk-publish-error",
                        "last-core-bulk-publish-pending-retries",
                        "last-core-global-bulk-publish-status",
                        "last-core-global-bulk-publish-mode",
                        "last-core-global-bulk-publish-write-path",
                        "last-core-global-bulk-publish-primary-endpoint",
                        "last-core-global-bulk-publish-fallback-endpoint",
                        "last-core-global-bulk-publish-error",
                        "last-core-global-bulk-publish-pending-retries",
                        "addon-state-sync-bulk-max-pending-retries",
                        "addon-state-sync-island-bulk-successes",
                        "addon-state-sync-island-bulk-fallbacks",
                        "addon-state-sync-island-bulk-failures",
                        "addon-state-sync-island-bulk-pending-retries",
                        "addon-state-sync-island-bulk-retries-queued",
                        "addon-state-sync-island-bulk-retries-drained",
                        "addon-state-sync-island-bulk-retries-dropped",
                        "addon-state-sync-global-bulk-successes",
                        "addon-state-sync-global-bulk-fallbacks",
                        "addon-state-sync-global-bulk-failures",
                        "addon-state-sync-global-bulk-pending-retries",
                        "addon-state-sync-global-bulk-retries-queued",
                        "addon-state-sync-global-bulk-retries-drained",
                        "addon-state-sync-global-bulk-retries-dropped",
                        "addon-state-sync-table-successes",
                        "addon-state-sync-table-failures",
                        "addon-state-sync-reader-transport",
                        "addon-state-sync-global-table-load-successes",
                        "addon-state-sync-global-table-load-failures",
                        "addon-state-sync-island-table-load-successes",
                        "addon-state-sync-island-table-load-failures",
                        "addon-state-sync-flattened-load-fallbacks",
                        "addon-state-sync-core-api-failures",
                        "addon-state-sync-last-failure",
                        "addon-state-sync-last-failure-at",
                        "addon-state-bulk-save-api",
                        "addon-state-bulk-save-global-endpoint",
                        "addon-state-bulk-save-island-endpoint",
                        "addon-state-table-key-value-bulk-save-global-endpoint",
                        "addon-state-table-key-value-bulk-save-island-endpoint",
                        "addon-state-table-key-value-bulk-save-global-alias",
                        "addon-state-table-key-value-bulk-save-island-alias",
                        "addon-state-table-bulk-global-endpoint",
                        "addon-state-table-bulk-island-endpoint",
                        "addon-state-table-key-value-bulk-load-global-endpoint",
                        "addon-state-table-key-value-bulk-load-island-endpoint",
                        "addon-state-table-key-value-bulk-load-methods",
                        "addon-state-bulk-save-methods",
                        "addon-state-database-service-bulk-writer-api",
                        "database-core-api-bulk-load-policy",
                        "runtime-core-api-state-reader-transport",
                        "runtime-core-api-state-bulk-load-policy",
                        "core-api-table-save-mode",
                        "core-api-table-status-keys",
                        "core-api-bulk-status-keys",
                        "core-api-hydrate-status-keys",
                        "core-api-bulk-fallback-visibility",
                        "runtime-registration-policy",
                        "runtime-component-audit",
                        "feature-dependencies",
                        "runtime-feature-dependency-policy",
                        "runtime-feature-dependency-blocks",
                        "route-event-source",
                        "route-event-policy",
                        "route-event-feature-gate",
                        "route-event-state-scope",
                        "route-event-state-keys"
                ).forEach(key -> {
                    String value = metadata.get(key);
                    if (value != null && !value.isBlank()) {
                        visible.put("local." + key, value);
                    }
                });
            } catch (RuntimeException exception) {
                visible.put("local.database-metadata-error", exception.getMessage() == null ? "unknown" : exception.getMessage());
            }
        }
        if (database != null) {
            visible.put("local.database-active-backend", database.activeBackend().name());
            visible.put("local.database-cache-backend", database.cacheBackend());
            visible.put("local.database-attempted-backends", database.attemptedBackends().stream()
                    .map(DatabaseService.StorageBackend::name)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("none"));
            visible.put("local.database-fallback-reason", database.fallbackReason());
            visible.put("local.database-cache-description", database.cacheDescription());
            visible.put("local.database-description", database.databaseDescription());
        }
        if (sender instanceof Player player && addonIslandState != null) {
            islands.context(player).ifPresent(context -> {
                Map<String, String> islandState;
                try {
                    islandState = addonIslandState.apply(context.factoryIsland().islandUuid());
                } catch (RuntimeException exception) {
                    islandState = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
                }
                islandState.forEach((key, value) -> visible.put("island." + key, value));
            });
        }
        visible.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> messages.sendRaw(sender, "admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                )));
    }

    private void help(CommandSender sender, String label, int page) {
        List<String> commands = visibleHelpCommands(label, sender);
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commands, page, adminNavigationCommand(label), commandListPageSize);
        sender.sendMessage(messages.rawComponent("admin-command-list-title", Map.of("page", String.valueOf(commandPage.page()), "pages", String.valueOf(commandPage.pages())))
                .append(Component.text(" commands=" + commandPage.rangeSummary() + CommandListPolicy.HEADER_SUFFIX)));
        for (String command : CommandListPolicy.commandLines(commandPage)) {
            messages.sendRaw(sender, "command-list-entry", Map.of("command", command));
        }
    }

    private List<String> visibleHelpCommands(String label) {
        return visibleHelpCommands(label, null);
    }

    private List<String> visibleHelpCommands(String label, CommandSender viewer) {
        List<String> values = new ArrayList<>();
        for (String command : HELP_COMMANDS) {
            if (commandRequiresDisabledFeature(command)) {
                continue;
            }
            if (viewer != null && !hasAdminPermission(viewer, adminSubcommand(command))) {
                continue;
            }
            values.add(displayCommand(command, label));
        }
        return values;
    }

    private String adminSubcommand(String command) {
        String[] parts = command.split("\\s+");
        return parts.length > 2 ? parts[2] : "help";
    }

    private String adminNavigationCommand(String label) {
        return label + " admin command list";
    }

    private String displayCommand(String command, String label) {
        return command.replaceFirst("^factory", label);
    }

    private boolean commandRequiresDisabledFeature(String command) {
        return (command.contains(" give ") || command.contains(" giveitem ") || command.contains(" removehere")) && !enabled("machines")
                || command.contains(" addresearch ") && !enabled("research")
                || (command.contains(" setdebt ") || command.contains(" charge ") || command.contains(" repairhere")) && !enabled("maintenance")
                || command.contains(" repairhere") && !enabled("machines")
                || command.contains(" gennodes ") && !enabled("resource-nodes")
                || command.contains(" state") && !enabled("addon-state")
                || command.contains(" debug ") && !debugCommandsVisible()
                || command.contains(" debug networks") && !enabled("machines");
    }

    private int helpPage(String[] args) {
        if (args.length > 3 && isCommandListRoot(args[1]) && (args[2].equalsIgnoreCase("list") || args[2].equals("목록"))) {
            return (int) parseLong(args, 3, 1);
        }
        if (args.length > 2) {
            return (int) parseLong(args, 2, 1);
        }
        return 1;
    }

    private boolean isCommandListRoot(String value) {
        return value.equalsIgnoreCase("command")
                || value.equalsIgnoreCase("list")
                || value.equalsIgnoreCase("commands")
                || value.equalsIgnoreCase("command-list")
                || value.equals("명령어")
                || value.equals("명령어목록");
    }

    private String joined(String[] args, int fromIndex) {
        return joined(args, fromIndex, args.length);
    }

    private String joined(String[] args, int fromIndex, int toIndex) {
        if (args.length <= fromIndex) {
            return "";
        }
        int safeTo = Math.min(args.length, Math.max(fromIndex, toIndex));
        return String.join(" ", java.util.Arrays.copyOfRange(args, fromIndex, safeTo)).trim();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private long parseLong(String[] args, int index, long fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> amountSuggestions() {
        return List.of("1", "8", "16", "32", "64", "256", "1024");
    }

    private boolean needsPlayer(String adminSubcommand) {
        return adminSubcommand.equalsIgnoreCase("give")
                || adminSubcommand.equalsIgnoreCase("giveitem")
                || adminSubcommand.equalsIgnoreCase("addresearch")
                || adminSubcommand.equalsIgnoreCase("setdebt")
                || adminSubcommand.equalsIgnoreCase("charge")
                || adminSubcommand.equalsIgnoreCase("gennodes");
    }
}

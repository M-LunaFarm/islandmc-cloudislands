package kr.seungmin.satisskyfactory.command;

import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.config.SatisFeatureGateResolver;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.storage.SatisLegacyMigrationPolicy;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String MIGRATION_IMPORT_APPROVAL = SatisLegacyMigrationPolicy.APPROVAL_TOKEN;
    private static final String MIGRATION_SOURCE_POLICY = SatisLegacyMigrationPolicy.SOURCE_ACCESS_POLICY;
    private static final String MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS = SatisLegacyMigrationPolicy.forbiddenRuntimeProvidersCsv();
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
                "factory admin migration"
        ));
        commands.addAll(SatisLegacyMigrationPolicy.adminCommands());
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
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final ResourceNodeService nodes;
    private final SkyblockProvider skyblock;
    private final MaintenanceService maintenance;
    private final ResearchService research;
    private final PowerNetworkService power;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final DatabaseService database;
    private final Predicate<String> featureEnabled;
    private final Supplier<Map<String, String>> integrationMetadata;
    private final Supplier<Map<String, String>> addonState;
    private final Function<UUID, Map<String, String>> addonIslandState;
    private final Runnable reload;
    private String lastLegacyDryRunSource;
    private long lastLegacyDryRunRows;
    private String lastLegacyDryRunFingerprint;

    public AdminFactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                               StorageService storage, ResourceNodeService nodes, SkyblockProvider skyblock,
                               MaintenanceService maintenance, ResearchService research, PowerNetworkService power,
                               CustomItemFactory itemFactory, ItemRegistry items,
                               MessageService messages, DatabaseService database, Predicate<String> featureEnabled,
                               Supplier<Map<String, String>> integrationMetadata,
                               Supplier<Map<String, String>> addonState,
                               Function<UUID, Map<String, String>> addonIslandState,
                               Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.nodes = nodes;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
        this.research = research;
        this.power = power;
        this.itemFactory = itemFactory;
        this.items = items;
        this.messages = messages;
        this.database = database;
        this.featureEnabled = featureEnabled;
        this.integrationMetadata = integrationMetadata;
        this.addonState = addonState;
        this.addonIslandState = addonIslandState;
        this.reload = reload;
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
            if (!sender.hasPermission("satisskyfactory.debug") && !sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
        } else if (!sender.hasPermission("satisskyfactory.admin")) {
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
            case "migration", "migrate-superiorskyblock2", "migrate-ss2" -> {
                if (requireFeature(sender, "migration")) {
                    handleMigration(sender, args);
                }
            }
            case "state" -> {
                if (requireFeature(sender, "addon-state")) {
                    showAddonState(sender);
                }
            }
            case "give" -> {
                if (requireFeature(sender, "machines")) {
                    giveMachine(sender, args);
                }
            }
            case "giveitem" -> {
                if (requireFeature(sender, "machines")) {
                    giveItem(sender, args);
                }
            }
            case "addresearch" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "research")) {
                    return;
                }
                research.addResearch(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-research-updated");
            });
            case "setdebt" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "maintenance")) {
                    return;
                }
                maintenance.setDebt(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-debt-updated");
            });
            case "charge" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "maintenance")) {
                    return;
                }
                islands.context(target).ifPresent(context -> maintenance.chargeNow(island, target, context.islandRef().raw()));
                islands.save(island);
                messages.send(sender, "admin-maintenance-charged");
            });
            case "gennodes" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "resource-nodes")) {
                    return;
                }
                nodes.generateIfMissing(island.islandUuid(), target.getLocation(), location -> isInsideIsland(location, island));
                messages.send(sender, "admin-nodes-generated");
            });
            case "debug" -> debug(sender, args);
            case "removehere" -> {
                if (requireFeature(sender, "machines")) {
                    removeHere(sender);
                }
            }
            case "repairhere" -> {
                if (requireFeature(sender, "maintenance")) {
                    repairHere(sender);
                }
            }
            default -> messages.send(sender, "unknown-admin-command");
        }
        return true;
    }

    private String disabledFeatureFor(String subcommand) {
        return switch (subcommand) {
            case "migration", "migrate-superiorskyblock2", "migrate-ss2" -> enabled("migration") ? null : "migration";
            case "state" -> enabled("addon-state") ? null : "addon-state";
            case "give", "giveitem", "removehere" -> enabled("machines") ? null : "machines";
            case "addresearch" -> enabled("research") ? null : "research";
            case "setdebt", "charge", "repairhere" -> enabled("maintenance") ? null : "maintenance";
            case "gennodes" -> enabled("resource-nodes") ? null : "resource-nodes";
            case "debug" -> debugCommandsVisible() ? null : "debug";
            default -> null;
        };
    }

    private boolean isMigrationRoot(String value) {
        return value.equalsIgnoreCase("migration")
                || value.equalsIgnoreCase(SatisLegacyMigrationPolicy.LEGACY_COMMAND_ROOT)
                || value.equalsIgnoreCase("migrate-ss2");
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
            if (enabled("migration")) {
                values.add("migration");
                values.add(SatisLegacyMigrationPolicy.LEGACY_COMMAND_ROOT);
                values.add("migrate-ss2");
            }
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
                values.add("repairhere");
            }
            if (enabled("resource-nodes")) {
                values.add("gennodes");
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
        if (args.length == 3 && isMigrationRoot(args[1]) && enabled("migration")) {
            return filter(List.of("status", "scan", "dryrun", "dry-run", "verify", "verify-addon-state", "verify-no-legacy-provider", "import", "rollback"), args[2]);
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
        if (args[1].equalsIgnoreCase("gennodes") && !enabled("resource-nodes")) {
            return new ArrayList<>();
        }
        if (args.length == 3 && needsPlayer(args[1])) {
            return filter(onlinePlayerNames(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            return filter(definitions.all().stream().map(MachineDefinition::typeId).toList(), args[3]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("giveitem")) {
            return filter(itemIds(), args[3]);
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
        int maxPage = CommandListPolicy.pages(visibleHelpCommands("factory").size());
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

    private boolean isInsideIsland(org.bukkit.Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
                .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                .orElse(false);
    }

    private void giveMachine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "admin-give-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        definitions.get(args[3]).ifPresentOrElse(definition -> {
            long amount = parseLong(args, 4, 1);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            long returned = giveMachineItem(target, definition, amount);
            if (returned > 0) {
                messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-machine"));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "admin-giveitem-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        items.get(args[3]).ifPresentOrElse(item -> {
            long amount = parseLong(args, 4, 0);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            if (item.virtualOnly()) {
                if (!requireFeature(sender, "storage")) {
                    return;
                }
                if (!giveVirtualOnlyItem(sender, target, item.id(), amount)) {
                    return;
                }
            } else {
                long returned = giveVirtualItem(target, item.id(), amount);
                if (returned > 0) {
                    messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
                }
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-item"));
    }

    private long giveMachineItem(Player target, MachineDefinition definition, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemFactory.createMachineItem(definition.typeId(), stackAmount(definition.material(), remaining));
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private boolean giveVirtualOnlyItem(CommandSender sender, Player target, String itemId, long amount) {
        return islands.context(target).map(context -> {
            var inventory = storage.islandStorage(context.factoryIsland().islandUuid());
            if (!inventory.add(itemId, amount)) {
                messages.send(sender, "storage-full");
                return false;
            }
            storage.save(inventory);
            return true;
        }).orElseGet(() -> {
            messages.send(sender, "no-island");
            return false;
        });
    }

    private long giveVirtualItem(Player player, String itemId, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemStack(itemId, remaining);
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private ItemStack itemStack(String itemId, long amount) {
        return items.get(itemId)
                .map(item -> itemFactory.factoryItem(item, stackAmount(item.material(), amount)))
                .orElseGet(() -> {
                    Material material = material(itemId);
                    return new ItemStack(material, stackAmount(material, amount));
                });
    }

    private int stackAmount(Material material, long amount) {
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        return (int) Math.max(1, Math.min(maxStackSize, amount));
    }

    private Material material(String itemId) {
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        if (args[2].equalsIgnoreCase("networks") && !requireFeature(sender, "machines")) {
            return;
        }
        islands.existingContext(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                messages.send(sender, "debug-island", Map.of("island", context.factoryIsland().islandUuid().toString()));
            } else if (args[2].equalsIgnoreCase("networks")) {
                var state = power.state(context.factoryIsland().islandUuid());
                messages.send(sender, "debug-networks", Map.of(
                        "machines", String.valueOf(machines.byIsland(context.factoryIsland().islandUuid()).size()),
                        "ratio", NumberFormatter.ratio(state.ratio()),
                        "generation", NumberFormatter.decimal(state.generation(), 1),
                        "consumption", NumberFormatter.decimal(state.consumption(), 1),
                        "battery", NumberFormatter.decimal(state.batteryStored(), 1) + "/" + NumberFormatter.whole(state.batteryCapacity())));
            }
        });
    }

    private void removeHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            machines.forceRemove(machine);
            block.setType(Material.AIR, false);
            messages.send(sender, "machine-removed-admin");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repairHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            repair(machine);
            messages.send(sender, "machine-repaired");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        machines.save(machine);
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
        sender.sendMessage(messages.raw("admin-features-title"));
        sender.sendMessage(messages.raw("admin-integration-entry", Map.of("key", "feature-catalog", "value", SatisFeatureGateResolver.featureKeysMetadata())));
        sender.sendMessage(messages.raw("admin-integration-entry", Map.of("key", "feature-aliases", "value", SatisFeatureGateResolver.aliasMetadata())));
        sender.sendMessage(messages.raw("admin-integration-entry", Map.of("key", "feature-dependencies", "value", SatisFeatureGateResolver.dependencyMetadata())));
        sender.sendMessage(messages.raw("admin-integration-entry", Map.of("key", "feature-disable-policy", "value", SatisFeatureGateResolver.disablePolicy())));
        for (String feature : FEATURE_KEYS) {
            sender.sendMessage(messages.raw("admin-features-entry", Map.of(
                    "feature", feature,
                    "enabled", Boolean.toString(enabled(feature))
            )));
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
                "write-gate-lifecycle-subfeatures",
                "runtime-commands-status",
                "runtime-gui-status",
                "runtime-placeholder-status",
                "runtime-placeholder-exposure-policy",
                "runtime-placeholder-exposed-keys",
                "runtime-placeholder-denied-internal-fields",
                "runtime-placeholder-internal-placement-exposure"
        ).forEach(key -> {
            String value = details.get(key);
            if (value != null && !value.isBlank()) {
                sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", key,
                        "value", value
                )));
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
        sender.sendMessage(messages.raw("admin-integration-title"));
        Map<String, String> metadata;
        try {
            metadata = integrationMetadata == null ? Map.of() : integrationMetadata.get();
        } catch (RuntimeException exception) {
            metadata = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
        }
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void showMigration(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        state.put("satis-schema", "3");
        state.put("satis-storage-key", "cloudislands-island-uuid");
        state.put("legacy-source-project", SatisLegacyMigrationPolicy.SOURCE_PROJECT);
        state.put("legacy-skyblock-source", SatisLegacyMigrationPolicy.LEGACY_SKYBLOCK_SOURCE);
        state.put("legacy-satis-source", SatisLegacyMigrationPolicy.LEGACY_SATIS_SOURCE);
        state.put("legacy-allowed-input", "read-only-snapshot,sqlite-export");
        state.put("superior-migration-input-only", "true");
        state.put("superior-runtime-dependency", "false");
        state.put("superior-forbidden-runtime-dependencies", MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS);
        state.put("superior-provider-lookup", "disabled-at-runtime");
        state.put("superior-provider-service-check", "plugin-enabled-only-no-bukkit-service-binding");
        state.put("superior-migration-source-policy", SatisLegacyMigrationPolicy.SOURCE_ACCESS_POLICY);
        state.put("superior-runtime-dependency-policy", SatisLegacyMigrationPolicy.RUNTIME_DEPENDENCY_POLICY);
        state.put("superior-runtime-provider-hook-policy", SatisLegacyMigrationPolicy.RUNTIME_PROVIDER_HOOK_POLICY);
        state.put("superior-migration-manifest-policy", SatisLegacyMigrationPolicy.MANIFEST_POLICY);
        state.put("superior-migration-manifest-file", "migration-backups/legacy-import-last-manifest.json");
        state.put("superior-migration-output-id-policy", SatisLegacyMigrationPolicy.OUTPUT_ID_POLICY);
        state.put("superior-migration-approval-policy", SatisLegacyMigrationPolicy.APPROVAL_POLICY);
        state.put("superior-migration-approval-token-policy", SatisLegacyMigrationPolicy.APPROVAL_TOKEN_POLICY);
        state.put("superior-import-scan", "/ciadmin migrate-superiorskyblock2 scan [path]");
        state.put("superior-import-dryrun", "/ciadmin migrate-superiorskyblock2 dryrun [path]");
        state.put("superior-import-import", "/ciadmin migrate-superiorskyblock2 import <approvalToken>");
        state.put("superior-import-verify", "/ciadmin migrate-superiorskyblock2 verify [path]");
        state.put("superior-import-verify-addon-state", "/ciadmin migrate-superiorskyblock2 verify-addon-state <islandUuid>");
        state.put("superior-import-verify-no-legacy-provider", "/ciadmin migrate-superiorskyblock2 verify-no-legacy-provider");
        state.put("superior-import-rollback", "/ciadmin migrate-superiorskyblock2 rollback");
        state.put("satismc-import-status", "/factory admin migration status");
        state.put("satismc-import-scan", "/factory admin migration scan <sqlitePath>");
        state.put("satismc-import-dryrun", "/factory admin migration dryrun <sqlitePath>");
        state.put("satismc-import-verify", "/factory admin migration verify <sqlitePath>");
        state.put("satismc-import-verify-addon-state", "/factory admin migration verify-addon-state <islandUuid>");
        state.put("satismc-import-verify-no-legacy-provider", "/factory admin migration verify-no-legacy-provider");
        state.put("satismc-import-import", "/factory admin migration import <sqlitePath> " + MIGRATION_IMPORT_APPROVAL + "|" + MIGRATION_IMPORT_APPROVAL + ":<dryrun-sha256>");
        state.put("satismc-import-approval", MIGRATION_IMPORT_APPROVAL);
        state.put("satismc-import-mode", "cross-backend-sqlite-copy");
        state.put("satismc-import-manifest", "migration-backups/legacy-import-last-manifest.json");
        state.put("satismc-import-prerequisite", "same-source-dryrun-or-verify-before-import");
        state.put("satismc-core-api-import-guard", "reject-core-api-import-when-addon-state-writer-unavailable");
        state.put("satismc-rollback-mode", "sqlite-snapshot-restore-or-shared-backend-table-restore");
        state.put("satismc-rollback-safety", "restores-known-satis-tables-from-migration-backups/legacy-import-last.db");
        state.put("feature-gate", "migration=" + enabled("migration"));
        state.put("disabled-behavior", "reject-status-scan-dryrun-verify-import-rollback");
        state.put("writes-when-disabled", "false");
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void handleMigration(CommandSender sender, String[] args) {
        if (!enabled("migration")) {
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("feature-gate", "migration=false");
            state.put("mode", "disabled");
            state.put("writes", "false");
            state.put("source-policy", MIGRATION_SOURCE_POLICY);
            state.put("live-provider-hooks", "false");
            state.put("forbidden-runtime-providers", MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS);
            state.put("reason", "migration feature is disabled by config");
            state.put("disabled-behavior", "reject-status-scan-dryrun-verify-import-rollback");
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
            return;
        }
        if (args.length >= 3) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("status")) {
                showMigration(sender);
                return;
            }
            if (action.equals("scan") || action.equals("dryrun") || action.equals("dry-run") || action.equals("verify")) {
                scanLegacyDatabase(sender, args, action);
                return;
            }
            if (action.equals("verify-addon-state")) {
                verifyMigrationAddonState(sender, args);
                return;
            }
            if (action.equals("verify-no-legacy-provider")) {
                verifyNoLegacyProvider(sender);
                return;
            }
            if (action.equals("import")) {
                importLegacyDatabase(sender, args);
                return;
            }
            if (action.equals("rollback")) {
                rollbackLegacyDatabase(sender);
                return;
            }
        }
        showMigration(sender);
    }

    private void verifyMigrationAddonState(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "usage",
                    "value", "/factory admin migration verify-addon-state <islandUuid>"
            )));
            return;
        }
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        state.put("mode", "verify-addon-state-roundtrip");
        state.put("writes", "false");
        state.put("policy", SatisLegacyMigrationPolicy.ADDON_STATE_VERIFY_POLICY);
        state.put("requires-feature", "addon-state");
        state.put("feature-gate", "addon-state=" + enabled("addon-state"));
        try {
            UUID islandId = UUID.fromString(args[3]);
            Map<String, String> values = addonIslandState == null ? Map.of() : addonIslandState.apply(islandId);
            Map<String, String> safeValues = values == null ? Map.of() : values;
            state.put("island", islandId.toString());
            state.put("state-keys", Integer.toString(safeValues.size()));
            state.put("status", enabled("addon-state") ? (safeValues.isEmpty() ? "empty" : "available") : "addon-state-feature-disabled");
            state.put("roundtrip", enabled("addon-state") && !safeValues.isEmpty() ? "passed" : "not-proven");
            state.put("authority", "cloudislands-addon-state");
            state.put("node-bound", "false");
            state.put("next-step", safeValues.isEmpty() ? "activate-or-import-island-then-rerun-verify-addon-state" : "run-verify-no-legacy-provider");
        } catch (IllegalArgumentException exception) {
            state.put("status", "invalid-island-uuid");
            state.put("island", args[3]);
            state.put("roundtrip", "failed");
        }
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void verifyNoLegacyProvider(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        state.put("mode", "verify-no-legacy-provider");
        state.put("writes", "false");
        state.put("policy", SatisLegacyMigrationPolicy.RUNTIME_PROVIDER_HOOK_POLICY);
        state.put("runtime-dependency-policy", SatisLegacyMigrationPolicy.RUNTIME_DEPENDENCY_POLICY);
        state.put("forbidden-runtime-providers", MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS);
        boolean passed = true;
        for (String provider : MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS.split(",")) {
            String name = provider.trim();
            boolean enabledProvider;
            try {
                enabledProvider = Bukkit.getPluginManager().isPluginEnabled(name);
            } catch (RuntimeException exception) {
                enabledProvider = false;
                state.put("provider-check-error." + name, exception.getClass().getSimpleName());
            }
            state.put("provider." + name, enabledProvider ? "enabled" : "absent-or-disabled");
            if (enabledProvider) {
                passed = false;
            }
            boolean registeredService = legacyProviderServiceRegistered(name);
            state.put("service." + name, registeredService ? "registered" : "absent");
            if (registeredService) {
                passed = false;
            }
        }
        state.put("status", passed ? "passed" : "failed");
        state.put("live-provider-hooks", passed ? "false" : "legacy-provider-plugin-or-service-present");
        state.put("next-step", passed ? "migration-runtime-clean" : "remove-legacy-provider-before-accepting-migration");
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private boolean legacyProviderServiceRegistered(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String needle = provider.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        try {
            for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
                String serviceName = service.getName().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                if (serviceName.contains(needle)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return true;
        }
        return false;
    }

    private void scanLegacyDatabase(CommandSender sender, String[] args, String action) {
        if (args.length < 4) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "usage",
                    "value", "/factory admin migration " + action + " <sqlitePath>"
            )));
            return;
        }
        try {
            DatabaseService.LegacyImportPlan plan = database.scanLegacyDatabase(new File(joined(args, 3)));
            if (action.equals("dryrun") || action.equals("dry-run") || action.equals("verify")) {
                lastLegacyDryRunSource = plan.sourcePath();
                lastLegacyDryRunRows = plan.importableRows();
                lastLegacyDryRunFingerprint = legacySourceFingerprint(plan.sourcePath());
            }
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("source", plan.sourcePath());
            state.put("source-policy", MIGRATION_SOURCE_POLICY);
            state.put("source-kind", "sqlite-snapshot");
            state.put("live-provider-hooks", "false");
            state.put("forbidden-runtime-providers", MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS);
            state.put("mode", action.equals("scan") ? "scan-only" : (action.equals("verify") ? "verify-no-write" : "dryrun-no-write"));
            state.put("target-backend", database.activeBackend().name());
            state.put("writes", "false");
            state.put("conflict-policy", "none-scan-only");
            state.put("import-prerequisite-recorded", Boolean.toString(action.equals("dryrun") || action.equals("dry-run") || action.equals("verify")));
            if (lastLegacyDryRunSource != null && lastLegacyDryRunSource.equals(plan.sourcePath())) {
                state.put("source-fingerprint", lastLegacyDryRunFingerprint);
                state.put("approval-fingerprint-token", legacyApprovalToken(lastLegacyDryRunFingerprint));
            }
            state.put("importable-rows", String.valueOf(plan.importableRows()));
            state.put("importable-tables", String.valueOf(plan.importableTables()));
            state.put("skipped-tables", String.valueOf(plan.skippedTables()));
            plan.tables().forEach(table -> state.put("table." + table.table(),
                    (table.importable() ? "ready" : "skip") + ",rows=" + table.sourceRows() + ",reason=" + table.reason()));
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
        } catch (RuntimeException exception) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "error",
                    "value", exception.getMessage() == null ? "unknown" : exception.getMessage()
            )));
        }
    }

    private void importLegacyDatabase(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "usage",
                    "value", "/factory admin migration import <sqlitePath> " + MIGRATION_IMPORT_APPROVAL + "|" + MIGRATION_IMPORT_APPROVAL + ":<dryrun-sha256>"
            )));
            return;
        }
        int approvalIndex = approvalIndex(args);
        if (approvalIndex < 0 || approvalIndex <= 3) {
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("mode", "approval-required");
            state.put("writes", "false");
            state.put("approval", MIGRATION_IMPORT_APPROVAL);
            state.put("approval-fingerprint-form", MIGRATION_IMPORT_APPROVAL + ":<dryrun-sha256>");
            state.put("usage", "/factory admin migration import <sqlitePath> " + MIGRATION_IMPORT_APPROVAL + "|" + MIGRATION_IMPORT_APPROVAL + ":<dryrun-sha256>");
            state.put("safe-path", "/factory admin migration dryrun <sqlitePath>");
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
            return;
        }
        try {
            String sourcePath = joined(args, 3, approvalIndex);
            DatabaseService.LegacyImportPlan plan = database.scanLegacyDatabase(new File(sourcePath));
            if (lastLegacyDryRunSource == null || !lastLegacyDryRunSource.equals(plan.sourcePath())) {
                sender.sendMessage(messages.raw("admin-migration-title"));
                Map<String, String> state = new LinkedHashMap<>();
                state.put("mode", "dryrun-required");
                state.put("writes", "false");
                state.put("source", plan.sourcePath());
                state.put("required", "/factory admin migration dryrun <sqlitePath>");
                state.put("reason", "import requires a prior dryrun or verify for the same source path");
                state.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                                "key", entry.getKey(),
                                "value", entry.getValue()
                        ))));
                return;
            }
            if (lastLegacyDryRunRows != plan.importableRows()) {
                sender.sendMessage(messages.raw("admin-migration-title"));
                Map<String, String> state = new LinkedHashMap<>();
                state.put("mode", "dryrun-stale");
                state.put("writes", "false");
                state.put("source", plan.sourcePath());
                state.put("dryrun-rows", String.valueOf(lastLegacyDryRunRows));
                state.put("current-rows", String.valueOf(plan.importableRows()));
                state.put("required", "/factory admin migration dryrun <sqlitePath>");
                state.put("reason", "source changed after dryrun or verify");
                state.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                                "key", entry.getKey(),
                                "value", entry.getValue()
                        ))));
                return;
            }
            String currentFingerprint = legacySourceFingerprint(plan.sourcePath());
            if (lastLegacyDryRunFingerprint == null || !lastLegacyDryRunFingerprint.equals(currentFingerprint)) {
                sender.sendMessage(messages.raw("admin-migration-title"));
                Map<String, String> state = new LinkedHashMap<>();
                state.put("mode", "dryrun-stale");
                state.put("writes", "false");
                state.put("source", plan.sourcePath());
                state.put("dryrun-fingerprint", lastLegacyDryRunFingerprint == null ? "none" : lastLegacyDryRunFingerprint);
                state.put("current-fingerprint", currentFingerprint);
                state.put("required", "/factory admin migration dryrun <sqlitePath>");
                state.put("reason", "source file changed after dryrun or verify");
                state.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                                "key", entry.getKey(),
                                "value", entry.getValue()
                        ))));
                return;
            }
            if (!legacyApprovalAccepted(args[approvalIndex], currentFingerprint)) {
                sender.sendMessage(messages.raw("admin-migration-title"));
                Map<String, String> state = new LinkedHashMap<>();
                state.put("mode", "approval-fingerprint-mismatch");
                state.put("writes", "false");
                state.put("source", plan.sourcePath());
                state.put("approval", approvalToken(args[approvalIndex]));
                state.put("required", legacyApprovalToken(currentFingerprint));
                state.put("compat-approval", MIGRATION_IMPORT_APPROVAL);
                state.put("reason", "fingerprint approval token must match the last dryrun source");
                state.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                                "key", entry.getKey(),
                                "value", entry.getValue()
                        ))));
                return;
            }
            String approvedDryRunSource = lastLegacyDryRunSource;
            long approvedDryRunRows = lastLegacyDryRunRows;
            String approvedDryRunFingerprint = lastLegacyDryRunFingerprint;
            String approvedToken = approvalToken(args[approvalIndex]);
            DatabaseService.LegacyImportResult result = database.importLegacyDatabase(new File(sourcePath));
            lastLegacyDryRunSource = null;
            lastLegacyDryRunRows = 0L;
            lastLegacyDryRunFingerprint = null;
            reload.run();
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("source", result.sourcePath());
            state.put("source-policy", MIGRATION_SOURCE_POLICY);
            state.put("source-kind", "sqlite-snapshot");
            state.put("live-provider-hooks", "false");
            state.put("forbidden-runtime-providers", MIGRATION_FORBIDDEN_RUNTIME_PROVIDERS);
            state.put("target-backend", database.activeBackend().name());
            state.put("dryrun-source", approvedDryRunSource);
            state.put("dryrun-rows", String.valueOf(approvedDryRunRows));
            state.put("dryrun-fingerprint", approvedDryRunFingerprint);
            state.put("dryrun-cache", "cleared-after-import");
            state.put("copied-rows", String.valueOf(result.copiedRows()));
            state.put("copied-tables", String.join(",", result.copiedTables()));
            state.put("skipped-tables", result.skippedTables().isEmpty() ? "none" : String.join(",", result.skippedTables()));
            state.put("migration-manifest", result.manifestPath());
            state.put("mode", "cross-backend-sqlite-copy");
            state.put("writes", "true");
            state.put("approval", approvedToken);
            state.put("approval-policy", approvedToken.equalsIgnoreCase(MIGRATION_IMPORT_APPROVAL) ? "compat-token-with-dryrun-fingerprint-check" : "fingerprint-token");
            state.put("conflict-policy", "insert-ignore-existing-rows");
            state.put("core-api-import-guard", database.activeBackend() == DatabaseService.StorageBackend.CORE_API ? "passed-addon-state-writer-required" : "not-required-for-" + database.activeBackend().name());
            state.put("core-api-writer-required", Boolean.toString(database.activeBackend() == DatabaseService.StorageBackend.CORE_API));
            state.put("rollback-backup", result.rollbackBackupPath());
            state.put("rollback-command", "/factory admin migration rollback");
            state.put("next-import-prerequisite", "/factory admin migration dryrun <sqlitePath>");
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
        } catch (RuntimeException exception) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "error",
                    "value", exception.getMessage() == null ? "unknown" : exception.getMessage()
            )));
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "core-api-import-guard",
                    "value", database.activeBackend() == DatabaseService.StorageBackend.CORE_API ? "failed-or-unavailable-addon-state-writer" : "not-required-for-" + database.activeBackend().name()
            )));
        }
    }

    private String legacySourceFingerprint(String sourcePath) {
        File source = new File(sourcePath);
        return "sha256=" + sha256(source) + ",size=" + source.length() + ",modified=" + source.lastModified();
    }

    private String sha256(File source) {
        try (InputStream input = new java.io.BufferedInputStream(new java.io.FileInputStream(source))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to fingerprint legacy source database", exception);
        }
    }

    private void rollbackLegacyDatabase(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        try {
            DatabaseService.LegacyRollbackResult result = database.rollbackLastLegacyImport();
            if (result.restored()) {
                reload.run();
            }
            state.put("mode", rollbackMode(result));
            state.put("restored", Boolean.toString(result.restored()));
            state.put("status", result.status());
            state.put("backup", result.backupPath());
            state.put("next-step", result.nextStep());
            state.put("automatic-delete", "false");
            state.put("reason", rollbackReason(result));
            state.put("target-backend", database.activeBackend().name());
            state.put("core-api-rollback-policy", database.activeBackend() == DatabaseService.StorageBackend.CORE_API
                    ? "restore-local-cache-then-publish-addon-state"
                    : "not-required-for-" + database.activeBackend().name());
        } catch (RuntimeException exception) {
            state.put("mode", "rollback-failed");
            state.put("restored", "false");
            state.put("error", exception.getMessage() == null ? "unknown" : exception.getMessage());
            state.put("safe-path", "restore database backup, then run /factory admin migration verify <sqlitePath>");
        }
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private String rollbackMode(DatabaseService.LegacyRollbackResult result) {
        if (result != null && "core-api-addon-state-unavailable".equals(result.status())) {
            return "core-api-writer-required";
        }
        if (result == null || !result.restored()) {
            return "manual-restore-required";
        }
        if ("restored-shared-backend".equals(result.status())) {
            return "shared-backend-snapshot-restore";
        }
        if ("restored-core-api-local-cache-published".equals(result.status())) {
            return "core-api-local-cache-snapshot-publish";
        }
        return "sqlite-snapshot-restore";
    }

    private String rollbackReason(DatabaseService.LegacyRollbackResult result) {
        if (result != null && "core-api-addon-state-unavailable".equals(result.status())) {
            return "CORE_API rollback needs an active addon-state writer so restored cache rows are published cluster-wide";
        }
        if (result == null || !result.restored()) {
            return "rollback must not delete mixed live CloudIslands/Satis data automatically";
        }
        if ("restored-shared-backend".equals(result.status())) {
            return "restored last pre-import snapshot into active shared backend";
        }
        if ("restored-core-api-local-cache-published".equals(result.status())) {
            return "restored local CORE_API cache snapshot and published addon-state rows";
        }
        return "restored last SQLite pre-import snapshot";
    }

    private int approvalIndex(String[] args) {
        for (int i = 3; i < args.length; i++) {
            String token = approvalToken(args[i]);
            if (token.equalsIgnoreCase(MIGRATION_IMPORT_APPROVAL)
                    || token.toUpperCase(java.util.Locale.ROOT).startsWith(MIGRATION_IMPORT_APPROVAL + ":")) {
                return i;
            }
        }
        return -1;
    }

    private boolean legacyApprovalAccepted(String rawApproval, String fingerprint) {
        String token = approvalToken(rawApproval);
        return token.equalsIgnoreCase(MIGRATION_IMPORT_APPROVAL)
                || token.equalsIgnoreCase(legacyApprovalToken(fingerprint));
    }

    private String approvalToken(String rawApproval) {
        if (rawApproval == null) {
            return "";
        }
        String token = rawApproval.trim();
        if (token.toLowerCase(java.util.Locale.ROOT).startsWith("approval=")) {
            return token.substring("approval=".length()).trim();
        }
        if (token.toLowerCase(java.util.Locale.ROOT).startsWith("--confirm=")) {
            return token.substring("--confirm=".length()).trim();
        }
        return token;
    }

    private String legacyApprovalToken(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return MIGRATION_IMPORT_APPROVAL + ":unknown";
        }
        String marker = "sha256=";
        int start = fingerprint.indexOf(marker);
        if (start < 0) {
            return MIGRATION_IMPORT_APPROVAL + ":" + fingerprint.replaceAll("[^A-Za-z0-9._-]", "_");
        }
        int valueStart = start + marker.length();
        int valueEnd = fingerprint.indexOf(',', valueStart);
        return MIGRATION_IMPORT_APPROVAL + ":" + fingerprint.substring(valueStart, valueEnd < 0 ? fingerprint.length() : valueEnd);
    }

    private void showAddonState(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-integration-title"));
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
                        "database-setup-auto-selected",
                        "database-setup-selected-backend",
                        "database-setup-selected-source",
                        "database-setup-warning",
                        "database-setup-jdbc-aliases",
                        "database-setup-selection-policy",
                        "database-setup-backend-priority",
                        "database-setup-source-policy",
                        "database-setup-path",
                        "database-setup-fallback-precedence",
                        "database-setup-core-api-fallback",
                        "database-jdbc-inferred",
                        "database-jdbc-inferred-backend",
                        "database-active-backend",
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
                        "database-fallback-shared-backends",
                        "database-fallback-first-shared-backend",
                        "database-fallback-local-position",
                        "database-fallback-shared-safe",
                        "database-fallback-risk",
                        "database-fallback-production-safe",
                        "database-fallback-warning",
                        "database-fallback-authority",
                        "database-fallback-split-brain-risk",
                        "database-fallback-read-write-policy",
                        "database-config-source",
                        "database-core-api-marker",
                        "database-core-api-available",
                        "database-core-api-requires",
                        "database-core-api-mode",
                        "database-core-api-endpoint",
                        "database-core-api-local-cache",
                        "database-core-api-fallback-target",
                        "database-core-api-fallback-target-ready",
                        "database-core-api-fallback-policy",
                        "database-core-api-fallback-active",
                        "database-core-api-fallback-reason",
                        "database-core-api-flattened-fallback-enabled",
                        "database-core-api-write-fallback",
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
                        "runtime-addon-policy",
                        "runtime-cloudislands-api-required",
                        "runtime-standalone-island-management",
                        "runtime-skyblock-provider-policy",
                        "runtime-superior-migration-input-only",
                        "runtime-superior-runtime-dependency",
                        "runtime-superior-runtime-policy",
                        "runtime-forbidden-skyblock-providers",
                        "runtime-legacy-provider-lookup",
                        "runtime-migration-source-policy",
                        "runtime-addon-state-gate",
                        "runtime-addon-state-status",
                        "runtime-addon-state-policy",
                        "runtime-route-events-gate",
                        "runtime-route-events-status",
                        "runtime-route-events-policy",
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
                        "runtime-placeholder-internal-placement-exposure",
                        "placeholder-exposure-policy",
                        "placeholder-exposed-keys",
                        "placeholder-denied-internal-fields",
                        "placeholder-internal-placement-exposure",
                        "runtime-migration-gate",
                        "runtime-migration-status",
                        "runtime-migration-policy",
                        "runtime-storage-gate",
                        "runtime-storage-status",
                        "runtime-storage-policy",
                        "runtime-market-gate",
                        "runtime-market-status",
                        "runtime-market-policy",
                        "runtime-contracts-gate",
                        "runtime-contracts-status",
                        "runtime-contracts-policy",
                        "runtime-research-gate",
                        "runtime-research-status",
                        "runtime-research-policy",
                        "runtime-machines-gate",
                        "runtime-machines-status",
                        "runtime-machines-policy",
                        "runtime-resource-nodes-gate",
                        "runtime-resource-nodes-status",
                        "runtime-resource-nodes-policy",
                        "runtime-gui-gate",
                        "runtime-gui-status",
                        "runtime-gui-policy",
                        "runtime-menus-gate",
                        "runtime-menus-status",
                        "runtime-lifecycle-gate",
                        "runtime-lifecycle-status",
                        "runtime-lifecycle-policy",
                        "runtime-maintenance-gate",
                        "runtime-maintenance-status",
                        "runtime-maintenance-policy",
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
                        "runtime-dirty-save-running",
                        "runtime-dirty-save-pending-writes",
                        "runtime-dirty-save-pending-machines",
                        "runtime-dirty-save-pending-inventories",
                        "runtime-dirty-save-pending-nodes",
                        "runtime-dirty-save-pending-islands",
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
                        "write-gate-lifecycle-subfeatures",
                        "write-gate-addon-state",
                        "write-gate-route-events",
                        "write-gate-dirty-save",
                        "lifecycle-event-source",
                        "lifecycle-event-coverage",
                        "lifecycle-event-actions",
                        "lifecycle-event-storage-policy",
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
                        "last-relocation-at",
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
                        "island-state-location-remap",
                        "island-state-failover-policy",
                        "island-state-ab-node-scenario",
                        "island-state-storage-authority",
                        "island-state-write-fence",
                        "island-state-duplicate-tick-policy",
                        "island-state-reconnect-policy",
                        "addon-state-sync",
                        "addon-state-sync-configured",
                        "addon-state-sync-effective",
                        "addon-state-sync-available",
                        "addon-state-sync-policy",
                        "addon-state-sync-endpoint",
                        "addon-state-sync-bulk-status-keys",
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
            visible.put("local.database-attempted-backends", database.attemptedBackends().stream()
                    .map(DatabaseService.StorageBackend::name)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("none"));
            visible.put("local.database-fallback-reason", database.fallbackReason());
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
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void help(CommandSender sender, String label, int page) {
        List<String> commands = visibleHelpCommands(label);
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commands, page, label + " admin command list");
        sender.sendMessage(messages.raw("admin-command-list-title", Map.of("page", String.valueOf(commandPage.page()), "pages", String.valueOf(commandPage.pages()))) + " commands=" + commandPage.rangeSummary());
        for (String command : commandPage.entries()) {
            sender.sendMessage(messages.raw("command-list-entry", Map.of("command", command)));
        }
        if (commandPage.previousCommand() != null) {
            sender.sendMessage(messages.raw("command-list-entry", Map.of("command", commandPage.previousCommand())));
        }
        if (commandPage.nextCommand() != null) {
            sender.sendMessage(messages.raw("command-list-entry", Map.of("command", commandPage.nextCommand())));
        }
    }

    private List<String> visibleHelpCommands(String label) {
        List<String> values = new ArrayList<>();
        for (String command : HELP_COMMANDS) {
            if (commandRequiresDisabledFeature(command)) {
                continue;
            }
            values.add(command.replaceFirst("^factory", label));
        }
        return values;
    }

    private boolean commandRequiresDisabledFeature(String command) {
        return (command.contains(" give ") || command.contains(" giveitem ") || command.contains(" removehere")) && !enabled("machines")
                || command.contains(" addresearch ") && !enabled("research")
                || (command.contains(" setdebt ") || command.contains(" charge ") || command.contains(" repairhere")) && !enabled("maintenance")
                || command.contains(" gennodes ") && !enabled("resource-nodes")
                || command.contains(" migration") && !enabled("migration")
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

    private void withPlayerContext(CommandSender sender, String[] args, int playerIndex, AdminContextConsumer consumer) {
        if (args.length <= playerIndex) {
            messages.send(sender, "player-required");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[playerIndex]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        islands.context(target).ifPresentOrElse(context -> consumer.accept(target, context.factoryIsland()), () -> messages.send(sender, "no-island"));
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

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private List<String> itemIds() {
        return items.all().keySet().stream().sorted().toList();
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

    @FunctionalInterface
    private interface AdminContextConsumer {
        void accept(Player player, FactoryIsland island);
    }
}

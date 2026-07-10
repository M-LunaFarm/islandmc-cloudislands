package kr.seungmin.satisskyfactory.command;

import java.util.Map;
import java.util.function.Predicate;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class AdminIslandOperations {
    private final FactoryIslandService islands;
    private final ResourceNodeService nodes;
    private final SkyblockProvider skyblock;
    private final MaintenanceService maintenance;
    private final ResearchService research;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;

    AdminIslandOperations(FactoryIslandService islands, ResourceNodeService nodes, SkyblockProvider skyblock,
                          MaintenanceService maintenance, ResearchService research, MessageService messages,
                          Predicate<String> featureEnabled) {
        this.islands = islands;
        this.nodes = nodes;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
        this.research = research;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
    }

    void addResearch(CommandSender sender, String[] args) {
        withPlayerContext(sender, args, 2, (target, island) -> {
            if (!requireFeature(sender, "research")) {
                return;
            }
            long previousResearch = island.researchPoints();
            if (research.addResearch(island, parseLong(args, 3, 0))) {
                if (!islands.save(island)) {
                    island.researchPoints(previousResearch);
                    messages.send(sender, "feature-disabled", Map.of("feature", "research"));
                    return;
                }
                messages.send(sender, "admin-research-updated");
            }
        });
    }

    void setDebt(CommandSender sender, String[] args) {
        withPlayerContext(sender, args, 2, (target, island) -> {
            if (!requireFeature(sender, "maintenance")) {
                return;
            }
            long previousDebt = island.maintenanceDebt();
            MaintenanceStatus previousStatus = island.maintenanceStatus();
            long previousScore = island.factoryScore();
            if (maintenance.setDebt(island, parseLong(args, 3, 0))) {
                if (!islands.save(island)) {
                    island.maintenanceDebt(previousDebt);
                    island.maintenanceStatus(previousStatus);
                    island.factoryScore(previousScore);
                    messages.send(sender, "feature-disabled", Map.of("feature", "maintenance"));
                    return;
                }
                messages.send(sender, "admin-debt-updated");
            }
        });
    }

    void charge(CommandSender sender, String[] args) {
        withPlayerContext(sender, args, 2, (target, island) -> {
            if (!requireFeature(sender, "maintenance")) {
                return;
            }
            long previousDebt = island.maintenanceDebt();
            MaintenanceStatus previousStatus = island.maintenanceStatus();
            long previousScore = island.factoryScore();
            long previousLastMaintenanceAt = island.lastMaintenanceAt();
            islands.context(target).ifPresent(context -> {
                if (maintenance.chargeNowIfWritesAllowed(island, target, context.islandRef().raw())) {
                    if (!islands.save(island)) {
                        island.maintenanceDebt(previousDebt);
                        island.maintenanceStatus(previousStatus);
                        island.factoryScore(previousScore);
                        island.lastMaintenanceAt(previousLastMaintenanceAt);
                        messages.send(sender, "feature-disabled", Map.of("feature", "maintenance"));
                        return;
                    }
                    messages.send(sender, "admin-maintenance-charged");
                }
            });
        });
    }

    void generateNodes(CommandSender sender, String[] args) {
        withPlayerContext(sender, args, 2, (target, island) -> {
            if (!requireFeature(sender, "resource-nodes")) {
                return;
            }
            nodes.generateIfMissing(island.islandUuid(), target.getLocation(), location -> isInsideIsland(location, island));
            messages.send(sender, "admin-nodes-generated");
        });
    }

    private boolean isInsideIsland(org.bukkit.Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
            .map(ref -> ref.islandUuid().equals(island.islandUuid()))
            .orElse(false);
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
        islands.context(target).ifPresentOrElse(
            context -> consumer.accept(target, context.factoryIsland()),
            () -> messages.send(sender, "no-island")
        );
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

    @FunctionalInterface
    private interface AdminContextConsumer {
        void accept(Player player, FactoryIsland island);
    }
}

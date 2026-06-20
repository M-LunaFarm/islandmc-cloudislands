package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;
import kr.lunaf.cloudislands.paper.gui.ConfirmationTokenPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandConfirmationMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMemberMenu;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class IslandCommandBackend implements CommandExecutor, Listener {
    static final List<String> SUBCOMMANDS = IslandCommandCatalog.SUBCOMMANDS;
    static final List<String> HELP_COMMANDS = IslandCommandCatalog.HELP_COMMANDS;
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;
    private final IslandLevelScanService levelScanService;
    private final IslandBankCommandHandler bankCommands;
    private final IslandSnapshotCommandHandler snapshotCommands;
    private final IslandWarehouseCommandHandler warehouseCommands;
    private final IslandChatLogCommandHandler chatLogCommands;
    private final IslandProgressionCommandHandler progressionCommands;
    private final IslandEnvironmentCommandHandler environmentCommands;
    private final IslandSettingsCommandHandler settingsCommands;
    private final IslandHomeWarpCommandHandler homeWarpCommands;
    private final IslandVisitReviewCommandHandler visitReviewCommands;
    private final IslandLifecycleCommandHandler lifecycleCommands;
    private final IslandOverviewCommandHandler overviewCommands;
    private final IslandPermissionCommandHandler permissionCommands;
    private final IslandMembershipCommandHandler membershipCommands;
    private final IslandAdminNodeCommandHandler adminCommands;
    private final IslandRoutingCommandHandler routingCommands;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this(plugin, coreApiClient, protection, 20);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, "Lobby");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, new BukkitPlayerGateway(), new BukkitWorldGateway(plugin));
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null, players, worlds);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, players, worlds, "island-1");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds, String configuredNodeId) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
        this.levelScanService = levelScanService;
        this.routingCommands = new IslandRoutingCommandHandler(plugin, coreApiClient, routeWaitSeconds, fallbackServerName, new IslandRoutingCommandHandler.Runtime() {
            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback, String... variables) {
                return IslandCommandBackend.this.routeMessage(key, fallback, variables);
            }

            @Override
            public String routeMessage(Player player, String key, String fallback, String... variables) {
                return IslandCommandBackend.this.routeMessage(player, key, fallback, variables);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String playerMessage(String message) {
                return IslandCommandBackend.this.playerMessage(message);
            }

            @Override
            public boolean coreUnavailable(Throwable error) {
                return IslandCommandBackend.this.coreUnavailable(error);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }
        });
        this.bankCommands = new IslandBankCommandHandler(plugin, coreApiClient, economyBridge, new IslandBankCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.snapshotCommands = new IslandSnapshotCommandHandler(plugin, coreApiClient, new IslandSnapshotCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public String actionResultMessage(String action, UUID id, String body) {
                return IslandCommandBackend.this.actionResultMessage(action, id, body);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
                IslandCommandBackend.this.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
            }

            @Override
            public boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
                return IslandCommandBackend.this.confirmationAccepted(player, action, click);
            }
        });
        this.warehouseCommands = new IslandWarehouseCommandHandler(coreApiClient, new IslandWarehouseCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }
        });
        this.chatLogCommands = new IslandChatLogCommandHandler(plugin, coreApiClient, new IslandChatLogCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.progressionCommands = new IslandProgressionCommandHandler(plugin, coreApiClient, levelScanService, new IslandProgressionCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.environmentCommands = new IslandEnvironmentCommandHandler(plugin, coreApiClient, protection, new IslandEnvironmentCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String actionResultMessage(String label, UUID targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String actionResultMessage(String label, String targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.settingsCommands = new IslandSettingsCommandHandler(plugin, coreApiClient, new IslandSettingsCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String actionResultMessage(String label, UUID targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String actionResultMessage(String label, String targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.homeWarpCommands = new IslandHomeWarpCommandHandler(plugin, coreApiClient, new IslandHomeWarpCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String actionResultMessage(String label, String targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public IslandLocation location(Location location) {
                return IslandCommandBackend.this.location(location);
            }

            @Override
            public String pointListMessage(String body, String label, String emptyMessage) {
                return IslandCommandBackend.this.pointListMessage(body, label, emptyMessage);
            }

            @Override
            public IslandHomeWarpCommandHandler.Point point(String body, String requestedName, String fallbackWorldName) {
                return IslandCommandBackend.this.point(body, requestedName, fallbackWorldName);
            }

            @Override
            public void moveToPoint(Player player, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
                IslandCommandBackend.this.moveToPoint(player, point, missingMessage, successMessage);
            }

            @Override
            public boolean teleportLocalDefaultHome(Player player) {
                return IslandCommandBackend.this.teleportLocalDefaultHome(player);
            }

            @Override
            public boolean coreUnavailable(Throwable error) {
                return IslandCommandBackend.this.coreUnavailable(error);
            }

            @Override
            public boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, String islandInfo) {
                return IslandCommandBackend.this.publicWarpAllowed(player, point, islandInfo);
            }

            @Override
            public void routeWarp(Player player, UUID islandId, String warpName) {
                routingCommands.routeWarp(player, islandId, warpName);
            }

            @Override
            public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
                IslandCommandBackend.this.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
            }

            @Override
            public boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
                return IslandCommandBackend.this.confirmationAccepted(player, action, click);
            }
        });
        this.visitReviewCommands = new IslandVisitReviewCommandHandler(plugin, coreApiClient, new IslandVisitReviewCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
                routingCommands.routeTicket(player, ticketFuture, failureMessage);
            }
        });
        this.lifecycleCommands = new IslandLifecycleCommandHandler(plugin, coreApiClient, new IslandLifecycleCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public String actionResultMessage(String label, UUID targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.overviewCommands = new IslandOverviewCommandHandler(plugin, coreApiClient, new IslandOverviewCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.permissionCommands = new IslandPermissionCommandHandler(plugin, coreApiClient, new IslandPermissionCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public CompletableFuture<UUID> resolvePlayerUuid(String value) {
                return IslandCommandBackend.this.resolvePlayerUuid(value);
            }

            @Override
            public String actionResultMessage(String label, String targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String actionResultMessage(String label, UUID targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }
        });
        this.membershipCommands = new IslandMembershipCommandHandler(plugin, coreApiClient, new IslandMembershipCommandHandler.Runtime() {
            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public String joined(String[] args, int start) {
                return IslandCommandBackend.this.joined(args, start);
            }

            @Override
            public int integer(String value, int fallback) {
                return IslandCommandBackend.this.integer(value, fallback);
            }

            @Override
            public long longValue(String value, long fallback) {
                return IslandCommandBackend.this.longValue(value, fallback);
            }

            @Override
            public String roleKey(String value) {
                return permissionCommands.roleKey(value);
            }

            @Override
            public boolean editableRoleKey(String roleKey) {
                return permissionCommands.editableRoleKey(roleKey);
            }

            @Override
            public int defaultRoleWeight(String roleKey) {
                return permissionCommands.defaultRoleWeight(roleKey);
            }

            @Override
            public void listIslandMembers(Player player) {
                IslandCommandBackend.this.listIslandMembers(player);
            }

            @Override
            public void openIslandMemberMenu(Player player) {
                IslandCommandBackend.this.openIslandMemberMenu(player);
            }

            @Override
            public void openIslandMemberMenu(Player player, int page) {
                IslandCommandBackend.this.openIslandMemberMenu(player, page);
            }

            @Override
            public void inviteIslandMember(Player player, String target) {
                IslandCommandBackend.this.inviteIslandMember(player, target);
            }

            @Override
            public void listPendingInvites(Player player) {
                IslandCommandBackend.this.listPendingInvites(player);
            }

            @Override
            public void acceptIslandInviteTarget(Player player, String target) {
                IslandCommandBackend.this.acceptIslandInviteTarget(player, target);
            }

            @Override
            public void declineIslandInviteTarget(Player player, String target) {
                IslandCommandBackend.this.declineIslandInviteTarget(player, target);
            }

            @Override
            public void removeIslandMember(Player player, String target) {
                IslandCommandBackend.this.removeIslandMember(player, target);
            }

            @Override
            public void setIslandMemberRole(Player player, String target, IslandRole role, String successMessage) {
                IslandCommandBackend.this.setIslandMemberRole(player, target, role, successMessage);
            }

            @Override
            public void setIslandMemberRole(Player player, String target, String roleKey, String successMessage) {
                IslandCommandBackend.this.setIslandMemberRole(player, target, roleKey, successMessage);
            }

            @Override
            public void trustIslandMemberTemporary(Player player, String target, String duration) {
                IslandCommandBackend.this.trustIslandMemberTemporary(player, target, duration);
            }

            @Override
            public void transferIslandOwnership(Player player, String target) {
                IslandCommandBackend.this.transferIslandOwnership(player, target);
            }

            @Override
            public void banIslandVisitor(Player player, String target, String reason) {
                IslandCommandBackend.this.banIslandVisitor(player, target, reason);
            }

            @Override
            public void pardonIslandVisitor(Player player, String target) {
                IslandCommandBackend.this.pardonIslandVisitor(player, target);
            }

            @Override
            public void kickIslandVisitor(Player player, String target) {
                IslandCommandBackend.this.kickIslandVisitor(player, target);
            }

            @Override
            public void openIslandBanMenu(Player player) {
                IslandCommandBackend.this.openIslandBanMenu(player);
            }

            @Override
            public void listIslandBans(Player player) {
                IslandCommandBackend.this.listIslandBans(player);
            }

            @Override
            public void listIslandPermissions(Player player) {
                permissionCommands.listIslandPermissions(player);
            }

            @Override
            public void openIslandPermissionMenu(Player player) {
                permissionCommands.openIslandPermissionMenu(player);
            }

            @Override
            public void openIslandPermissionMenu(Player player, int page, int rolePage) {
                permissionCommands.openIslandPermissionMenu(player, page, rolePage);
            }

            @Override
            public void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
                permissionCommands.stageIslandPermission(player, roleName, permissionName, allowedValue);
            }

            @Override
            public void resetStagedIslandPermissions(Player player) {
                permissionCommands.resetStagedIslandPermissions(player);
            }

            @Override
            public void saveStagedIslandPermissions(Player player) {
                permissionCommands.saveStagedIslandPermissions(player);
            }

            @Override
            public void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
                permissionCommands.setIslandPermission(player, roleName, permissionName, allowedValue);
            }

            @Override
            public void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue) {
                permissionCommands.setIslandPermissionOverride(player, target, permissionName, allowedValue);
            }

            @Override
            public void openIslandRoleMenu(Player player) {
                permissionCommands.openIslandRoleMenu(player);
            }

            @Override
            public void listIslandRoles(Player player) {
                permissionCommands.listIslandRoles(player);
            }

            @Override
            public void upsertIslandRole(Player player, String roleKey, int weight, String displayName) {
                permissionCommands.upsertIslandRole(player, roleKey, weight, displayName);
            }

            @Override
            public void resetIslandRole(Player player, String roleKey) {
                permissionCommands.resetIslandRole(player, roleKey);
            }

            @Override
            public void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click) {
                permissionCommands.adjustIslandRoleWeight(player, roleName, weightValue, displayName, click);
            }

            @Override
            public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
                IslandCommandBackend.this.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
            }

            @Override
            public boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
                return IslandCommandBackend.this.confirmationAccepted(player, action, click);
            }
        });
        this.adminCommands = new IslandAdminNodeCommandHandler(plugin, coreApiClient, configuredNodeId, new IslandAdminNodeCommandHandler.Runtime() {
            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String actionResultMessage(String label, String targetId, String body) {
                return IslandCommandBackend.this.actionResultMessage(label, targetId, body);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
                IslandCommandBackend.this.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
            }

            @Override
            public boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
                return IslandCommandBackend.this.confirmationAccepted(player, action, click);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.messages = messages;
        this.locales = locales;
        this.players = players;
        this.worlds = worlds;
    }

    private <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.request(auditAction), operation);
    }

    private <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction), operation);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(routeMessage("player-only-command", "플레이어만 사용할 수 있습니다."));
            return true;
        }
        if (args.length == 0) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        int commandListPage = commandListPage(args);
        if (commandListPage > 0) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, commandListPage);
            return true;
        }
        if (subcommand.equals("menu") || subcommand.equals("메뉴")) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
            return true;
        }
        if (overviewCommands.handleCommand(player, subcommand)) {
            return true;
        }
        if (lifecycleCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (homeWarpCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (settingsCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (visitReviewCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (progressionCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (bankCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (warehouseCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (chatLogCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (environmentCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (snapshotCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (membershipCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
        return true;
    }

    void executeGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action == null) {
            return;
        }
        String actionId = action.actionId();
        if (actionId == null || actionId.isBlank()) {
            return;
        }
        if (bankCommands.handleGuiAction(player, action)) {
            return;
        }
        if (snapshotCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (chatLogCommands.handleGuiAction(player, action)) {
            return;
        }
        if (progressionCommands.handleGuiAction(player, action)) {
            return;
        }
        if (environmentCommands.handleGuiAction(player, action)) {
            return;
        }
        if (settingsCommands.handleGuiAction(player, action, click.right())) {
            return;
        }
        if (homeWarpCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (visitReviewCommands.handleGuiAction(player, action)) {
            return;
        }
        if (lifecycleCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (overviewCommands.handleGuiAction(player, action)) {
            return;
        }
        if (membershipCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (adminCommands.handleGuiAction(player, action, click)) {
            return;
        }
        switch (actionId) {
            case "island.main.open" -> sendCommandList(player, "섬", "섬 명령어 목록", HELP_COMMANDS, 1);
            case "gui.close" -> player.closeInventory();
            default -> message(player, routeMessage("gui-action-unknown", "알 수 없는 GUI 작업입니다: ") + actionId);
        }
    }

    private void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
        IslandConfirmationMenu.open(player, messagesFor(player), IslandConfirmationMenu.Confirmation.of(
            title,
            description,
            material,
            confirmName,
            confirmAction,
            ConfirmationTokenPolicy.withToken(confirmAction, data),
            confirmLore,
            cancelAction
        ));
    }

    private boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
        if (action != null && ConfirmationTokenPolicy.confirmed(action.actionId(), action.data(), click)) {
            return true;
        }
        message(player, routeMessage("confirmation-token-invalid", "확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }

    private void sendCommandList(Player player, String title, List<String> commands, int page) {
        sendCommandList(player, "섬", title, commands, page);
    }

    private void sendCommandList(Player player, String label, String title, List<String> commands, int page) {
        List<String> labelledCommands = commands.stream()
            .map(command -> command.replaceFirst("^섬", label))
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(labelledCommands, page, label + " command list");
        String headerTitle = routeMessage("command-list-title", title + " ");
        String headerSuffix = routeMessage("command-list-suffix", CommandListPolicy.HEADER_SUFFIX);
        player.sendMessage(headerTitle + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + headerSuffix);
        for (String command : commandPage.entries()) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + command);
        }
        if (commandPage.previousCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
        }
        if (commandPage.nextCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
        }
    }

    private int helpPage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        return (int) number(args[index], 1L);
    }

    private int commandListPage(String[] args) {
        if (args.length == 0) {
            return 0;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("command") && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return helpPage(args, 2);
        }
        if (first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록")) {
            return helpPage(args, 1);
        }
        return 0;
    }

    private String playerCodeMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String policyMessage = RouteFailureMessagePolicy.playerMessage(code, fallback);
        if (!java.util.Objects.equals(policyMessage, fallback) || !RouteFailureMessagePolicy.FALLBACK_CATEGORY.equals(RouteFailureMessagePolicy.playerSafeCategory(code))) {
            return policyMessage;
        }
        return switch (code) {
            case "OWNER_ROLE_PROTECTED" -> "섬 소유자는 소유권 양도로만 변경할 수 있습니다.";
            case "MEMBER_ROLE_UNAVAILABLE" -> "멤버 역할로 사용할 수 없는 값입니다.";
            case "VISITOR_BAN_DENIED" -> "섬 멤버는 방문자 밴으로 처리할 수 없습니다.";
            case "REVIEW_OWNER_DENIED" -> "자기 섬은 평가할 수 없습니다.";
            case "REVIEW_RATING_INVALID" -> "평점은 1~5 사이여야 합니다.";
            case "INSUFFICIENT_ITEMS" -> "섬 창고 수량이 부족합니다.";
            default -> fallback;
        };
    }

    private MessageRenderer messagesFor(Player player) {
        return messages == null || player == null ? messages : messages.forLocale(locales == null ? player.getLocale() : locales.locale(player));
    }

    private String routeMessage(String key, String fallback, String... variables) {
        String rendered = messages == null ? "" : messages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String routeMessage(Player player, String key, String fallback, String... variables) {
        MessageRenderer playerMessages = messagesFor(player);
        String rendered = playerMessages == null ? "" : playerMessages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    private void listIslandMembers(Player player) {
        currentIsland(player, "섬 안에서만 멤버를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandMembers(islandId)
                .thenAccept(body -> message(player, memberListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 멤버를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandMemberMenu(Player player) {
        openIslandMemberMenu(player, 0);
    }

    private void openIslandMemberMenu(Player player, int page) {
        currentIsland(player, "섬 안에서만 멤버 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMemberMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player), page));
    }

    private void inviteIslandMember(Player player, String target) {
        currentIsland(player, "섬 안에서만 플레이어를 초대할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                message(player, routeMessage("member-invite-denied", "섬 멤버를 초대할 권한이 없습니다."));
                return;
            }
            Player online = plugin.getServer().getPlayerExact(target);
            UUID parsed = uuid(target);
            if (online != null || parsed != null) {
                sendIslandInvite(player, islandId, online == null ? parsed : online.getUniqueId());
                return;
            }
            coreApiClient.playerInfoByName(target).thenAccept(body -> {
                UUID profileUuid = uuid(text(body, "playerUuid"));
                sendIslandInvite(player, islandId, profileUuid == null ? plugin.getServer().getOfflinePlayer(target).getUniqueId() : profileUuid);
            }).exceptionally(error -> {
                sendIslandInvite(player, islandId, plugin.getServer().getOfflinePlayer(target).getUniqueId());
                return null;
            });
        });
    }

    private void sendIslandInvite(Player player, UUID islandId, UUID targetUuid) {
        mutate("island.invite.create", () -> coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid))
            .thenAccept(body -> message(player, actionResultMessage("섬 초대", text(body, "inviteId"), body)))
            .exceptionally(error -> {
                message(player, "섬 초대를 보내지 못했습니다.");
                return null;
            });
    }

    private void listPendingInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId())
            .thenAccept(body -> message(player, inviteListMessage(body)))
            .exceptionally(error -> {
                message(player, "섬 초대 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void acceptIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            message(player, routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        mutate("island.invite.accept", () -> coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()))
            .thenAccept(body -> message(player, inviteActionMessage("섬 초대 수락", inviteId, body)))
            .exceptionally(error -> {
                message(player, "섬 초대를 수락하지 못했습니다.");
                return null;
            });
    }

    private void acceptIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            acceptIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private void declineIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            message(player, routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        mutate("island.invite.decline", () -> coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId()))
            .thenAccept(body -> message(player, inviteActionMessage("섬 초대 거절", inviteId, body)))
            .exceptionally(error -> {
                message(player, "섬 초대를 거절하지 못했습니다.");
                return null;
            });
    }

    private void declineIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            declineIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private CompletableFuture<UUID> resolveInviteTarget(Player player, String target) {
        UUID parsed = uuid(target);
        if (parsed != null) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> {
                UUID inviteId = findInviteId(body, parsed);
                return inviteId == null ? parsed : inviteId;
            });
        }
        Player online = plugin.getServer().getPlayerExact(target);
        if (online != null) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> findInviteId(body, online.getUniqueId()));
        }
        return coreApiClient.playerInfoByName(target)
            .handle((body, error) -> error == null ? uuid(text(body, "playerUuid")) : null)
            .thenCompose(playerUuid -> {
                if (playerUuid == null) {
                    return resolveInviteIslandName(player, target);
                }
                return coreApiClient.listPendingInvites(player.getUniqueId()).thenCompose(invites -> {
                    UUID inviteId = findInviteId(invites, playerUuid);
                    return inviteId == null ? resolveInviteIslandName(player, target) : CompletableFuture.completedFuture(inviteId);
                });
            });
    }

    private CompletableFuture<UUID> resolveInviteIslandName(Player player, String islandName) {
        return coreApiClient.islandInfoByName(islandName)
            .thenCompose(body -> coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(invites -> findInviteId(invites, uuid(text(body, "islandId")))));
    }

    private UUID findInviteId(String body, UUID targetUuid) {
        if (body == null || targetUuid == null) {
            return null;
        }
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            UUID inviteId = uuid(text(object, "inviteId"));
            if (targetUuid.equals(inviteId) || targetUuid.equals(uuid(text(object, "islandId"))) || targetUuid.equals(uuid(text(object, "inviterUuid")))) {
                return inviteId;
            }
            index = objectEnd + 1;
        }
        return null;
    }

    private void removeIslandMember(Player player, String target) {
        currentIsland(player, "섬 안에서만 멤버를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                message(player, routeMessage("member-remove-denied", "섬 멤버를 추방할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.member.remove", () -> coreApiClient.removeIslandMemberResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 멤버 제거", targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 멤버를 제거하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void setIslandMemberRole(Player player, String target, IslandRole role, String successMessage) {
        setIslandMemberRole(player, target, role.name(), successMessage);
    }

    private void setIslandMemberRole(Player player, String target, String roleKey, String successMessage) {
        currentIsland(player, "섬 안에서만 멤버 역할을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutate("island.member.role.set", () -> coreApiClient.setIslandMemberResult(islandId, player.getUniqueId(), targetUuid, roleKey))
                    .thenAccept(body -> message(player, actionResultMessage(successMessage, targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 멤버 역할을 변경하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void trustIslandMemberTemporary(Player player, String target, String duration) {
        long seconds = parseDurationSeconds(duration, 3600L);
        if (seconds <= 0L) {
            message(player, "신뢰 기간을 올바르게 입력해주세요. 예: 30m, 2h, 1d");
            return;
        }
        currentIsland(player, "섬 안에서만 임시 신뢰를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutate("island.member.temp-trust", () -> coreApiClient.trustIslandMemberTemporary(islandId, player.getUniqueId(), targetUuid, seconds))
                    .thenAccept(body -> message(player, actionResultMessage("섬 임시 신뢰 설정 " + formatDuration(seconds), targetUuid, body) + " 만료=" + text(body, "expiresAt")))
                    .exceptionally(error -> {
                        message(player, "섬 임시 신뢰를 설정하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void transferIslandOwnership(Player player, String target) {
        currentIsland(player, "섬 안에서만 소유권을 양도할 수 있습니다.").ifPresent(islandId -> {
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.ownership.transfer", () -> coreApiClient.transferIslandOwnershipResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 소유권 양도", targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 소유권을 양도하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void banIslandVisitor(Player player, String target, String reason) {
        currentIsland(player, "섬 안에서만 방문자를 밴할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                message(player, routeMessage("visitor-ban-denied", "섬 방문자를 밴할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.ban", () -> coreApiClient.banIslandVisitorResult(islandId, player.getUniqueId(), targetUuid, reason))
                    .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (resultRejected(body)) {
                            player.sendMessage(playerMessage(actionResultMessage("섬 방문자 밴", targetUuid, body)));
                            return;
                        }
                        moveVisitorToFallback(islandId, targetUuid, "섬에서 밴되어 로비로 이동합니다.", "섬에서 밴되어 로비로 이동하지 못했습니다.");
                        player.sendMessage(playerMessage(actionResultMessage("섬 방문자 밴", targetUuid, body)));
                    }))
                    .exceptionally(error -> {
                        message(player, "섬 방문자를 밴하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void pardonIslandVisitor(Player player, String target) {
        currentIsland(player, "섬 안에서만 방문자 밴을 해제할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                message(player, routeMessage("visitor-pardon-denied", "섬 방문자 밴을 해제할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.pardon", () -> coreApiClient.pardonIslandVisitorResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 방문자 밴 해제", targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 방문자 밴을 해제하지 못했습니다.");
                        return null;
                    });
                });
        });
    }

    private void kickIslandVisitor(Player player, String target) {
        currentIsland(player, "섬 안에서만 방문자를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.KICK_VISITOR)) {
                message(player, routeMessage("visitor-kick-denied", "섬 방문자를 추방할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.kick", () -> coreApiClient.kickIslandVisitorResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (resultRejected(body)) {
                            player.sendMessage(playerMessage(actionResultMessage("섬 방문자 추방", targetUuid, body)));
                            return;
                        }
                        if (plugin.getServer().getPlayer(targetUuid) == null) {
                            message(player, routeMessage("visitor-kick-target-offline", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 온라인이 아닙니다."));
                            return;
                        }
                        if (!moveVisitorToFallback(islandId, targetUuid, "섬에서 추방되어 로비로 이동합니다.", "섬에서 추방되어 로비로 이동하지 못했습니다.")) {
                            message(player, routeMessage("visitor-kick-target-not-on-island", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 이 섬에 없습니다."));
                            return;
                        }
                        player.sendMessage(playerMessage(actionResultMessage("섬 방문자 추방", targetUuid, body)));
                    }))
                    .exceptionally(error -> {
                        message(player, "섬 방문자를 추방하지 못했습니다.");
                        return null;
                    });
            }).exceptionally(error -> {
                message(player, "대상 플레이어를 찾지 못했습니다.");
                return null;
            });
        });
    }

    private boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
        Player targetPlayer = plugin.getServer().getPlayer(targetUuid);
        if (targetPlayer == null) {
            return false;
        }
        UUID targetIslandId = protection.islandAt(targetPlayer.getLocation().getBlock()).orElse(null);
        if (!islandId.equals(targetIslandId)) {
            return false;
        }
        routingCommands.connectPlayerToFallback(targetPlayer, successMessage, failureMessage);
        return true;
    }

    private void listIslandBans(Player player) {
        currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandBans(islandId)
                .thenAccept(body -> message(player, banListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 밴 목록을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandBanMenu(Player player) {
        currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.").ifPresent(islandId -> IslandBanMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        java.util.Optional<UUID> islandId = protection.islandAt(player.getLocation().getBlock());
        if (islandId.isEmpty()) {
            player.sendMessage(missingMessage);
        }
        return islandId;
    }

    private boolean allowed(Player player, IslandPermission permission) {
        Location location = player.getLocation();
        return protection.checkBlock(
            player.getUniqueId(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            permission,
            player.isOp()
        ).allowed();
    }

    private boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, String islandInfo) {
        return point.publicAccess()
            && bool(islandInfo, "publicAccess")
            && protection.checkSystemFlag(player.getLocation().getBlock(), IslandFlag.PUBLIC_WARPS).allowed();
    }

    private IslandLocation location(Location location) {
        java.util.Optional<IslandRegion> region = protection.regionAt(location.getBlock());
        return new IslandLocation(
            "",
            region.map(value -> location.getX() - value.originX()).orElse(location.getX()),
            location.getY(),
            region.map(value -> location.getZ() - value.originZ()).orElse(location.getZ()),
            location.getYaw(),
            location.getPitch()
        );
    }

    private String pointListMessage(String body, String label, String emptyMessage) {
        List<String> names = names(body);
        return names.isEmpty() ? emptyMessage : label + ": " + String.join(", ", names);
    }

    private String memberListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String playerUuid = text(object, "playerUuid");
            String role = text(object, "role");
            String expiresAt = text(object, "expiresAt");
            if (!playerUuid.isBlank()) {
                entries.add(compactId(playerUuid) + (role.isBlank() ? "" : " 역할=" + role) + (expiresAt.isBlank() ? "" : " 만료=" + expiresAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 멤버가 없습니다." : "섬 멤버: " + String.join(", ", entries);
    }

    private String inviteListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = text(object, "inviteId");
            String islandId = text(object, "islandId");
            String inviterUuid = text(object, "inviterUuid");
            if (!inviteId.isBlank()) {
                entries.add(compactId(inviteId) + (islandId.isBlank() ? "" : " 섬=" + compactId(islandId)) + (inviterUuid.isBlank() ? "" : " 초대한사람=" + compactId(inviterUuid)));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private String banListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String bannedUuid = text(object, "bannedUuid");
            String reason = text(object, "reason");
            if (!bannedUuid.isBlank()) {
                entries.add(bannedUuid + (reason.isBlank() ? "" : " " + reason));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 밴 목록이 비어 있습니다." : "섬 밴 목록: " + String.join(", ", entries);
    }

    private String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long number(String value, long fallback) {
        return longValue(value, fallback);
    }

    private long parseDurationSeconds(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (normalized.endsWith("m")) {
            multiplier = 60L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("h")) {
            multiplier = 3600L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("d")) {
            multiplier = 86400L;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        long amount = longValue(normalized, -1L);
        if (amount <= 0L) {
            return -1L;
        }
        return Math.max(60L, Math.min(amount * multiplier, 2_592_000L));
    }

    private String formatDuration(long seconds) {
        if (seconds % 86400L == 0L) {
            return (seconds / 86400L) + "d";
        }
        if (seconds % 3600L == 0L) {
            return (seconds / 3600L) + "h";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private UUID playerUuid(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID parsed = uuid(value);
        if (parsed != null) {
            return parsed;
        }
        return plugin.getServer().getOfflinePlayer(value).getUniqueId();
    }

    private CompletableFuture<UUID> resolvePlayerUuid(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        UUID parsed = uuid(value);
        if (parsed != null) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.playerInfoByName(value)
            .thenApply(body -> {
                UUID profileUuid = uuid(text(body, "playerUuid"));
                return profileUuid == null ? plugin.getServer().getOfflinePlayer(value).getUniqueId() : profileUuid;
            })
            .exceptionally(error -> plugin.getServer().getOfflinePlayer(value).getUniqueId());
    }

    private IslandHomeWarpCommandHandler.Point point(String body, String requestedName, String fallbackWorldName) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String target = requestedName == null || requestedName.isBlank() ? "default" : requestedName;
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                return null;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                return null;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            if (target.equalsIgnoreCase(text(object, "name"))) {
                return new IslandHomeWarpCommandHandler.Point(
                    text(object, "worldName").isBlank() ? fallbackWorldName : text(object, "worldName"),
                    decimal(object, "localX"),
                    decimal(object, "localY"),
                    decimal(object, "localZ"),
                    (float) decimal(object, "yaw"),
                    (float) decimal(object, "pitch"),
                    bool(object, "publicAccess")
                );
            }
            index = objectEnd + 1;
        }
        return null;
    }

    private void moveToPoint(Player player, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (point == null) {
                player.sendMessage(missingMessage);
                return;
            }
            java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
            String worldName = region.map(IslandRegion::world).orElse(point.worldName());
            World world = worlds.world(worldName);
            if (world == null) {
                message(player, routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."));
                return;
            }
            double targetX = region.map(value -> value.originX() + point.x()).orElse(point.x());
            double targetZ = region.map(value -> value.originZ() + point.z()).orElse(point.z());
            players.teleport(player, new Location(world, targetX, point.y(), targetZ, point.yaw(), point.pitch()));
            player.sendMessage(successMessage);
        });
    }

    private boolean teleportLocalDefaultHome(Player player) {
        java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
        if (region.isEmpty()) {
            return false;
        }
        IslandRegion current = region.get();
        moveToPoint(
            player,
            new IslandHomeWarpCommandHandler.Point(current.world(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, false),
            routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."),
            routeMessage("core-service-home-fallback", CoreApiDegradedModePolicy.HOME_FALLBACK_MESSAGE)
        );
        return true;
    }

    private String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private double decimal(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length()) {
            char current = json.charAt(end);
            if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.') {
                end++;
                continue;
            }
            break;
        }
        try {
            return Double.parseDouble(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private boolean bool(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return false;
        }
        int valueStart = start + needle.length();
        return json.startsWith("true", valueStart);
    }

    private String inviteActionMessage(String label, UUID inviteId, String body) {
        return actionResultMessage(label, inviteId, body);
    }

    private String actionResultMessage(String label, UUID targetId, String body) {
        return actionResultMessage(label, targetId == null ? "" : targetId.toString(), body);
    }

    private String actionResultMessage(String label, String targetId, String body) {
        boolean rejected = resultRejected(body);
        String code = text(body == null ? "" : body, "code");
        StringBuilder builder = new StringBuilder(label)
            .append(rejected ? " 실패" : " 완료");
        if (targetId != null && !targetId.isBlank()) {
            builder.append(": 대상=").append(compactId(targetId));
        }
        if (!code.isBlank()) {
            builder.append(" 사유=").append(code);
        }
        return builder.toString();
    }

    private boolean resultRejected(String body) {
        return body == null || body.contains("\"error\"") || body.contains("\"accepted\":false") || body.contains("\"applied\":false");
    }

    private String compactId(String value) {
        return value != null && value.length() == 36 && value.indexOf('-') > 0 ? value.substring(0, 8) : value;
    }

    private List<String> names(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        String needle = "\"name\":\"";
        int index = 0;
        while (index < body.length()) {
            int start = body.indexOf(needle, index);
            if (start < 0) {
                break;
            }
            int valueStart = start + needle.length();
            int end = jsonStringEnd(body, valueStart);
            if (end < 0) {
                break;
            }
            names.add(unescape(body.substring(valueStart, end)));
            index = end + 1;
        }
        return names;
    }

    private int jsonStringEnd(String value, int start) {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped) {
                if (current == '\\') {
                    escaped = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(current);
            }
            escaped = false;
        }
        return builder.toString();
    }

    private void message(Player player, String message) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(playerMessage(message)));
    }

    private String playerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 요청을 처리하지 못했습니다." : message;
        return PlayerRouteMessagePolicy.sanitize(value);
    }

    private String coreWriteFailureMessage(Throwable error, String fallback) {
        return coreUnavailable(error)
            ? routeMessage("core-service-maintenance", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE)
            : fallback;
    }

    private boolean coreUnavailable(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof CoreApiException exception) {
            return exception.status() == 0 || exception.status() >= 500;
        }
        return current instanceof java.io.IOException
            || current instanceof java.net.ConnectException
            || current instanceof java.net.http.HttpTimeoutException
            || current instanceof java.net.http.HttpConnectTimeoutException;
    }
}

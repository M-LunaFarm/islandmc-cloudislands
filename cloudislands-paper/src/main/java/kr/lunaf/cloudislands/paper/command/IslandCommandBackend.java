package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class IslandCommandBackend {
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
    private final IslandCommandRouter router;
    private final IslandCommandMessenger commandMessages;
    private final IslandCommandIslandContext islandContext;
    private final IslandCommandLocalTeleports localTeleports;
    private final IslandCommandConfirmations confirmations;
    private final IslandCommandMemberPresentation memberPresentation;
    private final MemberManagementUseCase memberManagement;
    private final IslandCommandPlayerResolver playerResolver;

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
        this.commandMessages = new IslandCommandMessenger(plugin, messages, locales);
        this.islandContext = new IslandCommandIslandContext(protection);
        this.memberManagement = new MemberManagementUseCase(coreApiClient);
        this.playerResolver = new IslandCommandPlayerResolver(plugin, memberManagement);
        this.localTeleports = new IslandCommandLocalTeleports(plugin, protection, players, worlds, commandMessages);
        this.confirmations = new IslandCommandConfirmations(commandMessages);
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
        this.memberPresentation = new IslandCommandMemberPresentation(plugin, coreApiClient, protection, commandMessages, islandContext, routingCommands);
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
            public boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
                return IslandCommandBackend.this.publicWarpAllowed(player, point, islandPublicAccess);
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
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
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
            public boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
                return IslandCommandBackend.this.moveVisitorToFallback(islandId, targetUuid, successMessage, failureMessage);
            }

            @Override
            public String playerMessage(String message) {
                return IslandCommandBackend.this.playerMessage(message);
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
            public void openIslandBanMenu(Player player) {
                IslandCommandBackend.this.openIslandBanMenu(player);
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
            public void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue, String expectedVersion) {
                permissionCommands.stageIslandPermission(player, roleName, permissionName, allowedValue, expectedVersion);
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
        this.router = IslandCommandRouterFactory.create(
            bankCommands,
            snapshotCommands,
            warehouseCommands,
            chatLogCommands,
            progressionCommands,
            environmentCommands,
            settingsCommands,
            homeWarpCommands,
            visitReviewCommands,
            lifecycleCommands,
            overviewCommands,
            membershipCommands,
            adminCommands,
            commandMessages
        );
    }

    private <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return IslandCommandRuntimeSupport.mutate(auditAction, operation);
    }

    private <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return IslandCommandRuntimeSupport.mutateIdempotent(auditAction, operation);
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return router.handleCommand(sender, command, label, args);
    }

    void executeGuiAction(Player player, GuiAction action, GuiClick click) {
        router.handleGuiAction(player, action, click);
    }

    private void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
        confirmations.open(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
    }

    private boolean confirmationAccepted(Player player, GuiAction action, GuiClick click) {
        return confirmations.accepted(player, action, click);
    }

    private String playerCodeMessage(String code, String fallback) {
        return commandMessages.playerCodeMessage(code, fallback);
    }

    private MessageRenderer messagesFor(Player player) {
        return commandMessages.messagesFor(player);
    }

    private String routeMessage(String key, String fallback, String... variables) {
        return commandMessages.routeMessage(key, fallback, variables);
    }

    private String routeMessage(Player player, String key, String fallback, String... variables) {
        return commandMessages.routeMessage(player, key, fallback, variables);
    }

    public void onQuit(PlayerQuitEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    public void onKick(PlayerKickEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    private void openIslandMemberMenu(Player player) {
        memberPresentation.openMemberMenu(player);
    }

    private void openIslandMemberMenu(Player player, int page) {
        memberPresentation.openMemberMenu(player, page);
    }

    private boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
        return memberPresentation.moveVisitorToFallback(islandId, targetUuid, successMessage, failureMessage);
    }

    private void openIslandBanMenu(Player player) {
        memberPresentation.openBanMenu(player);
    }

    private java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        return islandContext.currentIsland(player, missingMessage);
    }

    private boolean allowed(Player player, IslandPermission permission) {
        return islandContext.allowed(player, permission);
    }

    private boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
        return islandContext.publicWarpAllowed(player, point, islandPublicAccess);
    }

    private IslandLocation location(Location location) {
        return islandContext.location(location);
    }

    private String joined(String[] args, int start) {
        return IslandCommandArgs.joined(args, start);
    }

    private int integer(String value, int fallback) {
        return IslandCommandArgs.integer(value, fallback);
    }

    private long longValue(String value, long fallback) {
        return IslandCommandArgs.longValue(value, fallback);
    }

    private CompletableFuture<UUID> resolvePlayerUuid(String value) {
        return playerResolver.resolvePlayerUuid(value);
    }

    private void moveToPoint(Player player, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
        localTeleports.moveToPoint(player, point, missingMessage, successMessage);
    }

    private boolean teleportLocalDefaultHome(Player player) {
        return localTeleports.teleportLocalDefaultHome(player);
    }

    private void message(Player player, String message) {
        commandMessages.message(player, message);
    }

    private String playerMessage(String message) {
        return commandMessages.playerMessage(message);
    }

    private String coreWriteFailureMessage(Throwable error, String fallback) {
        return IslandCommandRuntimeSupport.coreWriteFailureMessage(
            coreUnavailable(error),
            routeMessage("core-service-maintenance", IslandCommandRuntimeSupport.maintenanceFallback()),
            fallback
        );
    }

    private boolean coreUnavailable(Throwable error) {
        return IslandCommandRuntimeSupport.coreUnavailable(error);
    }
}

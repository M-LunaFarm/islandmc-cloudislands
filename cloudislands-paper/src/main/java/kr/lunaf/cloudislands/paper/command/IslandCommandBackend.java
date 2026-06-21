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
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase.MemberActionResult;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase;
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
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;
    private final MemberManagementUseCase memberManagement;

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
        this.memberManagement = new MemberManagementUseCase(coreApiClient);
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
            public void openIslandMemberMenu(Player player) {
                IslandCommandBackend.this.openIslandMemberMenu(player);
            }

            @Override
            public void openIslandMemberMenu(Player player, int page) {
                IslandCommandBackend.this.openIslandMemberMenu(player, page);
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
        this.messages = messages;
        this.locales = locales;
        this.players = players;
        this.worlds = worlds;
        this.router = new IslandCommandRouter(
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
            new IslandCommandRouter.Runtime() {
                @Override
                public void message(Player player, String message) {
                    IslandCommandBackend.this.message(player, message);
                }

                @Override
                public String routeMessage(String key, String fallback) {
                    return IslandCommandBackend.this.routeMessage(key, fallback);
                }
            }
        );
    }

    private <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.request(auditAction), operation);
    }

    private <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction), operation);
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return router.handleCommand(sender, command, label, args);
    }

    void executeGuiAction(Player player, GuiAction action, GuiClick click) {
        router.handleGuiAction(player, action, click);
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

    public void onQuit(PlayerQuitEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    public void onKick(PlayerKickEvent event) {
        routingCommands.clearRouteLoading(event.getPlayer());
    }

    private void openIslandMemberMenu(Player player) {
        openIslandMemberMenu(player, 0);
    }

    private void openIslandMemberMenu(Player player, int page) {
        currentIsland(player, "섬 안에서만 멤버 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMemberMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player), page));
    }

    private void banIslandVisitor(Player player, String target, String reason) {
        currentIsland(player, "섬 안에서만 방문자를 밴할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                message(player, routeMessage("visitor-ban-denied", "섬 방문자를 밴할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.ban", () -> memberManagement.banVisitorAction(islandId, player.getUniqueId(), targetUuid, reason))
                    .thenAccept(result -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (!result.accepted()) {
                            player.sendMessage(playerMessage(memberActionMessage("섬 방문자 밴", targetUuid, result)));
                            return;
                        }
                        moveVisitorToFallback(islandId, targetUuid, "섬에서 밴되어 로비로 이동합니다.", "섬에서 밴되어 로비로 이동하지 못했습니다.");
                        player.sendMessage(playerMessage(memberActionMessage("섬 방문자 밴", targetUuid, result)));
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
                mutateIdempotent("island.visitor.pardon", () -> memberManagement.pardonVisitorAction(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(result -> message(player, memberActionMessage("섬 방문자 밴 해제", targetUuid, result)))
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
                mutateIdempotent("island.visitor.kick", () -> memberManagement.kickVisitorAction(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(result -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (!result.accepted()) {
                            player.sendMessage(playerMessage(memberActionMessage("섬 방문자 추방", targetUuid, result)));
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
                        player.sendMessage(playerMessage(memberActionMessage("섬 방문자 추방", targetUuid, result)));
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

    private boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
        return point.publicAccess()
            && islandPublicAccess
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
        return memberManagement.playerUuidByName(value)
            .thenApply(profileUuid -> profileUuid == null ? plugin.getServer().getOfflinePlayer(value).getUniqueId() : profileUuid)
            .exceptionally(error -> plugin.getServer().getOfflinePlayer(value).getUniqueId());
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

    private String memberActionMessage(String label, UUID targetId, MemberActionResult result) {
        return actionStatusMessage(label, targetId == null ? "" : targetId.toString(), result != null && result.accepted(), result == null ? "" : result.code());
    }

    private String actionStatusMessage(String label, String targetId, boolean accepted, String code) {
        StringBuilder builder = new StringBuilder(label)
            .append(accepted ? " 완료" : " 실패");
        if (targetId != null && !targetId.isBlank()) {
            builder.append(": 대상=").append(compactId(targetId));
        }
        if (!accepted && code != null && !code.isBlank()) {
            builder.append(" 사유=").append(code);
        }
        return builder.toString();
    }

    private String compactId(String value) {
        return value != null && value.length() == 36 && value.indexOf('-') > 0 ? value.substring(0, 8) : value;
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

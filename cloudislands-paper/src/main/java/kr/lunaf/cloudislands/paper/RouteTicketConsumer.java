package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RoutingCommandClient;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.event.IslandPreVisitEvent;
import kr.lunaf.cloudislands.paper.event.IslandVisitEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketConsumedEvent;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.SafeTeleportResolver;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy;
import kr.lunaf.cloudislands.protocol.route.RoutePreparationProgressPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class RouteTicketConsumer {
    private final Plugin plugin;
    private final RoutingCommandClient routingCommands;
    private final String nodeId;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;
    private final java.util.Map<UUID, LoadingBar> loadingBars = new ConcurrentHashMap<>();
    private final AtomicLong consumeRetries = new AtomicLong();
    private final AtomicLong consumeFailures = new AtomicLong();
    private final AtomicLong worldWaitRetries = new AtomicLong();
    private final AtomicLong teleportAttempts = new AtomicLong();
    private final AtomicLong teleportSuccesses = new AtomicLong();
    private final AtomicLong teleportFailures = new AtomicLong();
    private volatile String lastFailureReason = "";
    private volatile String lastTargetType = "";
    private volatile ActiveIslandRegistry activeIslands;
    private volatile MessageRenderer messages;

    public RouteTicketConsumer(Plugin plugin, CoreApiClient coreApiClient, String nodeId) {
        this(plugin, coreApiClient, nodeId, new BukkitPlayerGateway(), new BukkitWorldGateway(plugin));
    }

    RouteTicketConsumer(Plugin plugin, CoreApiClient coreApiClient, String nodeId, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this.plugin = plugin;
        this.routingCommands = coreApiClient.routingCommands();
        this.nodeId = nodeId;
        this.players = players;
        this.worlds = worlds;
    }

    public void setActiveIslands(ActiveIslandRegistry activeIslands) {
        this.activeIslands = activeIslands;
    }

    public void setMessages(MessageRenderer messages) {
        this.messages = messages;
    }

    public void clearLoading(UUID playerUuid) {
        loadingBars.remove(playerUuid);
    }

    public void consumeAndTeleport(PlayerConnectionSession playerSession, UUID ticketId, String nonce) {
        consumeAndTeleport(playerSession, ticketId, nonce, 0);
    }

    public CompletableFuture<Boolean> teleportToWorldSpawn(PlayerConnectionSession playerSession, String worldName) {
        return PaperSchedulers.supply(plugin, () -> worlds.worldSpawn(worldName))
            .thenCompose(target -> target == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : worlds.safeDestination(target, null))
            .thenCompose(destination -> PaperSchedulers.supply(plugin, () -> {
                Player player = currentPlayer(playerSession);
                return player != null && destination.isPresent()
                    && SafeTeleportResolver.isSafe(destination.get(), null)
                    && players.teleport(player, destination.get());
            }));
    }

    private void consumeAndTeleport(PlayerConnectionSession playerSession, UUID ticketId, String nonce, int attempt) {
        UUID playerUuid = playerSession.playerUuid();
        if (currentPlayer(playerSession) == null) {
            recordFailure("PLAYER_DISCONNECTED");
            clearRoute(playerSession, ticketId, "PLAYER_DISCONNECTED");
            return;
        }
        if (attempt == 0 || attempt % 5 == 0) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> notifyPreparing(playerSession, attempt));
        }
        routingCommands.consumeTicket(ticketId, playerUuid, nodeId, nonce).thenAccept(ticket -> {
            if (ticket.isPresent()) {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> teleport(playerSession, ticket.get(), 0));
                return;
            }
            if (attempt < 20) {
                consumeRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> consumeAndTeleport(playerSession, ticketId, nonce, attempt + 1), 20L);
            } else {
                recordConsumeFailure("TICKET_NOT_READY");
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> failRoute(playerSession, ticketId, "TICKET_NOT_READY", true));
            }
        }).exceptionally(error -> {
            if (attempt < 20) {
                consumeRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> consumeAndTeleport(playerSession, ticketId, nonce, attempt + 1), 20L);
            } else {
                recordConsumeFailure("CONSUME_EXCEPTION");
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> failRoute(playerSession, ticketId, "CONSUME_EXCEPTION", true));
            }
            return null;
        });
    }

    private void teleport(PlayerConnectionSession playerSession, RouteTicket ticket, int attempt) {
        UUID playerUuid = playerSession.playerUuid();
        Player player = currentPlayer(playerSession);
        String worldName = ticket.targetWorld();
        World world = worlds.world(worldName);
        if (player == null) {
            recordFailure("PLAYER_DISCONNECTED");
            clearRoute(playerSession, ticket.ticketId(), "PLAYER_DISCONNECTED");
            return;
        }
        if (islandTransitioning(ticket.islandId())) {
            recordTeleportFailure("ISLAND_TRANSITION_IN_PROGRESS");
            failRoute(playerSession, ticket.ticketId(), "ISLAND_TRANSITION_IN_PROGRESS", true);
            return;
        }
        if (world == null) {
            if (attempt == 0 || attempt % 5 == 0) {
                notifyPreparing(playerSession, attempt);
            }
            if (attempt < 20) {
                worldWaitRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> teleport(playerSession, ticket, attempt + 1), 20L);
            } else {
                recordTeleportFailure("WORLD_NOT_READY");
                failRoute(playerSession, ticket.ticketId(), "WORLD_NOT_READY", true);
            }
            return;
        }
        java.util.Map<String, String> payload = ticket.payload();
        String placementSource = payload.getOrDefault("placementSource", "");
        if (ticket.action() == RouteAction.VISIT) {
            IslandPreVisitEvent preVisit = new IslandPreVisitEvent(ticket.islandId(), playerUuid, player, worldName, placementSource);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(preVisit);
            if (preVisit.isCancelled()) {
                recordTeleportFailure("VISIT_CANCELLED");
                player.sendActionBar(playerComponent(player, "route-visit-cancelled", "섬 방문이 취소되었습니다."));
                failRoute(playerSession, ticket.ticketId(), "VISIT_CANCELLED", true);
                return;
            }
        }
        Optional<Location> maybeTarget = targetLocation(world, ticket, payload);
        if (maybeTarget.isEmpty()) {
            recordTeleportFailure("ACTIVE_ISLAND_ORIGIN_MISSING");
            failRoute(playerSession, ticket.ticketId(), "ACTIVE_ISLAND_ORIGIN_MISSING", true);
            return;
        }
        Location requested = maybeTarget.get();
        IslandRegion targetRegion = targetRegion(ticket.islandId());
        if (targetRegion == null) {
            recordTeleportFailure("ACTIVE_ISLAND_REGION_MISSING");
            failRoute(playerSession, ticket.ticketId(), "ACTIVE_ISLAND_REGION_MISSING", true);
            return;
        }
        worlds.safeDestination(requested, targetRegion).whenComplete((destination, error) ->
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                if (error != null || destination == null || destination.isEmpty()) {
                    recordTeleportFailure("UNSAFE_TELEPORT_TARGET");
                    failRoute(playerSession, ticket.ticketId(), "UNSAFE_TELEPORT_TARGET", true);
                    return;
                }
                completeTeleport(playerSession, ticket, payload, placementSource, destination.get(), targetRegion);
            })
        );
    }

    private void completeTeleport(PlayerConnectionSession playerSession, RouteTicket ticket, java.util.Map<String, String> payload, String placementSource, Location target, IslandRegion targetRegion) {
        UUID playerUuid = playerSession.playerUuid();
        Player player = currentPlayer(playerSession);
        if (player == null) {
            recordFailure("PLAYER_DISCONNECTED");
            clearRoute(playerSession, ticket.ticketId(), "PLAYER_DISCONNECTED");
            return;
        }
        if (islandTransitioning(ticket.islandId())) {
            recordTeleportFailure("ISLAND_TRANSITION_IN_PROGRESS");
            failRoute(playerSession, ticket.ticketId(), "ISLAND_TRANSITION_IN_PROGRESS", true);
            return;
        }
        if (!SafeTeleportResolver.isSafe(target, targetRegion)) {
            recordTeleportFailure("TELEPORT_TARGET_CHANGED");
            failRoute(playerSession, ticket.ticketId(), "TELEPORT_TARGET_CHANGED", true);
            return;
        }
        teleportAttempts.incrementAndGet();
        lastTargetType = payload.getOrDefault("targetType", ticket.action().name());
        if (players.teleport(player, target)) {
            teleportSuccesses.incrementAndGet();
            hideLoading(playerSession, player);
            player.sendActionBar(arrivalComponent(player, ticket.action()));
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new RouteTicketConsumedEvent(
                    ticket.ticketId(),
                    ticket.islandId(),
                    playerUuid,
                    ticket.action().name(),
                    ticket.targetNode(),
                    routeEventFields(ticket, target, payload)
            ));
            if (ticket.action() == RouteAction.VISIT) {
                kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandVisitEvent(ticket.islandId(), playerUuid, player, ticket.targetWorld(), placementSource));
            }
        } else {
            recordTeleportFailure("BUKKIT_TELEPORT_REJECTED");
            failRoute(playerSession, ticket.ticketId(), "BUKKIT_TELEPORT_REJECTED", true);
        }
    }

    private IslandRegion targetRegion(UUID islandId) {
        ActiveIslandRegistry registry = activeIslands;
        ActiveIslandRegistry.ActiveIsland active = registry == null ? null : registry.find(islandId).orElse(null);
        if (active == null) {
            return null;
        }
        int half = Math.max(1, active.islandSize() / 2);
        return new IslandRegion(
            active.islandId(),
            active.worldName(),
            active.originX() - half,
            active.originX() + half,
            active.originZ() - half,
            active.originZ() + half,
            active.cellX(),
            active.cellZ()
        );
    }

    private boolean islandTransitioning(UUID islandId) {
        ActiveIslandRegistry registry = activeIslands;
        return registry != null && registry.isTransitioning(islandId);
    }

    private Optional<Location> targetLocation(World world, RouteTicket ticket, java.util.Map<String, String> payload) {
        double localX = decimal(payload, "localX", defaultLocalX(ticket.action()));
        double localY = decimal(payload, "localY", defaultLocalY(ticket.action()));
        double localZ = decimal(payload, "localZ", defaultLocalZ(ticket.action()));
        ActiveIslandRegistry registry = activeIslands;
        ActiveIslandRegistry.ActiveIsland active = registry == null ? null : registry.find(ticket.islandId()).orElse(null);
        if (active == null) {
            return Optional.empty();
        }
        double worldX = active.originX() + localX;
        double worldZ = active.originZ() + localZ;
        return Optional.of(new Location(world, worldX, localY, worldZ, (float) decimal(payload, "yaw", 180.0D), (float) decimal(payload, "pitch", 0.0D)));
    }

    private java.util.Map<String, String> routeEventFields(RouteTicket ticket, Location target, java.util.Map<String, String> payload) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("targetWorld", ticket.targetWorld() == null ? "" : ticket.targetWorld());
        fields.put("targetNode", ticket.targetNode() == null ? "" : ticket.targetNode());
        fields.put("targetType", payload.getOrDefault("targetType", ticket.action().name()));
        fields.put("targetResolution", targetResolution(ticket));
        fields.put("teleportDestinationPolicy", "active-island-origin-plus-ticket-local-offset-safe-resolved");
        fields.put("homeName", payload.getOrDefault("homeName", ""));
        fields.put("warpName", payload.getOrDefault("warpName", ""));
        fields.put("localX", Double.toString(decimal(payload, "localX", defaultLocalX(ticket.action()))));
        fields.put("localY", Double.toString(decimal(payload, "localY", defaultLocalY(ticket.action()))));
        fields.put("localZ", Double.toString(decimal(payload, "localZ", defaultLocalZ(ticket.action()))));
        fields.put("worldX", Double.toString(target.getX()));
        fields.put("worldY", Double.toString(target.getY()));
        fields.put("worldZ", Double.toString(target.getZ()));
        fields.put("yaw", Float.toString(target.getYaw()));
        fields.put("pitch", Float.toString(target.getPitch()));
        return fields;
    }

    private String targetResolution(RouteTicket ticket) {
        ActiveIslandRegistry registry = activeIslands;
        return registry != null && registry.find(ticket.islandId()).isPresent() ? "active-island-origin" : "unresolved-active-island-origin";
    }

    private void recordConsumeFailure(String reason) {
        consumeFailures.incrementAndGet();
        recordFailure(reason);
    }

    private void recordTeleportFailure(String reason) {
        teleportFailures.incrementAndGet();
        recordFailure(reason);
    }

    private void recordFailure(String reason) {
        lastFailureReason = reason == null ? "" : reason;
    }

    public long consumeRetries() {
        return consumeRetries.get();
    }

    public long consumeFailures() {
        return consumeFailures.get();
    }

    public long worldWaitRetries() {
        return worldWaitRetries.get();
    }

    public long teleportAttempts() {
        return teleportAttempts.get();
    }

    public long teleportSuccesses() {
        return teleportSuccesses.get();
    }

    public long teleportFailures() {
        return teleportFailures.get();
    }

    public String lastFailureReason() {
        return lastFailureReason;
    }

    public String lastTargetType() {
        return lastTargetType;
    }

    private double defaultLocalX(RouteAction action) {
        return 0.5D;
    }

    private double defaultLocalY(RouteAction action) {
        return 100.0D;
    }

    private double defaultLocalZ(RouteAction action) {
        return action == RouteAction.VISIT ? 2.5D : 0.5D;
    }

    private String arrivalMessage(Player player, kr.lunaf.cloudislands.api.model.RouteAction action) {
        return switch (action) {
            case VISIT -> playerMessage(player, "route-arrived-visit", "방문한 섬에 도착했습니다.");
            case WARP -> playerMessage(player, "route-arrived-warp", "섬 워프에 도착했습니다.");
            case ADMIN_TELEPORT -> playerMessage(player, "route-arrived-admin", "관리자 이동이 완료되었습니다.");
            default -> playerMessage(player, "route-arrived-home", "내 섬에 도착했습니다.");
        };
    }

    private Component arrivalComponent(Player player, kr.lunaf.cloudislands.api.model.RouteAction action) {
        return componentText(player, arrivalMessage(player, action));
    }

    private void notifyPreparing(PlayerConnectionSession playerSession, int attempt) {
        UUID playerUuid = playerSession.playerUuid();
        Player player = currentPlayer(playerSession);
        if (player != null) {
            Component loading = playerComponent(player, "route-consume-loading", "섬 로딩 중");
            LoadingBar loadingBar = loadingBars.compute(playerUuid, (_ignored, current) -> {
                if (current != null && current.playerSession() == playerSession) {
                    return current;
                }
                if (current != null && current.playerSession().isCurrent(player)) {
                    player.hideBossBar(current.bossBar());
                }
                return new LoadingBar(playerSession, BossBar.bossBar(loading, RoutePreparationProgressPolicy.handoffProgress(0), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
            });
            BossBar bar = loadingBar.bossBar();
            bar.name(loading);
            bar.progress(RoutePreparationProgressPolicy.handoffProgress(attempt));
            player.showBossBar(bar);
            player.sendActionBar(playerComponent(player, "route-consume-preparing", "섬을 준비하는 중입니다..."));
        }
    }

    private void notifyRouteFailed(PlayerConnectionSession playerSession) {
        Player player = currentPlayer(playerSession);
        if (player == null) {
            clearLoading(playerSession);
            return;
        }
        hideLoading(playerSession, player);
        player.sendActionBar(playerComponent(player, "route-consume-failed", "섬 이동 준비가 완료되지 않았습니다. 다시 시도해주세요."));
    }

    private String playerMessage(String key, String fallback) {
        return sanitizePlayerMessage(message(key, fallback));
    }

    private String playerMessage(Player player, String key, String fallback) {
        return sanitizePlayerMessage(message(player, key, fallback));
    }

    private Component playerComponent(Player player, String key, String fallback) {
        return componentText(player, playerMessage(player, key, fallback));
    }

    private Component componentText(Player player, String text) {
        MessageRenderer renderer = messages;
        if (renderer == null) {
            return Component.text(text);
        }
        return renderer.componentTextForLocale(PlayerLocaleCache.clientLocale(player), text);
    }

    private String message(String key, String fallback) {
        MessageRenderer renderer = messages;
        if (renderer == null) {
            return fallback;
        }
        String rendered = renderer.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String message(Player player, String key, String fallback) {
        MessageRenderer renderer = messages;
        if (renderer == null) {
            return fallback;
        }
        String rendered = renderer.plainForLocale(PlayerLocaleCache.clientLocale(player), key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String sanitizePlayerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 이동을 처리하지 못했습니다." : message;
        return PlayerRouteMessagePolicy.sanitize(value);
    }

    private void hideLoading(PlayerConnectionSession playerSession, Player player) {
        LoadingBar loadingBar = loadingBars.get(playerSession.playerUuid());
        if (loadingBar != null && loadingBar.playerSession() == playerSession
            && loadingBars.remove(playerSession.playerUuid(), loadingBar)) {
            player.hideBossBar(loadingBar.bossBar());
        }
    }

    private void clearLoading(PlayerConnectionSession playerSession) {
        LoadingBar loadingBar = loadingBars.get(playerSession.playerUuid());
        if (loadingBar != null && loadingBar.playerSession() == playerSession) {
            loadingBars.remove(playerSession.playerUuid(), loadingBar);
        }
    }

    private void clearRoute(PlayerConnectionSession playerSession, UUID ticketId, String reason) {
        routingCommands.clearRoute(playerSession.playerUuid(), ticketId, reason == null || reason.isBlank() ? "ROUTE_FAILED" : reason).exceptionally(error -> null);
        clearLoading(playerSession);
    }

    private void failRoute(PlayerConnectionSession playerSession, UUID ticketId, String reason, boolean clearCoreRoute) {
        if (clearCoreRoute && ticketId != null) {
            clearRoute(playerSession, ticketId, reason);
        } else {
            clearLoading(playerSession);
        }
        notifyRouteFailed(playerSession);
    }

    private Player currentPlayer(PlayerConnectionSession playerSession) {
        Player player = players.onlinePlayer(playerSession.playerUuid());
        return playerSession.isCurrent(player) ? player : null;
    }

    private double decimal(java.util.Map<String, String> payload, String key, double fallback) {
        try {
            double value = Double.parseDouble(payload.getOrDefault(key, Double.toString(fallback)));
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record LoadingBar(PlayerConnectionSession playerSession, BossBar bossBar) {
    }
}

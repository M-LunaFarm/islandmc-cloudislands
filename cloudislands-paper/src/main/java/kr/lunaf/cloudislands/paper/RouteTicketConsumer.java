package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.event.IslandPreVisitEvent;
import kr.lunaf.cloudislands.paper.event.IslandVisitEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketConsumedEvent;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
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
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;
    private final java.util.Map<UUID, BossBar> loadingBars = new ConcurrentHashMap<>();
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
        this.coreApiClient = coreApiClient;
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

    public void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce) {
        consumeAndTeleport(ticketId, playerUuid, nonce, 0);
    }

    private void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce, int attempt) {
        if (players.onlinePlayer(playerUuid) == null) {
            recordFailure("PLAYER_DISCONNECTED");
            clearRoute(playerUuid, ticketId, "PLAYER_DISCONNECTED");
            return;
        }
        if (attempt == 0 || attempt % 5 == 0) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> notifyPreparing(playerUuid, attempt));
        }
        coreApiClient.consumeTicket(ticketId, playerUuid, nodeId, nonce).thenAccept(ticket -> {
            if (ticket.isPresent()) {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> teleport(playerUuid, ticket.get(), 0));
                return;
            }
            if (attempt < 20) {
                consumeRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> consumeAndTeleport(ticketId, playerUuid, nonce, attempt + 1), 20L);
            } else {
                recordConsumeFailure("TICKET_NOT_READY");
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> failRoute(playerUuid, ticketId, "TICKET_NOT_READY", true));
            }
        }).exceptionally(error -> {
            if (attempt < 20) {
                consumeRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> consumeAndTeleport(ticketId, playerUuid, nonce, attempt + 1), 20L);
            } else {
                recordConsumeFailure("CONSUME_EXCEPTION");
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> failRoute(playerUuid, ticketId, "CONSUME_EXCEPTION", true));
            }
            return null;
        });
    }

    private void teleport(UUID playerUuid, RouteTicket ticket, int attempt) {
        Player player = players.onlinePlayer(playerUuid);
        String worldName = ticket.targetWorld();
        World world = worlds.world(worldName);
        if (player == null) {
            recordFailure("PLAYER_DISCONNECTED");
            clearRoute(playerUuid, ticket.ticketId(), "PLAYER_DISCONNECTED");
            return;
        }
        if (world == null) {
            if (attempt == 0 || attempt % 5 == 0) {
                notifyPreparing(playerUuid, attempt);
            }
            if (attempt < 20) {
                worldWaitRetries.incrementAndGet();
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () -> teleport(playerUuid, ticket, attempt + 1), 20L);
            } else {
                recordTeleportFailure("WORLD_NOT_READY");
                failRoute(playerUuid, ticket.ticketId(), "WORLD_NOT_READY", true);
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
                player.sendActionBar(Component.text(playerMessage("route-visit-cancelled", "섬 방문이 취소되었습니다.")));
                failRoute(playerUuid, ticket.ticketId(), "VISIT_CANCELLED", true);
                return;
            }
        }
        Optional<Location> maybeTarget = targetLocation(world, ticket, payload);
        if (maybeTarget.isEmpty()) {
            recordTeleportFailure("ACTIVE_ISLAND_ORIGIN_MISSING");
            failRoute(playerUuid, ticket.ticketId(), "ACTIVE_ISLAND_ORIGIN_MISSING", true);
            return;
        }
        Location target = maybeTarget.get();
        teleportAttempts.incrementAndGet();
        lastTargetType = payload.getOrDefault("targetType", ticket.action().name());
        if (players.teleport(player, target)) {
            teleportSuccesses.incrementAndGet();
            hideLoading(player);
            player.sendActionBar(Component.text(arrivalMessage(ticket.action())));
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new RouteTicketConsumedEvent(
                    ticket.ticketId(),
                    ticket.islandId(),
                    playerUuid,
                    ticket.action().name(),
                    ticket.targetNode(),
                    routeEventFields(ticket, target, payload)
            ));
            if (ticket.action() == RouteAction.VISIT) {
                kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandVisitEvent(ticket.islandId(), playerUuid, player, worldName, placementSource));
            }
        } else {
            recordTeleportFailure("BUKKIT_TELEPORT_REJECTED");
            failRoute(playerUuid, ticket.ticketId(), "BUKKIT_TELEPORT_REJECTED", true);
        }
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
        fields.put("teleportDestinationPolicy", "active-island-origin-plus-ticket-local-offset");
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

    private String arrivalMessage(kr.lunaf.cloudislands.api.model.RouteAction action) {
        return switch (action) {
            case VISIT -> playerMessage("route-arrived-visit", "방문한 섬에 도착했습니다.");
            case WARP -> playerMessage("route-arrived-warp", "섬 워프에 도착했습니다.");
            case ADMIN_TELEPORT -> playerMessage("route-arrived-admin", "관리자 이동이 완료되었습니다.");
            default -> playerMessage("route-arrived-home", "내 섬에 도착했습니다.");
        };
    }

    private void notifyPreparing(UUID playerUuid, int attempt) {
        Player player = players.onlinePlayer(playerUuid);
        if (player != null) {
            BossBar bar = loadingBars.computeIfAbsent(playerUuid, ignored -> BossBar.bossBar(Component.text(playerMessage("route-consume-loading", "섬 로딩 중")), RoutePreparationProgressPolicy.handoffProgress(0), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
            bar.progress(RoutePreparationProgressPolicy.handoffProgress(attempt));
            player.showBossBar(bar);
            player.sendActionBar(Component.text(playerMessage("route-consume-preparing", "섬을 준비하는 중입니다...")));
        }
    }

    private void notifyRouteFailed(UUID playerUuid) {
        Player player = players.onlinePlayer(playerUuid);
        if (player == null) {
            loadingBars.remove(playerUuid);
            return;
        }
        hideLoading(player);
        player.sendActionBar(Component.text(playerMessage("route-consume-failed", "섬 이동 준비가 완료되지 않았습니다. 다시 시도해주세요.")));
    }

    private String playerMessage(String key, String fallback) {
        return sanitizePlayerMessage(message(key, fallback));
    }

    private String message(String key, String fallback) {
        MessageRenderer renderer = messages;
        if (renderer == null) {
            return fallback;
        }
        String rendered = renderer.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String sanitizePlayerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 이동을 처리하지 못했습니다." : message;
        return PlayerRouteMessagePolicy.sanitize(value);
    }

    private void hideLoading(Player player) {
        BossBar bar = loadingBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void clearRoute(UUID playerUuid, UUID ticketId, String reason) {
        coreApiClient.clearRoute(playerUuid, ticketId, reason == null || reason.isBlank() ? "ROUTE_FAILED" : reason).exceptionally(error -> null);
        loadingBars.remove(playerUuid);
    }

    private void failRoute(UUID playerUuid, UUID ticketId, String reason, boolean clearCoreRoute) {
        if (clearCoreRoute && ticketId != null) {
            clearRoute(playerUuid, ticketId, reason);
        } else {
            loadingBars.remove(playerUuid);
        }
        notifyRouteFailed(playerUuid);
    }

    private double decimal(java.util.Map<String, String> payload, String key, double fallback) {
        try {
            double value = Double.parseDouble(payload.getOrDefault(key, Double.toString(fallback)));
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

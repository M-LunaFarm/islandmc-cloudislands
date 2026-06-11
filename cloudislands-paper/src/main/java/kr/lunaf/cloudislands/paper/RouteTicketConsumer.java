package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.event.IslandPreVisitEvent;
import kr.lunaf.cloudislands.paper.event.IslandVisitEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketConsumedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;

public final class RouteTicketConsumer {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final String nodeId;

    public RouteTicketConsumer(Plugin plugin, CoreApiClient coreApiClient, String nodeId) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
    }

    public void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce) {
        consumeAndTeleport(ticketId, playerUuid, nonce, 0);
    }

    private void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce, int attempt) {
        coreApiClient.consumeTicket(ticketId, playerUuid, nodeId, nonce).thenAccept(ticket -> {
            if (ticket.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> teleport(playerUuid, ticket.get()));
                return;
            }
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> consumeAndTeleport(ticketId, playerUuid, nonce, attempt + 1), 20L);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> notifyRouteFailed(playerUuid));
            }
        }).exceptionally(error -> {
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> consumeAndTeleport(ticketId, playerUuid, nonce, attempt + 1), 20L);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> notifyRouteFailed(playerUuid));
            }
            return null;
        });
    }

    private void teleport(UUID playerUuid, RouteTicket ticket) {
        Player player = Bukkit.getPlayer(playerUuid);
        String worldName = ticket.targetWorld();
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (player == null || world == null) {
            return;
        }
        if (ticket.action() == RouteAction.VISIT) {
            IslandPreVisitEvent preVisit = new IslandPreVisitEvent(ticket.islandId(), playerUuid, player, worldName);
            Bukkit.getPluginManager().callEvent(preVisit);
            if (preVisit.isCancelled()) {
                player.sendActionBar(Component.text("섬 방문이 취소되었습니다."));
                return;
            }
        }
        java.util.Map<String, String> payload = ticket.payload();
        if (player.teleport(new Location(world, decimal(payload, "localX", 0.5D), decimal(payload, "localY", 100.0D), decimal(payload, "localZ", 0.5D), (float) decimal(payload, "yaw", 180.0D), (float) decimal(payload, "pitch", 0.0D)))) {
            player.sendActionBar(Component.text(arrivalMessage(ticket.action())));
            Bukkit.getPluginManager().callEvent(new RouteTicketConsumedEvent(ticket.islandId(), ticket.ticketId(), playerUuid, player, ticket.action(), worldName));
            if (ticket.action() == RouteAction.VISIT) {
                Bukkit.getPluginManager().callEvent(new IslandVisitEvent(ticket.islandId(), playerUuid, player, worldName));
            }
        }
    }

    private String arrivalMessage(kr.lunaf.cloudislands.api.model.RouteAction action) {
        return switch (action) {
            case VISIT -> "방문한 섬에 도착했습니다.";
            case WARP -> "섬 워프에 도착했습니다.";
            case ADMIN_TELEPORT -> "관리자 이동이 완료되었습니다.";
            default -> "내 섬에 도착했습니다.";
        };
    }

    private void notifyRouteFailed(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendActionBar(Component.text("섬 이동 정보를 확인하지 못했습니다."));
        }
    }

    private double decimal(java.util.Map<String, String> payload, String key, double fallback) {
        try {
            return Double.parseDouble(payload.getOrDefault(key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

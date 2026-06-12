package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
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
    private volatile ActiveIslandRegistry activeIslands;

    public RouteTicketConsumer(Plugin plugin, CoreApiClient coreApiClient, String nodeId) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
    }

    public void setActiveIslands(ActiveIslandRegistry activeIslands) {
        this.activeIslands = activeIslands;
    }

    public void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce) {
        consumeAndTeleport(ticketId, playerUuid, nonce, 0);
    }

    private void consumeAndTeleport(UUID ticketId, UUID playerUuid, String nonce, int attempt) {
        if (attempt == 0 || attempt % 5 == 0) {
            Bukkit.getScheduler().runTask(plugin, () -> notifyPreparing(playerUuid));
        }
        coreApiClient.consumeTicket(ticketId, playerUuid, nodeId, nonce).thenAccept(ticket -> {
            if (ticket.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> teleport(playerUuid, ticket.get(), 0));
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

    private void teleport(UUID playerUuid, RouteTicket ticket, int attempt) {
        Player player = Bukkit.getPlayer(playerUuid);
        String worldName = ticket.targetWorld();
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (player == null || world == null) {
            if (attempt == 0 || attempt % 5 == 0) {
                notifyPreparing(playerUuid);
            }
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> teleport(playerUuid, ticket, attempt + 1), 20L);
            } else {
                notifyRouteFailed(playerUuid);
            }
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
        Location target = targetLocation(world, ticket, payload);
        if (player.teleport(target)) {
            player.sendActionBar(Component.text(arrivalMessage(ticket.action())));
            Bukkit.getPluginManager().callEvent(new RouteTicketConsumedEvent(ticket.islandId(), ticket.ticketId(), playerUuid, player, ticket.action(), worldName));
            if (ticket.action() == RouteAction.VISIT) {
                Bukkit.getPluginManager().callEvent(new IslandVisitEvent(ticket.islandId(), playerUuid, player, worldName));
            }
        } else {
            notifyRouteFailed(playerUuid);
        }
    }

    private Location targetLocation(World world, RouteTicket ticket, java.util.Map<String, String> payload) {
        double localX = decimal(payload, "localX", defaultLocalX(ticket.action()));
        double localY = decimal(payload, "localY", defaultLocalY(ticket.action()));
        double localZ = decimal(payload, "localZ", defaultLocalZ(ticket.action()));
        ActiveIslandRegistry registry = activeIslands;
        ActiveIslandRegistry.ActiveIsland active = registry == null ? null : registry.find(ticket.islandId()).orElse(null);
        double worldX = active == null ? localX : active.originX() + localX;
        double worldZ = active == null ? localZ : active.originZ() + localZ;
        return new Location(world, worldX, localY, worldZ, (float) decimal(payload, "yaw", 180.0D), (float) decimal(payload, "pitch", 0.0D));
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
            case VISIT -> "방문한 섬에 도착했습니다.";
            case WARP -> "섬 워프에 도착했습니다.";
            case ADMIN_TELEPORT -> "관리자 이동이 완료되었습니다.";
            default -> "내 섬에 도착했습니다.";
        };
    }

    private void notifyPreparing(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다..."));
        }
    }

    private void notifyRouteFailed(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendActionBar(Component.text("섬 이동 준비가 완료되지 않았습니다. 다시 시도해주세요."));
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

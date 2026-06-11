package kr.lunaf.cloudislands.paper;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
                Bukkit.getScheduler().runTask(plugin, () -> teleport(playerUuid, ticket.get().targetWorld(), ticket.get().payload()));
                return;
            }
            if (attempt < 20) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> consumeAndTeleport(ticketId, playerUuid, nonce, attempt + 1), 20L);
            }
        });
    }

    private void teleport(UUID playerUuid, String worldName, java.util.Map<String, String> payload) {
        Player player = Bukkit.getPlayer(playerUuid);
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (player == null || world == null) {
            return;
        }
        player.teleport(new Location(world, decimal(payload, "localX", 0.5D), decimal(payload, "localY", 100.0D), decimal(payload, "localZ", 0.5D), (float) decimal(payload, "yaw", 180.0D), (float) decimal(payload, "pitch", 0.0D)));
    }

    private double decimal(java.util.Map<String, String> payload, String key, double fallback) {
        try {
            return Double.parseDouble(payload.getOrDefault(key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

package kr.lunaf.cloudislands.paper.session;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;

public final class PaperRouteSessionListener implements Listener {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final RouteTicketConsumer ticketConsumer;
    private final String nodeId;
    private final boolean requireRouteSession;
    private final String fallbackServerName;

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, false);
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, "Lobby");
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, String fallbackServerName) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.ticketConsumer = ticketConsumer;
        this.nodeId = nodeId;
        this.requireRouteSession = requireRouteSession;
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        consumeSession(event.getPlayer().getUniqueId(), 0);
    }

    private void consumeSession(java.util.UUID playerUuid, int attempt) {
        coreApiClient.consumeRouteSession(playerUuid, nodeId).thenAccept(session -> {
            if (session.isPresent()) {
                var value = session.get();
                ticketConsumer.consumeAndTeleport(value.ticketId(), value.playerUuid(), value.nonce());
                return;
            }
            if (attempt < 6) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> consumeSession(playerUuid, attempt + 1), 10L);
            } else if (requireRouteSession) {
                rejectDirectJoin(playerUuid);
            }
        }).exceptionally(error -> {
            if (attempt < 6) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> consumeSession(playerUuid, attempt + 1), 10L);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var player = Bukkit.getPlayer(playerUuid);
                    if (player != null) {
                        player.sendActionBar(Component.text("섬 이동 정보를 확인하지 못했습니다."));
                    }
                });
            }
            return null;
        });
    }

    private void rejectDirectJoin(java.util.UUID playerUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendActionBar(Component.text("섬 이동 정보가 없어 로비로 이동합니다."));
                sendToFallback(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var stillHere = Bukkit.getPlayer(playerUuid);
                    if (stillHere != null) {
                        stillHere.kick(Component.text("섬 이동 정보가 없어 접속할 수 없습니다. /섬 홈으로 다시 이동해주세요."));
                    }
                }, 40L);
            }
        });
    }

    private void sendToFallback(org.bukkit.entity.Player player) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("Connect");
            output.writeUTF(fallbackServerName);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException ignored) {
            player.kick(Component.text("섬 이동 정보가 없어 접속할 수 없습니다. /섬 홈으로 다시 이동해주세요."));
        }
    }
}

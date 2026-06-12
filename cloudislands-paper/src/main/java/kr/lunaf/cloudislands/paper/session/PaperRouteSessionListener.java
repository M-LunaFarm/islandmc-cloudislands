package kr.lunaf.cloudislands.paper.session;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;

public final class PaperRouteSessionListener implements Listener {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final RouteTicketConsumer ticketConsumer;
    private final String nodeId;
    private final boolean requireRouteSession;
    private final String fallbackServerName;
    private final Map<UUID, PlayerRouteSession> verifiedSessions = new ConcurrentHashMap<>();

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
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!requireRouteSession) {
            return;
        }
        try {
            var session = coreApiClient.findRouteSession(event.getUniqueId(), nodeId).get(3L, TimeUnit.SECONDS);
            if (session.isPresent()) {
                PlayerRouteSession verified = session.get();
                verifiedSessions.put(event.getUniqueId(), verified);
                scheduleVerifiedSessionExpiry(event.getUniqueId(), verified);
                return;
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Route session pre-login check failed for " + event.getUniqueId() + ": " + exception.getMessage());
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "섬 이동 정보가 없어 접속할 수 없습니다. /섬 홈으로 다시 이동해주세요.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        PlayerRouteSession verified = verifiedSessions.remove(playerUuid);
        if (verified != null) {
            if (verified.expiresAt().isAfter(java.time.Instant.now())) {
                consumeSession(playerUuid, 0);
            } else if (requireRouteSession) {
                rejectDirectJoin(playerUuid);
            } else {
                consumeSession(playerUuid, 0);
            }
            return;
        }
        consumeSession(playerUuid, 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        verifiedSessions.remove(event.getPlayer().getUniqueId());
    }

    private void scheduleVerifiedSessionExpiry(UUID playerUuid, PlayerRouteSession session) {
        long delayMillis = Math.max(50L, session.expiresAt().toEpochMilli() - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> verifiedSessions.remove(playerUuid, session), delayTicks);
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
            } else if (requireRouteSession) {
                rejectDirectJoin(playerUuid);
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

package kr.lunaf.cloudislands.paper.session;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.security.ProxySourceAllowlist;
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
    private final boolean forwardingReady;
    private final boolean requireProxySourceAllowlist;
    private final String fallbackServerName;
    private final ProxySourceAllowlist proxySourceAllowlist;
    private final MessageRenderer messages;
    private final Map<UUID, PlayerRouteSession> verifiedSessions = new ConcurrentHashMap<>();
    private final AtomicLong proxySourceRejections = new AtomicLong();
    private final AtomicLong proxySourceConfigurationRejections = new AtomicLong();
    private final AtomicLong forwardingRejections = new AtomicLong();
    private final AtomicLong routeSessionRejections = new AtomicLong();
    private final AtomicLong routeSessionCheckFailures = new AtomicLong();

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, false);
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, true, "Lobby", new ProxySourceAllowlist(java.util.List.of()));
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, String fallbackServerName) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, true, fallbackServerName, new ProxySourceAllowlist(java.util.List.of()));
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, boolean forwardingReady, String fallbackServerName) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, forwardingReady, fallbackServerName, new ProxySourceAllowlist(java.util.List.of()));
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, boolean forwardingReady, String fallbackServerName, ProxySourceAllowlist proxySourceAllowlist) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, forwardingReady, fallbackServerName, proxySourceAllowlist, null);
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, boolean forwardingReady, String fallbackServerName, ProxySourceAllowlist proxySourceAllowlist, MessageRenderer messages) {
        this(plugin, coreApiClient, ticketConsumer, nodeId, requireRouteSession, forwardingReady, false, fallbackServerName, proxySourceAllowlist, messages);
    }

    public PaperRouteSessionListener(Plugin plugin, CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId, boolean requireRouteSession, boolean forwardingReady, boolean requireProxySourceAllowlist, String fallbackServerName, ProxySourceAllowlist proxySourceAllowlist, MessageRenderer messages) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.ticketConsumer = ticketConsumer;
        this.nodeId = nodeId;
        this.requireRouteSession = requireRouteSession;
        this.forwardingReady = forwardingReady;
        this.requireProxySourceAllowlist = requireProxySourceAllowlist;
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
        this.proxySourceAllowlist = proxySourceAllowlist == null ? new ProxySourceAllowlist(java.util.List.of()) : proxySourceAllowlist;
        this.messages = messages;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (requireProxySourceAllowlist && !proxySourceAllowlist.configured()) {
            proxySourceConfigurationRejections.incrementAndGet();
            plugin.getLogger().warning("Rejected login because security.proxy-source-allowlist is required but empty");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, playerMessage("route-login-proxy-allowlist-required", "섬 서버 프록시 보안 설정이 완료되지 않았습니다. 관리자에게 문의해주세요."));
            return;
        }
        if (!proxySourceAllowlist.allows(event.getAddress())) {
            proxySourceRejections.incrementAndGet();
            plugin.getLogger().warning("Rejected non-proxy login source for " + event.getUniqueId() + " from " + event.getAddress().getHostAddress());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, playerMessage("route-login-proxy-required", "정상적인 프록시 경로로 접속해주세요."));
            return;
        }
        if (!forwardingReady) {
            forwardingRejections.incrementAndGet();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, playerMessage("route-login-forwarding-not-ready", "섬 서버 보안 설정이 완료되지 않았습니다. 관리자에게 문의해주세요."));
            return;
        }
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
            routeSessionCheckFailures.incrementAndGet();
            plugin.getLogger().warning("Route session pre-login check failed for " + event.getUniqueId() + ": " + exception.getMessage());
        }
        routeSessionRejections.incrementAndGet();
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, playerMessage("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요."));
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
        UUID playerUuid = event.getPlayer().getUniqueId();
        verifiedSessions.remove(playerUuid);
        ticketConsumer.clearLoading(playerUuid);
    }

    private void scheduleVerifiedSessionExpiry(UUID playerUuid, PlayerRouteSession session) {
        long delayMillis = Math.max(50L, session.expiresAt().toEpochMilli() - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> verifiedSessions.remove(playerUuid, session), delayTicks);
    }

    private void consumeSession(java.util.UUID playerUuid, int attempt) {
        if (attempt == 0 || attempt == 3) {
            showPreparing(playerUuid);
        }
        coreApiClient.consumeRouteSession(playerUuid, nodeId, attempt >= 6).thenAccept(session -> {
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
                        player.sendActionBar(Component.text(playerMessage("route-session-check-failed", "섬 입장 준비를 확인하지 못했습니다.")));
                    }
                });
            }
            return null;
        });
    }

    private void showPreparing(java.util.UUID playerUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendActionBar(Component.text(playerMessage("route-session-preparing", "섬 입장을 준비하는 중입니다...")));
            }
        });
    }

    private void rejectDirectJoin(java.util.UUID playerUuid) {
        routeSessionRejections.incrementAndGet();
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendActionBar(Component.text(playerMessage("route-session-missing-fallback", "섬 입장 요청이 없어 로비로 이동합니다.")));
                sendToFallback(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var stillHere = Bukkit.getPlayer(playerUuid);
                    if (stillHere != null) {
                        stillHere.kick(Component.text(playerMessage("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요.")));
                    }
                }, 40L);
            }
        });
    }

    private void sendToFallback(org.bukkit.entity.Player player) {
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            player.kick(Component.text(playerMessage("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요.")));
            return;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("Connect");
            output.writeUTF(fallbackServerName);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException | RuntimeException ignored) {
            player.kick(Component.text(playerMessage("route-login-session-required", "정상적인 섬 입장 요청이 없습니다. /섬 홈으로 다시 이동해주세요.")));
        }
    }

    private String playerMessage(String key, String fallback) {
        return sanitizePlayerMessage(message(key, fallback));
    }

    private String message(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String sanitizePlayerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 입장 요청을 처리하지 못했습니다." : message;
        return value
            .replaceAll("(?i)\\btargetNode\\s*[=:]\\s*[^\\s,|]+", "targetNode=숨김")
            .replaceAll("(?i)\\brequestedNode\\s*[=:]\\s*[^\\s,|]+", "requestedNode=숨김")
            .replaceAll("(?i)\\btargetServerName\\s*[=:]\\s*[^\\s,|]+", "targetServerName=숨김")
            .replaceAll("(?i)\\bserver\\s*[=:]\\s*[^\\s,|]+", "server=숨김")
            .replaceAll("(?i)\\bnode\\s*[=:]\\s*[^\\s,|]+", "node=숨김")
            .replaceAll("(?i)\\b[A-Za-z0-9_.-]*island[-_ ]?\\d+[A-Za-z0-9_.-]*\\b", "섬 서버")
            .replaceAll("(?i)\\b[A-Za-z0-9_.-]*node[-_ ]?\\d+[A-Za-z0-9_.-]*\\b", "섬 서버");
    }

    public long proxySourceRejections() {
        return proxySourceRejections.get();
    }

    public long proxySourceConfigurationRejections() {
        return proxySourceConfigurationRejections.get();
    }

    public long forwardingRejections() {
        return forwardingRejections.get();
    }

    public long routeSessionRejections() {
        return routeSessionRejections.get();
    }

    public long routeSessionCheckFailures() {
        return routeSessionCheckFailures.get();
    }
}

package kr.lunaf.cloudislands.velocity.routing;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.List;
import java.util.function.Function;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import net.kyori.adventure.text.Component;

public final class RouteFallbackService {
    private final ProxyServer proxy;
    private final String fallbackServer;
    private final VelocityRoutingMetrics metrics;
    private final Function<String, Component> playerMessage;

    public RouteFallbackService(
        ProxyServer proxy,
        String fallbackServer,
        VelocityRoutingMetrics metrics,
        Function<String, Component> playerMessage
    ) {
        this.proxy = proxy;
        this.fallbackServer = fallbackServer;
        this.metrics = metrics;
        this.playerMessage = playerMessage;
    }

    public boolean fallbackAvailable() {
        return findServer(fallbackServer) != null;
    }

    public boolean playerOnline(Player player) {
        return player != null && proxy.getPlayer(player.getUniqueId()).isPresent();
    }

    public RegisteredServer findServer(String name) {
        return proxy.getServer(name).or(() -> proxy.getServer(nodeIdToServerName(name))).orElse(null);
    }

    public void transfer(Player player, String message) {
        transfer(player, message, "ROUTE_FAILED");
    }

    public void transfer(Player player, String message, String code) {
        String safeCode = safeFailureCode(code, "ROUTE_FAILED");
        metrics.routeFailure(safeCode);
        metrics.rememberFallback(safeCode, "attempt");
        if (!playerOnline(player)) {
            metrics.fallbackSkippedOffline();
            metrics.rememberFallback(safeCode, "skipped-offline");
            return;
        }
        player.sendMessage(playerMessage.apply(message));
        RegisteredServer server = findServer(fallbackServer);
        if (server == null) {
            metrics.fallbackMissing();
            metrics.rememberFallback(safeCode, "fallback-missing");
            return;
        }
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (success) {
                metrics.fallbackTransfer();
                metrics.rememberFallback(safeCode, "transferred");
            } else {
                metrics.fallbackFailure();
                metrics.rememberFallback(safeCode, "transfer-failed");
            }
        }).exceptionally(error -> {
            metrics.fallbackFailure();
            metrics.rememberFallback(safeCode, "transfer-exception");
            return null;
        });
    }

    public int moveNodePlayersToFallback(String nodeId) {
        RegisteredServer target = findServer(nodeId);
        RegisteredServer fallback = findServer(fallbackServer);
        if (target == null || fallback == null) {
            metrics.fallbackMissing();
            return 0;
        }
        List<Player> players = List.copyOf(target.getPlayersConnected());
        for (Player connected : players) {
            connected.sendMessage(Component.text("섬 점검으로 로비로 이동합니다."));
            connected.createConnectionRequest(fallback).connectWithIndication().thenAccept(success -> {
                if (success) {
                    metrics.fallbackTransfer();
                } else {
                    metrics.fallbackFailure();
                }
            }).exceptionally(error -> {
                metrics.fallbackFailure();
                return null;
            });
        }
        return players.size();
    }

    public static String safeFailureCode(String code, String fallback) {
        String value = code == null || code.isBlank() ? fallback : code;
        return value == null || value.isBlank() ? "ROUTE_FAILED" : value.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    static String nodeIdToServerName(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        String[] parts = nodeId.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}

package kr.lunaf.cloudislands.paper.command;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.paper.application.IslandRoutingUseCase;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;
import kr.lunaf.cloudislands.protocol.route.RoutePreparationProgressPolicy;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandRoutingCommandHandler {
    private final Plugin plugin;
    private final IslandRoutingUseCase routingUseCase;
    private final int routeWaitSeconds;
    private final String fallbackServerName;
    private final Runtime runtime;
    private final Map<UUID, BossBar> routeBossBars = new ConcurrentHashMap<>();

    IslandRoutingCommandHandler(Plugin plugin, CoreApiClient coreApiClient, int routeWaitSeconds, String fallbackServerName, Runtime runtime) {
        this.plugin = plugin;
        this.routingUseCase = new IslandRoutingUseCase(coreApiClient);
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
        this.runtime = runtime;
    }

    void routeWarp(Player player, UUID islandId, String warpName) {
        routeTicket(player, routingUseCase.createWarpTicket(player.getUniqueId(), islandId, warpName, runtime::mutate), "해당 워프로 이동할 수 없습니다.");
    }

    void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> routeTicket(player, ticket, failureMessage, 0)).exceptionally(error -> {
            clearRouteLoading(player);
            runtime.message(player, routeFailureMessage(error, failureMessage));
            return null;
        });
    }

    void clearRouteLoading(Player player) {
        BossBar bossBar = routeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    boolean connectPlayerToFallback(Player player, String successMessage, String failureMessage) {
        connectPlayerToServer(player, fallbackServerName, successMessage, failureMessage);
        return true;
    }

    private void routeTicket(Player player, RouteTicket ticket, String failureMessage, int attempt) {
        if (ticket.state().name().equals("READY")) {
            String target = routeTargetName(ticket);
            showRouteLoading(player, 1.0f, runtime.routeMessage(player, "route-loading-complete", target + " 로딩 완료", "target", target));
            player.sendActionBar(routeComponent(player, "route-ready", "잠시 후 " + target + "으로 이동합니다.", "target", target));
            publishAndConnect(player, ticket, failureMessage);
            return;
        }
        if (attempt >= routeWaitSeconds) {
            clearRouteLoading(player);
            runtime.message(player, failureMessage);
            return;
        }
        int progress = RoutePreparationProgressPolicy.preparingPercent(attempt);
        String target = RoutePreparationProgressPolicy.safeTargetName(routeTargetName(ticket));
        String progressValue = Integer.toString(progress);
        showRouteLoading(player, RoutePreparationProgressPolicy.preparingProgress(attempt), runtime.routeMessage(player, "route-loading-progress", RoutePreparationProgressPolicy.loadingTitle(target, attempt), "target", target, "progress", progressValue));
        player.sendActionBar(routeComponent(player, "route-preparing-progress", RoutePreparationProgressPolicy.preparingActionBar(target, attempt), "target", target, "progress", progressValue));
        CompletableFuture.runAsync(() -> routingUseCase.routeTicketStatus(ticket).thenAccept(status -> {
            if (status.isPresent()) {
                routeTicket(player, status.get(), failureMessage, attempt + 1);
            } else {
                clearRouteLoading(player);
                runtime.message(player, failureMessage);
            }
        }).exceptionally(error -> {
            clearRouteLoading(player);
            runtime.message(player, routeFailureMessage(error, failureMessage));
            return null;
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        if (runtime.coreUnavailable(error)) {
            return runtime.routeMessage("core-service-maintenance", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE);
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return runtime.playerCodeMessage(coreError.code(), fallback);
            }
            if (current instanceof java.io.IOException) {
                return CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE;
            }
            current = current.getCause();
        }
        return fallback;
    }

    private Component routeComponent(Player player, String key, String fallback, String... variables) {
        return Component.text(runtime.playerMessage(runtime.routeMessage(player, key, fallback, variables)));
    }

    private void publishAndConnect(Player player, RouteTicket ticket, String failureMessage) {
        routingUseCase.publishRouteSession(ticket, runtime::mutate).thenRun(() -> {
            clearRouteLoading(player);
            connectWithTicket(player, ticket, ticket.payload().getOrDefault("targetServerName", ticket.targetNode()));
        }).exceptionally(error -> {
            clearRouteLoading(player);
            clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
            runtime.message(player, routeFailureMessage(error, failureMessage));
            return null;
        });
    }

    private void showRouteLoading(Player player, float progress, String title) {
        BossBar bossBar = routeBossBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(Component.text(runtime.playerMessage(title)), progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bossBar.name(Component.text(runtime.playerMessage(title)));
        bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
                runtime.message(player, runtime.routeMessage("route-command-failed", "섬으로 이동하지 못했습니다."));
                return;
            }
            if (!canUseBungeeConnect()) {
                clearFailedRoute(ticket, "BUNGEE_CONNECT_UNAVAILABLE");
                runtime.message(player, runtime.routeMessage("route-command-publish-failed", "섬 이동 경로를 준비하지 못했습니다."));
                return;
            }
            try {
                sendConnectPluginMessage(player, targetServerName);
                runtime.message(player, runtime.routeMessage("route-command-started", "섬으로 이동합니다."));
            } catch (IOException | RuntimeException exception) {
                clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
                runtime.message(player, runtime.routeMessage("route-command-failed", "섬으로 이동하지 못했습니다."));
            }
        });
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        routingUseCase.clearRoute(ticket, reason, runtime::mutate).exceptionally(error -> null);
    }

    private String routeTargetName(RouteTicket ticket) {
        if (ticket == null) {
            return "섬";
        }
        return switch (PlayerRouteTicketView.from(ticket).destination()) {
            case "my-island" -> "내 섬";
            case "other-island" -> "다른 사람 섬";
            case "island-ranking" -> "섬 랭킹";
            case "island-visit" -> "방문할 섬";
            case "island-settings" -> "섬 설정";
            case "island-warps" -> "섬 워프";
            default -> "섬";
        };
    }

    private void connectPlayerToServer(Player player, String targetServerName, String successMessage, String failureMessage) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                player.sendMessage(runtime.playerMessage(failureMessage));
                return;
            }
            if (!canUseBungeeConnect()) {
                player.sendMessage(runtime.playerMessage(failureMessage));
                return;
            }
            try {
                sendConnectPluginMessage(player, targetServerName);
                player.sendMessage(runtime.playerMessage(successMessage));
            } catch (IOException | RuntimeException exception) {
                player.sendMessage(runtime.playerMessage(failureMessage));
            }
        });
    }

    private void sendConnectPluginMessage(Player player, String targetServerName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF("Connect");
        output.writeUTF(targetServerName);
        player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
    }

    private boolean canUseBungeeConnect() {
        return plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord");
    }

    interface Runtime {
        void message(Player player, String message);

        String routeMessage(String key, String fallback, String... variables);

        String routeMessage(Player player, String key, String fallback, String... variables);

        String playerCodeMessage(String code, String fallback);

        String playerMessage(String message);

        boolean coreUnavailable(Throwable error);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }
}

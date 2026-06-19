package kr.lunaf.cloudislands.velocity.routing;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import net.kyori.adventure.bossbar.BossBar;

public final class RouteTicketRouter {
    private final CoreApiClient coreApiClient;
    private final int routeWaitSeconds;
    private final VelocityMessages messages;
    private final VelocityRoutingMetrics metrics;
    private final RouteFallbackService fallbackService;
    private final RouteProgressPresenter progressPresenter;

    public RouteTicketRouter(
            CoreApiClient coreApiClient,
            int routeWaitSeconds,
            VelocityMessages messages,
            VelocityRoutingMetrics metrics,
            RouteFallbackService fallbackService,
            RouteProgressPresenter progressPresenter) {
        this.coreApiClient = coreApiClient;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.messages = messages;
        this.metrics = metrics;
        this.fallbackService = fallbackService;
        this.progressPresenter = progressPresenter;
    }

    public void route(Player player, RouteTicket ticket, String failureMessage) {
        metrics.routeAttempt();
        if (ticket == null) {
            fallbackService.transfer(player, failureMessage);
            return;
        }
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        if (ticket.state().name().equals("PREPARING")) {
            String target = routeTargetName(ticket);
            progressPresenter.actionBar(player, messages.text("route-preparing", "target", target));
            BossBar bossBar = progressPresenter.loadingBossBar(messages.text("route-loading-title", "target", target));
            progressPresenter.showBossBar(player, bossBar);
            waitForReadyTicket(player, ticket, failureMessage, bossBar, 0);
            return;
        }
        publishAndConnect(player, ticket);
    }

    public void routeFuture(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> route(player, ticket, failureMessage)).exceptionally(error -> {
            fallbackService.transfer(player, routeFailureMessage(error, failureMessage), routeFailureCode(error, "ROUTE_FAILED"));
            return null;
        });
    }

    private void waitForReadyTicket(Player player, RouteTicket ticket, String failureMessage, BossBar bossBar, int attempt) {
        if (!fallbackService.playerOnline(player)) {
            progressPresenter.hideBossBar(player, bossBar);
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        String target = routeTargetName(ticket);
        String progressValue = RouteProgressPresenter.progressValue(attempt);
        progressPresenter.preparing(player, bossBar, messages.text("route-loading-progress", "target", target, "progress", progressValue), messages.text("route-preparing-progress", "target", target, "progress", progressValue), attempt);
        coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            Optional<RouteTicket> ready = status.filter(value -> value.state().name().equals("READY"));
            if (ready.isPresent()) {
                String readyTarget = routeTargetName(ready.get());
                progressPresenter.ready(player, bossBar, messages.text("route-ready", "target", readyTarget));
                progressPresenter.hideBossBar(player, bossBar);
                publishAndConnect(player, ready.get());
                return;
            }
            if (status.isPresent() && terminalRouteState(status.get())) {
                progressPresenter.hideBossBar(player, bossBar);
                fallbackService.transfer(player, terminalRouteMessage(status.get(), failureMessage));
                return;
            }
            if (attempt >= routeWaitSeconds) {
                progressPresenter.hideBossBar(player, bossBar);
                clearFailedRoute(ticket, "ROUTE_READY_TIMEOUT");
                fallbackService.transfer(player, failureMessage, "ROUTE_READY_TIMEOUT");
                return;
            }
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> waitForReadyTicket(player, ticket, failureMessage, bossBar, attempt + 1));
        }).exceptionally(error -> {
            progressPresenter.hideBossBar(player, bossBar);
            clearFailedRoute(ticket, "ROUTE_STATUS_FAILED");
            fallbackService.transfer(player, routeFailureMessage(error, failureMessage), routeFailureCode(error, "ROUTE_STATUS_FAILED"));
            return null;
        });
    }

    private boolean terminalRouteState(RouteTicket ticket) {
        if (ticket == null || ticket.state() == null) {
            return false;
        }
        return switch (ticket.state().name()) {
            case "FAILED", "EXPIRED", "CANCELLED", "CONSUMED" -> true;
            default -> false;
        };
    }

    private String terminalRouteMessage(RouteTicket ticket, String fallback) {
        String state = ticket == null || ticket.state() == null ? "" : ticket.state().name();
        if ("EXPIRED".equals(state)) {
            return "섬 이동 준비 시간이 만료되었습니다. 다시 시도해주세요.";
        }
        if ("CANCELLED".equals(state)) {
            return "섬 이동이 취소되었습니다.";
        }
        if ("CONSUMED".equals(state)) {
            return "이미 사용된 섬 이동 요청입니다. 다시 시도해주세요.";
        }
        String reason = ticket == null ? "" : ticket.payload().getOrDefault("failureReason", "");
        if (!reason.isBlank()) {
            return playerErrorMessage(reason, fallback);
        }
        return fallback;
    }

    private void publishAndConnect(Player player, RouteTicket ticket) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, ticket, targetServerName);
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
            fallbackService.transfer(player, "섬 이동 정보를 준비하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        RegisteredServer server = fallbackService.findServer(targetServerName);
        if (server == null) {
            clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
            fallbackService.transfer(player, "섬 이동 경로를 찾을 수 없습니다.", "TARGET_SERVER_NOT_FOUND");
            return;
        }
        connect(player, ticket, server);
    }

    private void connect(Player player, RouteTicket ticket, RegisteredServer server) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                clearFailedRoute(ticket, "CONNECT_FAILED");
                fallbackService.transfer(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.", "CONNECT_FAILED");
                return;
            }
            metrics.routeSuccess();
            progressPresenter.actionBar(player, arrivalMessage(ticket));
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "CONNECT_EXCEPTION");
            fallbackService.transfer(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.", "CONNECT_EXCEPTION");
            return null;
        });
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        if (ticket == null) {
            return;
        }
        coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "ROUTE_FAILED" : reason).exceptionally(error -> null);
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return playerErrorMessage(coreError.code(), fallback);
            }
            if (current instanceof IOException) {
                return messages.text("island-service-maintenance");
            }
            current = current.getCause();
        }
        return fallback;
    }

    private String routeFailureCode(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return RouteFallbackService.safeFailureCode(coreError.code(), fallback);
            }
            if (current instanceof IOException) {
                return "CORE_API_IO";
            }
            current = current.getCause();
        }
        return RouteFallbackService.safeFailureCode(fallback, "ROUTE_FAILED");
    }

    private String playerErrorMessage(String code, String fallback) {
        return RouteFailureMessagePolicy.playerMessage(code, fallback);
    }

    public static String routeTargetName(RouteTicket ticket) {
        if (ticket == null) {
            return "섬";
        }
        return routeTargetName(PlayerRouteTicketView.from(ticket).destination());
    }

    public static String routeTargetName(String destination) {
        return switch (destination == null ? "" : destination.toLowerCase(Locale.ROOT)) {
            case "my-island" -> "내 섬";
            case "other-island" -> "다른 사람 섬";
            case "island-ranking" -> "섬 랭킹";
            case "island-visit" -> "방문할 섬";
            case "island-settings" -> "섬 설정";
            case "island-warps" -> "섬 워프";
            default -> "섬";
        };
    }

    private String arrivalMessage(RouteTicket ticket) {
        if (ticket == null) {
            return "섬에 도착했습니다.";
        }
        return switch (PlayerRouteTicketView.from(ticket).destination()) {
            case "my-island" -> "내 섬에 도착했습니다.";
            case "other-island" -> "다른 사람 섬에 도착했습니다.";
            case "island-ranking" -> "섬 랭킹을 열었습니다.";
            case "island-visit" -> "방문한 섬에 도착했습니다.";
            case "island-settings" -> "섬 설정을 열었습니다.";
            case "island-warps" -> "섬 워프에 도착했습니다.";
            default -> "섬에 도착했습니다.";
        };
    }
}

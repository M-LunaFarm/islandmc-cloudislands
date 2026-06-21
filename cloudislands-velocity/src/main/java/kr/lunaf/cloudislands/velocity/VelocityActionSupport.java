package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;
import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.velocity.message.VelocityCoreConfigMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityCoreStatusMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityEventMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityIslandMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityMigrationMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityNodeJobMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityPlayerPayloadFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityRouteMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocitySnapshotMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.routing.PendingRouteService;
import kr.lunaf.cloudislands.velocity.routing.RouteFallbackService;
import kr.lunaf.cloudislands.velocity.routing.RouteProgressPresenter;
import kr.lunaf.cloudislands.velocity.routing.RouteRequestGuard;
import kr.lunaf.cloudislands.velocity.routing.RouteTicketRouter;
import kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver;
import net.kyori.adventure.text.Component;

abstract class VelocityActionSupport {
    protected final CoreApiClient coreApiClient;
    protected final boolean hideNodeNames;
    protected final VelocityMessages messages;
    protected final VelocityCoreStatusMessageFormatter coreStatusMessages = new VelocityCoreStatusMessageFormatter();
    protected final VelocityCoreConfigMessageFormatter coreConfigMessages = new VelocityCoreConfigMessageFormatter();
    protected final VelocityMigrationMessageFormatter migrationMessages = new VelocityMigrationMessageFormatter();
    protected final VelocityEventMessageFormatter eventMessages;
    protected final VelocityIslandMessageFormatter islandMessages;
    protected final VelocityNodeJobMessageFormatter nodeJobMessages;
    protected final VelocityPlayerPayloadFormatter playerPayloads = new VelocityPlayerPayloadFormatter();
    protected final VelocityRouteMessageFormatter routeMessages;
    protected final VelocitySnapshotMessageFormatter snapshotMessages = new VelocitySnapshotMessageFormatter();
    protected final RouteFallbackService fallbackService;
    protected final RouteProgressPresenter progressPresenter;
    protected final RouteRequestGuard routeRequestGuard;
    protected final RouteTicketRouter routeTickets;
    protected final VelocityTargetResolver targetResolver;
    protected final PendingRouteService pendingRoutes;

    VelocityActionSupport(VelocityActionContext context) {
        this.coreApiClient = context.coreApiClient();
        this.hideNodeNames = context.hideNodeNames();
        this.messages = context.messages() == null ? VelocityMessages.defaults() : context.messages();
        this.eventMessages = new VelocityEventMessageFormatter(context.routePrivacy());
        this.islandMessages = new VelocityIslandMessageFormatter(context.routePrivacy());
        this.nodeJobMessages = new VelocityNodeJobMessageFormatter(context.routePrivacy());
        this.routeMessages = new VelocityRouteMessageFormatter(context.routePrivacy());
        this.fallbackService = context.fallbackService();
        this.progressPresenter = context.progressPresenter();
        this.routeRequestGuard = context.routeRequestGuard();
        this.routeTickets = context.routeTickets();
        this.targetResolver = context.targetResolver();
        this.pendingRoutes = context.pendingRoutes();
    }

    protected void sendPlayerPayload(Player player, String body, String emptyMessage, String successMessage) {
        player.sendMessage(Component.text(playerPayloads.playerPayloadMessage(body, emptyMessage, successMessage)));
    }

    protected void sendPlayerPayloadFuture(Player player, CompletableFuture<String> future, String emptyMessage, String successMessage) {
        future.thenAccept(body -> sendPlayerPayload(player, body, emptyMessage, successMessage)).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    protected void sendActionResult(Player player, CompletableFuture<Void> future, String successMessage, String failureMessage) {
        future.thenRun(() -> player.sendMessage(Component.text(successMessage))).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    protected void sendBodyResult(Player player, CompletableFuture<String> future, String emptyMessage) {
        future.thenAccept(body -> player.sendMessage(playerComponent(bodyResultMessage(body, emptyMessage)))).exceptionally(error -> {
            player.sendMessage(playerComponent(emptyMessage));
            return null;
        });
    }

    protected String bodyResultMessage(String body, String emptyMessage) {
        return playerPayloads.bodyResultMessage(body, emptyMessage);
    }

    protected String routeDebugMessage(String body) {
        return playerMessage(routeMessages.debug(body));
    }

    protected String routeDebugMessage(AdminRouteDebugView view) {
        return playerMessage(routeMessages.debug(view));
    }

    protected String routeTicketMessage(String body) {
        return playerMessage(routeMessages.ticket(body));
    }

    protected String routeTicketMessage(Optional<AdminRouteTicketView> ticket) {
        return playerMessage(routeMessages.ticket(ticket));
    }

    String routeClearMessage(String body) {
        return playerMessage(routeMessages.clear(body));
    }

    String routeClearMessage(AdminRouteClearView view) {
        return playerMessage(routeMessages.clear(view));
    }

    protected String snapshotListMessage(String body) {
        return snapshotMessages.snapshotList(body);
    }

    protected void sendInviteActionResult(Player player, CompletableFuture<String> future, String successMessage, String failureMessage) {
        future.thenAccept(body -> {
            if (body == null || body.isBlank() || body.contains("\"error\"") || body.contains("\"accepted\":false")) {
                player.sendMessage(Component.text(failureMessage));
                return;
            }
            player.sendMessage(Component.text(successMessage));
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    protected void adminIslandTarget(Player player, String target, Consumer<UUID> action) {
        targetResolver.resolveIslandId(target).thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("섬을 찾지 못했습니다."));
                return;
            }
            action.accept(islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬을 찾지 못했습니다."));
            return null;
        });
    }

    protected String playerErrorMessage(String code, String fallback) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.playerMessage(code, fallback);
    }

    protected boolean internalRouteCapacityCode(String code) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.capacityCode(code);
    }

    protected boolean internalRouteMaintenanceCode(String code) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.maintenanceCode(code);
    }

    protected void route(Player player, RouteTicket ticket, String failureMessage) {
        routeTickets.route(player, ticket, failureMessage);
    }

    protected void routeFuture(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        routeTickets.routeFuture(player, ticketFuture, failureMessage);
    }

    protected boolean allowRouteRequest(Player player) {
        if (routeRequestGuard.allow(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(Component.text("섬 이동 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."));
        return false;
    }

    protected Component playerComponent(String message) {
        return Component.text(playerMessage(message));
    }

    protected String playerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 이동을 처리하지 못했습니다." : message;
        if (!hideNodeNames) {
            return value;
        }
        return kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy.sanitize(value);
    }

    protected int moveNodePlayersToFallback(String nodeId) {
        return fallbackService.moveNodePlayersToFallback(nodeId);
    }

    protected String messageForCreateFailure(String code) {
        if (code != null && internalRouteCapacityCode(code)) {
            return messages.text("island-create-node-unavailable");
        }
        if (code != null && internalRouteMaintenanceCode(code)) {
            return messages.text("island-service-maintenance");
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> messages.text("island-create-already-has-island");
            case "TEMPLATE_UNAVAILABLE" -> messages.text("island-create-template-unavailable");
            case "CREATE_LOCKED" -> messages.text("island-create-locked");
            case "NODE_UNAVAILABLE" -> messages.text("island-create-node-unavailable");
            case "JOB_QUEUE_UNAVAILABLE", "RECOVERY_UNAVAILABLE" -> messages.text("island-service-maintenance");
            default -> messages.text("island-create-failed");
        };
    }

    protected boolean rejectExplicitIslandLookup(Player player, UUID islandId) {
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            return false;
        }
        player.sendMessage(Component.text("플레이어 명령에서는 섬 UUID 직접 조회를 사용할 수 없습니다."));
        return true;
    }

    protected void withResolvedIsland(Player player, UUID islandId, String missingMessage, String failureMessage, Consumer<UUID> action) {
        UUID emptyIslandId = new UUID(0L, 0L);
        if (islandId != null && !islandId.equals(emptyIslandId)) {
            action.accept(islandId);
            return;
        }
        coreApiClient.islandInfoByOwner(player.getUniqueId()).thenAccept(body -> {
            UUID resolvedIslandId = parseUuid(jsonValue(body, "islandId"));
            if (resolvedIslandId.equals(emptyIslandId)) {
                player.sendMessage(Component.text(missingMessage));
                return;
            }
            action.accept(resolvedIslandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }
}

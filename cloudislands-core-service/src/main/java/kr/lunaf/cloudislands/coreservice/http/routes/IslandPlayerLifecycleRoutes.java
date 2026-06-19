package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;

public final class IslandPlayerLifecycleRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLifecycleWorkflow lifecycle;
    private final DeleteRequester deleteRequester;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandPlayerLifecycleRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLifecycleWorkflow lifecycle,
            DeleteRequester deleteRequester,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.lifecycle = lifecycle;
        this.deleteRequester = deleteRequester;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/delete", this::delete);
        registry.route("/v1/islands/reset", this::reset);
    }

    private void delete(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID requesterUuid = JsonFields.uuid(body, "requesterUuid", EMPTY_UUID);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        boolean deleted = deleteRequester.request(islandId, requesterUuid, requesterUuid, "player-delete");
        if (deleted) {
            audit.log(requesterUuid, "PLAYER", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of());
            islandLogs.append(islandId, requesterUuid, "ISLAND_DELETE", Map.of());
        }
        CoreHttpResponses.write(exchange, deleted ? 202 : 403, deleteResultJson(new DeleteIslandResult(deleted, deleted ? "DELETED" : "NOT_OWNER_OR_MISSING", islandId)));
    }

    private void reset(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String reason = JsonFields.text(body, "reason", "player-reset");
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)) {
            return;
        }
        IslandLifecycleWorkflow.Result result = lifecycle.reset(islandId, reason);
        if (result.accepted()) {
            audit.log(actorUuid, "PLAYER", "ISLAND_RESET", "ISLAND", islandId.toString(), Map.of("reason", reason));
            islandLogs.append(islandId, actorUuid, "ISLAND_RESET", Map.of("reason", reason));
        }
        lifecycle(exchange, result);
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (var rule : permissionRules.list(islandId)) {
            permissions.put(rule.role(), rule.permission(), rule.allowed());
        }
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissions.allowed(member.role(), permission));
        boolean accepted = owner || allowed;
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHECKED.name(), Map.of(
            "islandId", islandId.toString(),
            "playerUuid", actorUuid.toString(),
            "permission", permission.name(),
            "allowed", Boolean.toString(accepted)
        ));
        if (accepted) {
            return true;
        }
        CoreHttpResponses.write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island permission " + permission.name() + " is required"));
        return false;
    }

    static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, lifecycleJson(result));
    }

    static String lifecycleJson(IslandLifecycleWorkflow.Result result) {
        return "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\"}";
    }

    static String deleteResultJson(DeleteIslandResult result) {
        return "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"islandId\":\"" + result.islandId() + "\"}";
    }

    @FunctionalInterface
    public interface DeleteRequester {
        boolean request(UUID islandId, UUID ownerUuid, UUID requesterUuid, String reason);
    }
}

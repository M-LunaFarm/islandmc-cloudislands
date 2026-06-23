package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
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
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;

public final class IslandWarehouseRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandWarehouseRepository warehouse;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandWarehouseRoutes(IslandWarehouseRepository warehouse, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, IslandPermissionRuleRepository permissionRules, IslandLogRepository islandLogs, AuditLogger audit, GlobalEventPublisher events) {
        this.warehouse = warehouse;
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/warehouse", this::list);
        registry.routePost("/v1/islands/warehouse/deposit", this::deposit);
        registry.routePost("/v1/islands/warehouse/withdraw", this::withdraw);
    }

    private void list(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        CoreHttpResponses.write(exchange, 200, warehouseJson(warehouse.list(islandId, JsonFields.integer(body, "limit", 27))));
    }

    private void deposit(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String materialKey = JsonFields.text(body, "materialKey", "");
        long amount = JsonFields.longValue(body, "amount", 0L);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.OPEN_CONTAINER)) {
            return;
        }
        var result = warehouse.deposit(islandId, materialKey, amount);
        recordMutation(actorUuid, islandId, materialKey, amount, result.code(), result.item().amount(), "DEPOSIT", result.accepted());
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, changeJson(result));
    }

    private void withdraw(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String materialKey = JsonFields.text(body, "materialKey", "");
        long amount = JsonFields.longValue(body, "amount", 0L);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.WITHDRAW_BANK)) {
            return;
        }
        var result = warehouse.withdraw(islandId, materialKey, amount);
        recordMutation(actorUuid, islandId, materialKey, amount, result.code(), result.item().amount(), "WITHDRAW", result.accepted());
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, changeJson(result));
    }

    private void recordMutation(UUID actorUuid, UUID islandId, String materialKey, long amount, String code, long balance, String operation, boolean accepted) {
        Map<String, String> fields = Map.of(
            "materialKey", IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey),
            "amount", Long.toString(amount),
            "code", code,
            "balance", Long.toString(balance)
        );
        audit.log(actorUuid, "PLAYER", "ISLAND_WAREHOUSE_" + operation, "ISLAND", islandId.toString(), fields);
        islandLogs.append(islandId, actorUuid, "ISLAND_WAREHOUSE_" + operation, fields);
        if (accepted) {
            events.publish(CloudIslandEventType.ISLAND_WAREHOUSE_CHANGED.name(), Map.of(
                "islandId", islandId.toString(),
                "actorUuid", actorUuid.toString(),
                "operation", operation,
                "materialKey", IslandWarehouseItemSnapshot.normalizeMaterialKey(materialKey),
                "amount", Long.toString(amount),
                "balance", Long.toString(balance)
            ));
        }
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissionRules.allowedRoleKey(islandId, actorUuid, member.effectiveRoleKey(), permission));
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

    static String warehouseJson(List<IslandWarehouseItemSnapshot> items) {
        List<Object> renderedItems = new ArrayList<>();
        for (IslandWarehouseItemSnapshot item : items) {
            renderedItems.add(itemMap(item));
        }
        return SimpleJson.stringify(Map.of("items", renderedItems));
    }

    static String changeJson(IslandWarehouseRepository.ChangeResult result) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", result.accepted());
        values.put("code", result.code());
        values.put("item", itemMap(result.item()));
        return SimpleJson.stringify(values);
    }

    static String itemJson(IslandWarehouseItemSnapshot item) {
        return SimpleJson.stringify(itemMap(item));
    }

    private static Map<String, Object> itemMap(IslandWarehouseItemSnapshot item) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", item.islandId());
        values.put("materialKey", item.materialKey());
        values.put("amount", item.amount());
        values.put("updatedAt", item.updatedAt());
        return values;
    }
}

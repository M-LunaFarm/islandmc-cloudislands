package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradeEffectApplier;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePurchaseResult;

public final class IslandUpgradeRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandUpgradeRepository upgradeRepository;
    private final IslandUpgradeService upgradeService;
    private final UpgradePolicy upgradePolicy;
    private final IslandBankRepository bankRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;
    private final UpgradeEffectApplier effectApplier;

    public IslandUpgradeRoutes(
            IslandUpgradeRepository upgradeRepository,
            IslandUpgradeService upgradeService,
            UpgradePolicy upgradePolicy,
            IslandBankRepository bankRepository,
            IslandLimitRepository limitRepository,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.upgradeRepository = upgradeRepository;
        this.upgradeService = upgradeService;
        this.upgradePolicy = upgradePolicy;
        this.bankRepository = bankRepository;
        this.limitRepository = limitRepository;
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
        this.effectApplier = new UpgradeEffectApplier(limitRepository, islandRepository, metadataRepository, islandLogs, events);
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/upgrades", this::upgrades);
        registry.routePost("/v1/islands/upgrades/purchase", this::purchase);
        registry.routePost("/v1/islands/upgrades/recalculate", this::recalculate);
    }

    private void upgrades(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, upgradesJson(upgradeRepository.list(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void purchase(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String upgradeKey = JsonFields.text(body, "upgradeKey", "size").toLowerCase();
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
            return;
        }
        UpgradePurchaseResult result = upgradeService.purchase(islandId, upgradeKey);
        audit.log(actorUuid, "PLAYER", "ISLAND_UPGRADE_PURCHASE", "ISLAND", islandId.toString(), Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_PURCHASE", Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
        if (result.accepted()) {
            events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of("islandId", islandId.toString(), "upgradeKey", upgradeKey, "level", Integer.toString(result.snapshot().level())));
            effectApplier.apply(islandId, actorUuid, upgradePolicy.rule(upgradeKey), result.snapshot().type(), result.snapshot().level());
            if (result.cost().signum() > 0) {
                String balance = bankRepository.balance(islandId).balance();
                events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "UPGRADE_PURCHASE", "amount", result.cost().toPlainString(), "balance", balance));
            }
        }
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, upgradePurchaseJson(result));
    }

    private void recalculate(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
            return;
        }
        int applied = recalculateUpgradeEffects(islandId, actorUuid);
        CoreHttpResponses.write(exchange, 202, upgradeRecalculationJson(islandId, applied, upgradeRepository.list(islandId)));
    }

    int recalculateUpgradeEffects(UUID islandId, UUID actorUuid) {
        int applied = 0;
        for (IslandUpgradeSnapshot upgrade : upgradeRepository.list(islandId)) {
            if (upgrade.level() <= 0) {
                continue;
            }
            effectApplier.apply(islandId, actorUuid, upgradePolicy.rule(upgrade.upgradeKey()), upgrade.type(), upgrade.level());
            applied++;
        }
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_RECALCULATE", Map.of("applied", Integer.toString(applied)));
        events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of("islandId", islandId.toString(), "operation", "RECALCULATE", "applied", Integer.toString(applied)));
        return applied;
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

    static String upgradesJson(List<IslandUpgradeSnapshot> upgrades) {
        List<Object> renderedUpgrades = new ArrayList<>();
        for (IslandUpgradeSnapshot upgrade : upgrades) {
            renderedUpgrades.add(upgradeMap(upgrade));
        }
        return SimpleJson.stringify(Map.of("upgrades", renderedUpgrades));
    }

    static String upgradePurchaseJson(UpgradePurchaseResult result) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", result.accepted());
        values.put("code", result.code());
        values.put("cost", result.cost().toPlainString());
        values.put("upgrade", result.snapshot() == null ? null : upgradeMap(result.snapshot()));
        return SimpleJson.stringify(values);
    }

    static String upgradeJson(IslandUpgradeSnapshot upgrade) {
        return SimpleJson.stringify(upgradeMap(upgrade));
    }

    static String upgradeRecalculationJson(UUID islandId, int applied, List<IslandUpgradeSnapshot> upgrades) {
        List<Object> renderedUpgrades = new ArrayList<>();
        for (IslandUpgradeSnapshot upgrade : upgrades) {
            renderedUpgrades.add(upgradeMap(upgrade));
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("islandId", islandId);
        values.put("applied", applied);
        values.put("upgrades", renderedUpgrades);
        return SimpleJson.stringify(values);
    }

    private static Map<String, Object> upgradeMap(IslandUpgradeSnapshot upgrade) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", upgrade.islandId());
        values.put("upgradeKey", upgrade.upgradeKey());
        values.put("type", upgrade.type());
        values.put("level", upgrade.level());
        values.put("updatedAt", upgrade.updatedAt());
        return values;
    }
}

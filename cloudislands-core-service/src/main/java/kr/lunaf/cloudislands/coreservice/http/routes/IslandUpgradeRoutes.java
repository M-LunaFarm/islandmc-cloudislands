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
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
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
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradeRule;

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
        this(upgradeRepository, upgradeService, upgradePolicy, bankRepository, limitRepository, null, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events);
    }

    public IslandUpgradeRoutes(
            IslandUpgradeRepository upgradeRepository,
            IslandUpgradeService upgradeService,
            UpgradePolicy upgradePolicy,
            IslandBankRepository bankRepository,
            IslandLimitRepository limitRepository,
            IslandGeneratorRepository generatorRepository,
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
        this.effectApplier = new UpgradeEffectApplier(limitRepository, islandRepository, metadataRepository, generatorRepository, islandLogs, events);
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/upgrades", this::upgrades);
        registry.routePost("/v1/islands/upgrades/purchase", this::purchase);
        registry.routePost("/v1/admin/islands/upgrades/purchase", this::adminPurchase);
        registry.routePost("/v1/admin/islands/upgrades/recalculate", this::adminRecalculate);
        registry.routePost("/v1/islands/upgrades/recalculate", this::recalculate);
    }

    private void upgrades(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, upgradesJson(upgradeRepository.list(JsonFields.uuid(body, "islandId", EMPTY_UUID)), upgradePolicy));
    }

    private void purchase(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String upgradeKey = JsonFields.text(body, "upgradeKey", "size").toLowerCase();
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
            return;
        }
        writePurchase(exchange, islandId, actorUuid, "PLAYER", "ISLAND_UPGRADE_PURCHASE", upgradeKey);
    }

    private void adminPurchase(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String upgradeKey = JsonFields.text(body, "upgradeKey", "size").toLowerCase();
        if (!requireIsland(exchange, islandId)) {
            return;
        }
        writePurchase(exchange, islandId, EMPTY_UUID, "ADMIN", "ISLAND_UPGRADE_ADMIN_PURCHASE", upgradeKey);
    }

    private void writePurchase(HttpExchange exchange, UUID islandId, UUID actorUuid, String actorType, String action, String upgradeKey) throws IOException {
        UpgradePurchaseResult result = upgradeService.purchase(islandId, upgradeKey);
        Map<String, String> fields = Map.of(
            "upgradeKey", upgradeKey,
            "code", result.code(),
            "cost", result.cost().toPlainString(),
            "itemCosts", SimpleJson.stringify(result.itemCosts())
        );
        audit.log(actorUuid, actorType, action, "ISLAND", islandId.toString(), fields);
        islandLogs.append(islandId, actorUuid, action, fields);
        if (result.accepted()) {
            events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of("islandId", islandId.toString(), "upgradeKey", upgradeKey, "level", Integer.toString(result.snapshot().level()), "actorType", actorType));
            effectApplier.apply(islandId, actorUuid, upgradePolicy.rule(upgradeKey), result.snapshot().type(), result.snapshot().level());
            if (result.cost().signum() > 0) {
                String balance = bankRepository.balance(islandId).balance();
                events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "actorType", actorType, "operation", "UPGRADE_PURCHASE", "amount", result.cost().toPlainString(), "balance", balance));
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

    private void adminRecalculate(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        if (!requireIsland(exchange, islandId)) {
            return;
        }
        int applied = recalculateUpgradeEffects(islandId, EMPTY_UUID);
        audit.log(EMPTY_UUID, "ADMIN", "ISLAND_UPGRADE_ADMIN_RECALCULATE", "ISLAND", islandId.toString(), Map.of("applied", Integer.toString(applied)));
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

    private boolean requireIsland(HttpExchange exchange, UUID islandId) throws IOException {
        if (islandRepository != null && islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return false;
        }
        return true;
    }

    static String upgradesJson(List<IslandUpgradeSnapshot> upgrades) {
        return upgradesJson(upgrades, null);
    }

    static String upgradesJson(List<IslandUpgradeSnapshot> upgrades, UpgradePolicy policy) {
        List<Object> renderedUpgrades = new ArrayList<>();
        Map<String, IslandUpgradeSnapshot> stored = new LinkedHashMap<>();
        for (IslandUpgradeSnapshot upgrade : upgrades) {
            stored.put(upgrade.upgradeKey(), upgrade);
        }
        if (policy != null) {
            for (UpgradeRule rule : policy.list().stream().sorted(java.util.Comparator.comparing(UpgradeRule::upgradeKey)).toList()) {
                IslandUpgradeSnapshot upgrade = stored.remove(rule.upgradeKey());
                int level = upgrade == null ? 0 : upgrade.level();
                LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                values.put("upgradeKey", rule.upgradeKey());
                values.put("type", rule.type());
                values.put("level", level);
                values.put("maxLevel", rule.maxLevel());
                values.put("nextCost", level >= rule.maxLevel() ? "" : rule.costForNextLevel(level).toPlainString());
                values.put("nextItemCosts", level >= rule.maxLevel() ? Map.of() : rule.itemCostsForNextLevel(level));
                values.put("updatedAt", upgrade == null ? null : upgrade.updatedAt());
                renderedUpgrades.add(values);
            }
        }
        for (IslandUpgradeSnapshot upgrade : stored.values()) {
            renderedUpgrades.add(upgradeMap(upgrade));
        }
        return SimpleJson.stringify(Map.of("upgrades", renderedUpgrades));
    }

    static String upgradePurchaseJson(UpgradePurchaseResult result) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", result.accepted());
        values.put("code", result.code());
        values.put("cost", result.cost().toPlainString());
        values.put("itemCosts", result.itemCosts());
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

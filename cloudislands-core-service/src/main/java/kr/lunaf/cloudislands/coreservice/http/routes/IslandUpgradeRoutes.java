package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
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
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/upgrades", this::upgrades);
        registry.route("/v1/islands/upgrades/purchase", this::purchase);
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
            applyUpgradeLimit(islandId, actorUuid, upgradePolicy.rule(upgradeKey), result.snapshot().type(), result.snapshot().level());
            applyUpgradeFlag(islandId, result.snapshot().type());
            if (result.cost().signum() > 0) {
                String balance = bankRepository.balance(islandId).balance();
                events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "UPGRADE_PURCHASE", "amount", result.cost().toPlainString(), "balance", balance));
            }
        }
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, upgradePurchaseJson(result));
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissionRules.allowed(islandId, actorUuid, member.role(), permission));
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

    private void applyUpgradeLimit(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        java.util.OptionalLong configuredValue = rule == null ? java.util.OptionalLong.empty() : rule.limitValueForLevel(level);
        IslandLimitSnapshot snapshot = switch (type) {
            case ISLAND_SIZE -> limitRepository.set(islandId, "SIZE", configuredValue.orElse(100L + Math.max(0L, level - 1L) * 50L), actorUuid);
            case MAX_MEMBERS -> limitRepository.set(islandId, "MEMBERS", configuredValue.orElse(3L + Math.max(0L, level - 1L) * 2L), actorUuid);
            case MAX_WARPS -> limitRepository.set(islandId, "WARPS", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case HOPPER_LIMIT -> limitRepository.set(islandId, "HOPPER", configuredValue.orElse(Math.max(1L, level) * 50L), actorUuid);
            case SPAWNER_LIMIT -> limitRepository.set(islandId, "SPAWNER", configuredValue.orElse(Math.max(1L, level) * 25L), actorUuid);
            case MOB_LIMIT -> limitRepository.set(islandId, "ENTITY", configuredValue.orElse(Math.max(1L, level) * 200L), actorUuid);
            case REDSTONE_LIMIT -> limitRepository.set(islandId, "REDSTONE", configuredValue.orElse(Math.max(1L, level) * 512L), actorUuid);
            case BANK_LIMIT -> limitRepository.set(islandId, "BANK", configuredValue.orElse(Math.max(1L, level) * 100000L), actorUuid);
            case GENERATOR_LEVEL, CROP_GROWTH, FLY_ACCESS -> null;
        };
        if (snapshot != null) {
            events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of("islandId", islandId.toString(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
        }
    }

    private void applyUpgradeFlag(UUID islandId, UpgradeType type) {
        if (type != UpgradeType.FLY_ACCESS) {
            return;
        }
        metadataRepository.setFlag(islandId, IslandFlag.FLY, "true");
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", IslandFlag.FLY.name(), "value", "true"));
    }

    static String upgradesJson(java.util.List<IslandUpgradeSnapshot> upgrades) {
        StringBuilder builder = new StringBuilder("{\"upgrades\":[");
        boolean first = true;
        for (IslandUpgradeSnapshot upgrade : upgrades) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(upgradeJson(upgrade));
        }
        return builder.append("]}").toString();
    }

    static String upgradePurchaseJson(UpgradePurchaseResult result) {
        return "{\"accepted\":" + result.accepted()
            + ",\"code\":\"" + result.code() + "\""
            + ",\"cost\":\"" + result.cost().toPlainString() + "\""
            + ",\"upgrade\":" + (result.snapshot() == null ? "null" : upgradeJson(result.snapshot()))
            + "}";
    }

    static String upgradeJson(IslandUpgradeSnapshot upgrade) {
        return "{\"islandId\":\"" + upgrade.islandId()
            + "\",\"upgradeKey\":\"" + escape(upgrade.upgradeKey())
            + "\",\"type\":\"" + upgrade.type()
            + "\",\"level\":" + upgrade.level()
            + ",\"updatedAt\":\"" + upgrade.updatedAt()
            + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

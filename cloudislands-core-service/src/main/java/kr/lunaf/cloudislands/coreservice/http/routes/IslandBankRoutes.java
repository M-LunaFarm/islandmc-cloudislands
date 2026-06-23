package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
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

public final class IslandBankRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandBankRepository bankRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandBankRoutes(
            IslandBankRepository bankRepository,
            IslandLimitRepository limitRepository,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
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
        registry.routePost("/v1/islands/bank", this::bank);
        registry.routePost("/v1/islands/bank/deposit", this::deposit);
        registry.routePost("/v1/islands/bank/withdraw", this::withdraw);
    }

    private void bank(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, bankJson(bankRepository.balance(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void deposit(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        BigDecimal amount = amount(body);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.DEPOSIT_BANK)) {
            return;
        }
        if (amount.signum() <= 0) {
            CoreHttpResponses.write(exchange, 409, bankChangeJson(new IslandBankChangeSnapshot(false, "INVALID_AMOUNT", bankRepository.balance(islandId))));
            return;
        }
        long bankLimit = limitValue(islandId, "BANK", Long.MAX_VALUE);
        var result = bankRepository.deposit(islandId, amount, bankLimit == Long.MAX_VALUE ? null : BigDecimal.valueOf(bankLimit));
        if (!result.accepted()) {
            CoreHttpResponses.write(exchange, 409, bankChangeJson(new IslandBankChangeSnapshot(false, result.code(), result.snapshot())));
            return;
        }
        var snapshot = result.snapshot();
        audit.log(actorUuid, "PLAYER", "ISLAND_BANK_DEPOSIT", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
        islandLogs.append(islandId, actorUuid, "ISLAND_BANK_DEPOSIT", Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
        events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "DEPOSIT", "amount", amount.toPlainString(), "balance", snapshot.balance()));
        CoreHttpResponses.write(exchange, 202, bankJson(snapshot));
    }

    private void withdraw(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        BigDecimal amount = amount(body);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.WITHDRAW_BANK)) {
            return;
        }
        var result = bankRepository.withdraw(islandId, amount);
        audit.log(actorUuid, "PLAYER", "ISLAND_BANK_WITHDRAW", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
        islandLogs.append(islandId, actorUuid, "ISLAND_BANK_WITHDRAW", Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
        if (result.accepted()) {
            events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "WITHDRAW", "amount", amount.toPlainString(), "balance", result.snapshot().balance()));
        }
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, bankChangeJson(new IslandBankChangeSnapshot(result.accepted(), result.code(), result.snapshot())));
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

    private long limitValue(UUID islandId, String limitKey, long fallback) {
        return limitRepository.list(islandId).stream()
            .filter(limit -> limit.limitKey().equals(limitKey))
            .mapToLong(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot::value)
            .findFirst()
            .orElse(fallback);
    }

    static BigDecimal amount(String body) {
        try {
            return new BigDecimal(JsonFields.text(body, "amount", "0"));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    static String bankJson(IslandBankSnapshot bank) {
        return SimpleJson.stringify(bankMap(bank));
    }

    static String bankChangeJson(IslandBankChangeSnapshot change) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", change.accepted());
        values.put("code", change.code());
        values.put("bank", bankMap(change.bank()));
        return SimpleJson.stringify(values);
    }

    private static Map<String, Object> bankMap(IslandBankSnapshot bank) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", bank.islandId());
        values.put("balance", bank.balance());
        values.put("updatedAt", bank.updatedAt());
        return values;
    }
}

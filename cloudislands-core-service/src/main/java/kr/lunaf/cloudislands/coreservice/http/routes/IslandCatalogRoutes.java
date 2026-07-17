package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.RoutingOrchestrator;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;

public final class IslandCatalogRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandLimitRepository limitRepository;
    private final PlayerProfileRepository playerProfiles;
    private final CreateIslandWorkflow createIsland;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;

    public IslandCatalogRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            CreateIslandWorkflow createIsland,
            IslandLogRepository islandLogs,
            AuditLogger audit) {
        this(islandRepository, metadataRepository, null, null, createIsland, islandLogs, audit);
    }

    public IslandCatalogRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandLimitRepository limitRepository,
            CreateIslandWorkflow createIsland,
            IslandLogRepository islandLogs,
            AuditLogger audit) {
        this(islandRepository, metadataRepository, limitRepository, null, createIsland, islandLogs, audit);
    }

    public IslandCatalogRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandLimitRepository limitRepository,
            PlayerProfileRepository playerProfiles,
            CreateIslandWorkflow createIsland,
            IslandLogRepository islandLogs,
            AuditLogger audit) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.limitRepository = limitRepository;
        this.playerProfiles = playerProfiles;
        this.createIsland = createIsland;
        this.islandLogs = islandLogs;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/info", this::info);
        registry.routePost("/v1/islands/public", this::publicIslands);
        registry.routePost("/v1/islands", this::create);
    }

    private void info(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID ownerUuid = JsonFields.uuid(body, "ownerUuid", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "");
        Optional<IslandSnapshot> island = islandId.equals(EMPTY_UUID)
            ? ownerUuid.equals(EMPTY_UUID) ? islandRepository.findByName(name) : islandRepository.findByOwner(ownerUuid)
            : islandRepository.findById(islandId);
        CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(value -> islandJson(value, limitRepository, metadataRepository, playerProfiles)).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    private void publicIslands(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 27), 1, 54);
        int offset = queryInteger(exchange, "offset", JsonFields.integer(body, "offset", 0), 0, 100_000);
        List<IslandSnapshot> islands = metadataRepository.publicIslandIdsPage(offset, limit).stream()
            .map(islandRepository::findById)
            .flatMap(Optional::stream)
            .sorted(Comparator.comparingLong(IslandSnapshot::level).reversed().thenComparing(IslandSnapshot::name))
            .toList();
        CoreHttpResponses.write(exchange, 200, islandsJson(islands, limitRepository, metadataRepository, playerProfiles));
    }

    private void create(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String templateId = JsonFields.text(body, "templateId", "default");
        boolean economySettlementManaged = JsonFields.bool(body, "economySettlementManaged", false);
        String settledCreationCost = JsonFields.text(body, "settledCreationCost", "");
        CreateIslandResult result = createIsland.create(playerUuid, templateId, economySettlementManaged, settledCreationCost);
        if (result.accepted() && result.island() != null) {
            metadataRepository.upsertMemberKey(result.island().islandId(), playerUuid, CoreRoleKeys.OWNER);
            islandLogs.append(result.island().islandId(), playerUuid, "ISLAND_CREATE", Map.of("templateId", templateId));
        }
        audit.log(playerUuid, "PLAYER", "ISLAND_CREATE", "ISLAND", result.island() == null ? "" : result.island().islandId().toString(), Map.of("code", result.code()));
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, createResultJson(result));
    }

    static String createResultJson(CreateIslandResult result) {
        Object ticket = result.ticket() == null ? null : SimpleJson.parse(RoutingOrchestrator.toJson(result.ticket()));
        Object islandId = result.island() == null ? "" : result.island().islandId();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", result.accepted());
        values.put("code", result.code());
        values.put("islandId", islandId);
        values.put("ticket", ticket);
        return SimpleJson.stringify(values);
    }

    static String islandsJson(List<IslandSnapshot> islands) {
        return islandsJson(islands, null);
    }

    static String islandsJson(List<IslandSnapshot> islands, IslandLimitRepository limits) {
        return islandsJson(islands, limits, null);
    }

    static String islandsJson(List<IslandSnapshot> islands, IslandLimitRepository limits, IslandMetadataRepository metadata) {
        return islandsJson(islands, limits, metadata, null);
    }

    static String islandsJson(List<IslandSnapshot> islands, IslandLimitRepository limits, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles) {
        List<Object> renderedIslands = new ArrayList<>();
        for (IslandSnapshot island : islands) {
            renderedIslands.add(islandMap(island, limits, metadata, playerProfiles));
        }
        return SimpleJson.stringify(Map.of("islands", renderedIslands));
    }

    static String islandJson(IslandSnapshot island) {
        return islandJson(island, null);
    }

    static String islandJson(IslandSnapshot island, IslandLimitRepository limits) {
        return islandJson(island, limits, null);
    }

    static String islandJson(IslandSnapshot island, IslandLimitRepository limits, IslandMetadataRepository metadata) {
        return islandJson(island, limits, metadata, null);
    }

    static String islandJson(IslandSnapshot island, IslandLimitRepository limits, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles) {
        return SimpleJson.stringify(islandMap(island, limits, metadata, playerProfiles));
    }

    static Map<String, Object> islandMap(IslandSnapshot island, IslandLimitRepository limits) {
        return islandMap(island, limits, null);
    }

    static Map<String, Object> islandMap(IslandSnapshot island, IslandLimitRepository limits, IslandMetadataRepository metadata) {
        return islandMap(island, limits, metadata, null);
    }

    static Map<String, Object> islandMap(IslandSnapshot island, IslandLimitRepository limits, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", island.islandId());
        values.put("ownerUuid", island.ownerUuid());
        if (playerProfiles != null) {
            String ownerName = playerProfiles.find(island.ownerUuid()).lastName();
            if (ownerName != null && !ownerName.isBlank()) {
                values.put("ownerName", ownerName);
            }
        }
        values.put("name", island.name());
        values.put("description", metadata == null ? "" : metadata.flags(island.islandId()).values().getOrDefault(IslandFlag.PROFILE_DESCRIPTION, ""));
        values.put("state", island.state());
        values.put("size", island.size());
        values.put("border", authoritativeBorder(island, limits));
        values.put("level", island.level());
        values.put("worth", island.worth());
        values.put("publicAccess", island.publicAccess());
        values.put("createdAt", island.createdAt());
        values.put("updatedAt", island.updatedAt());
        return values;
    }

    private static long authoritativeBorder(IslandSnapshot island, IslandLimitRepository limits) {
        if (limits == null) {
            return island.size();
        }
        return limits.list(island.islandId()).stream()
            .filter(limit -> limit.limitKey().equals("BORDER"))
            .mapToLong(limit -> limit.value())
            .findFirst()
            .orElse(island.size());
    }

    static int queryInteger(HttpExchange exchange, String key, int fallback, int min, int max) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Math.max(min, Math.min(fallback, max));
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            try {
                return Math.max(min, Math.min(Integer.parseInt(part.substring(separator + 1)), max));
            } catch (NumberFormatException ignored) {
                return Math.max(min, Math.min(fallback, max));
            }
        }
        return Math.max(min, Math.min(fallback, max));
    }
}

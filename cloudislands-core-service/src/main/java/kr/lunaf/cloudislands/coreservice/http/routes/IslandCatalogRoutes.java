package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.RoutingOrchestrator;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;

public final class IslandCatalogRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final CreateIslandWorkflow createIsland;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;

    public IslandCatalogRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            CreateIslandWorkflow createIsland,
            IslandLogRepository islandLogs,
            AuditLogger audit) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.createIsland = createIsland;
        this.islandLogs = islandLogs;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/info", this::info);
        registry.route("/v1/islands/public", this::publicIslands);
        registry.route("/v1/islands", this::create);
    }

    private void info(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID ownerUuid = JsonFields.uuid(body, "ownerUuid", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "");
        Optional<IslandSnapshot> island = islandId.equals(EMPTY_UUID)
            ? ownerUuid.equals(EMPTY_UUID) ? islandRepository.findByName(name) : islandRepository.findByOwner(ownerUuid)
            : islandRepository.findById(islandId);
        CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(IslandCatalogRoutes::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    private void publicIslands(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 27), 1, 54);
        List<IslandSnapshot> islands = metadataRepository.publicIslandIds(limit).stream()
            .map(islandRepository::findById)
            .flatMap(Optional::stream)
            .sorted(Comparator.comparingLong(IslandSnapshot::level).reversed().thenComparing(IslandSnapshot::name))
            .toList();
        CoreHttpResponses.write(exchange, 200, islandsJson(islands));
    }

    private void create(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String templateId = JsonFields.text(body, "templateId", "default");
        CreateIslandResult result = createIsland.create(playerUuid, templateId);
        if (result.accepted() && result.island() != null) {
            metadataRepository.upsertMember(result.island().islandId(), playerUuid, IslandRole.OWNER);
            islandLogs.append(result.island().islandId(), playerUuid, "ISLAND_CREATE", Map.of("templateId", templateId));
        }
        audit.log(playerUuid, "PLAYER", "ISLAND_CREATE", "ISLAND", result.island() == null ? "" : result.island().islandId().toString(), Map.of("code", result.code()));
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, createResultJson(result));
    }

    static String createResultJson(CreateIslandResult result) {
        String ticketJson = result.ticket() == null ? "null" : RoutingOrchestrator.toJson(result.ticket());
        String islandId = result.island() == null ? "" : result.island().islandId().toString();
        return "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"islandId\":\"" + islandId + "\",\"ticket\":" + ticketJson + "}";
    }

    static String islandsJson(List<IslandSnapshot> islands) {
        StringBuilder builder = new StringBuilder("{\"islands\":[");
        boolean first = true;
        for (IslandSnapshot island : islands) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(islandJson(island));
        }
        return builder.append("]}").toString();
    }

    static String islandJson(IslandSnapshot island) {
        return "{\"islandId\":\"" + island.islandId()
            + "\",\"ownerUuid\":\"" + island.ownerUuid()
            + "\",\"name\":\"" + escape(island.name())
            + "\",\"state\":\"" + island.state()
            + "\",\"size\":" + island.size()
            + ",\"border\":" + island.size()
            + ",\"level\":" + island.level()
            + ",\"worth\":\"" + escape(island.worth())
            + "\",\"publicAccess\":" + island.publicAccess()
            + ",\"createdAt\":\"" + island.createdAt()
            + "\",\"updatedAt\":\"" + island.updatedAt()
            + "\"}";
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

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

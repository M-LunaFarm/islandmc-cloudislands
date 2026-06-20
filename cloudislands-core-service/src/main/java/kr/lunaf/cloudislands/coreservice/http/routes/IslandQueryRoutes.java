package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;

public final class IslandQueryRoutes {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final String ISLAND_PREFIX = "/v1/islands/";
    private static final String PLAYER_PREFIX = "/v1/players/";

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandRuntimeRepository runtimeRepository;
    private final IslandLevelRepository levelRepository;
    private final RankingRecalculationService levelRecalculation;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandRoleRepository roleRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandUpgradeRepository upgradeRepository;
    private final IslandBankRepository bankRepository;
    private final IslandMissionRepository missionRepository;
    private final IslandSnapshotRepository snapshotRepository;
    private final IslandLogRepository islandLogs;
    private final PlayerProfileRepository playerProfiles;
    private final AuditLogger audit;
    private final IslandDeleteRequester deleteRequester;

    public IslandQueryRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandRuntimeRepository runtimeRepository,
            IslandLevelRepository levelRepository,
            RankingRecalculationService levelRecalculation,
            IslandPermissionRuleRepository permissionRules,
            IslandRoleRepository roleRepository,
            IslandLimitRepository limitRepository,
            IslandUpgradeRepository upgradeRepository,
            IslandBankRepository bankRepository,
            IslandMissionRepository missionRepository,
            IslandSnapshotRepository snapshotRepository,
            IslandLogRepository islandLogs,
            PlayerProfileRepository playerProfiles,
            AuditLogger audit,
            IslandDeleteRequester deleteRequester) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.runtimeRepository = runtimeRepository;
        this.levelRepository = levelRepository;
        this.levelRecalculation = levelRecalculation;
        this.permissionRules = permissionRules;
        this.roleRepository = roleRepository;
        this.limitRepository = limitRepository;
        this.upgradeRepository = upgradeRepository;
        this.bankRepository = bankRepository;
        this.missionRepository = missionRepository;
        this.snapshotRepository = snapshotRepository;
        this.islandLogs = islandLogs;
        this.playerProfiles = playerProfiles;
        this.audit = audit;
        this.deleteRequester = deleteRequester;
    }

    public void register(CoreRouteRegistry prefixRegistry) {
        prefixRegistry.route(ISLAND_PREFIX, this::islandPrefix);
        prefixRegistry.route(PLAYER_PREFIX, this::playerPrefix);
    }

    private void islandPrefix(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String tail = exchange.getRequestURI().getPath().substring(ISLAND_PREFIX.length());
        if (method.equalsIgnoreCase("GET") && tail.startsWith("by-owner/")) {
            UUID ownerUuid = uuidPath(tail.substring("by-owner/".length()));
            Optional<IslandSnapshot> island = islandRepository.findByOwner(ownerUuid);
            CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(IslandCatalogRoutes::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/members")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/members".length()));
            CoreHttpResponses.write(exchange, 200, IslandMemberRoutes.membersJson(metadataRepository.members(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/runtime")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/runtime".length()));
            Optional<IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
            CoreHttpResponses.write(exchange, runtime.isPresent() ? 200 : 404, runtime.map(IslandQueryRoutes::runtimeJson).orElseGet(() -> ApiResponses.error("ISLAND_RUNTIME_NOT_FOUND", "Island runtime was not found")));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/flags")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/flags".length()));
            CoreHttpResponses.write(exchange, 200, IslandSettingsRoutes.flagsJson(metadataRepository.flags(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/level")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/level".length()));
            if (islandRepository.findById(islandId).isEmpty()) {
                CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
                return;
            }
            var snapshot = levelRecalculation.recalculate(islandId, levelRepository.blockCounts(islandId), levelRepository.blockValues(), metadataRepository.members(islandId).size());
            CoreHttpResponses.write(exchange, 200, IslandBlockLevelRoutes.levelJson(snapshot));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/permissions")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/permissions".length()));
            CoreHttpResponses.write(exchange, 200, PermissionRoleRoutes.permissionsJson(permissionRules.list(islandId), permissionRules.listPlayerOverrides(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/roles")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/roles".length()));
            CoreHttpResponses.write(exchange, 200, PermissionRoleRoutes.rolesJson(roleRepository.list(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/bans")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/bans".length()));
            CoreHttpResponses.write(exchange, 200, IslandVisitorRoutes.bansJson(metadataRepository.bans(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/biome")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/biome".length()));
            CoreHttpResponses.write(exchange, 200, IslandSettingsRoutes.biomeJson(metadataRepository.biome(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/homes")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/homes".length()));
            CoreHttpResponses.write(exchange, 200, IslandWarpRoutes.homesJson(metadataRepository.homes(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/warps")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/warps".length()));
            CoreHttpResponses.write(exchange, 200, IslandWarpRoutes.warpsJson(metadataRepository.warps(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/limits")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/limits".length()));
            CoreHttpResponses.write(exchange, 200, ProgressionRoutes.limitsJson(limitRepository.list(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/upgrades")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/upgrades".length()));
            CoreHttpResponses.write(exchange, 200, IslandUpgradeRoutes.upgradesJson(upgradeRepository.list(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/bank")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/bank".length()));
            CoreHttpResponses.write(exchange, 200, IslandBankRoutes.bankJson(bankRepository.balance(islandId)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/missions")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/missions".length()));
            CoreHttpResponses.write(exchange, 200, ProgressionRoutes.missionsJson(missionRepository.list(islandId, "MISSION")));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/snapshots")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/snapshots".length()));
            CoreHttpResponses.write(exchange, 200, IslandSnapshotRoutes.snapshotsJson(snapshotRepository.list(islandId, 20)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/logs")) {
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/logs".length()));
            CoreHttpResponses.write(exchange, 200, IslandCommunicationRoutes.islandLogsJson(islandLogs.list(islandId, 30)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && !tail.contains("/")) {
            UUID islandId = uuidPath(tail);
            Optional<IslandSnapshot> island = islandRepository.findById(islandId);
            CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(IslandCatalogRoutes::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
            return;
        }
        if (method.equalsIgnoreCase("DELETE") && !tail.contains("/")) {
            UUID islandId = uuidPath(tail);
            Optional<IslandSnapshot> island = islandRepository.findById(islandId);
            UUID requesterUuid = queryUuid(exchange, "requesterUuid", island.map(IslandSnapshot::ownerUuid).orElse(SYSTEM_ACTOR));
            boolean deleted = island.isPresent() && deleteRequester.request(islandId, requesterUuid, island.get().ownerUuid(), "api-delete");
            audit.log(SYSTEM_ACTOR, "API", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
            CoreHttpResponses.write(exchange, deleted ? 202 : 404, deleted ? ApiResponses.ok(true) : ApiResponses.error("ISLAND_NOT_DELETED", "Island was not found or could not be deleted"));
            return;
        }
        CoreHttpResponses.write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
    }

    private void playerPrefix(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String tail = exchange.getRequestURI().getPath().substring(PLAYER_PREFIX.length());
        if (method.equalsIgnoreCase("GET") && !tail.contains("/")) {
            UUID playerUuid = uuidPath(tail);
            CoreHttpResponses.write(exchange, 200, PlayerProfileRoutes.playerProfileJson(playerProfiles.find(playerUuid)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/profile")) {
            UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/profile".length()));
            CoreHttpResponses.write(exchange, 200, PlayerProfileRoutes.playerProfileJson(playerProfiles.find(playerUuid)));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/island")) {
            UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/island".length()));
            Optional<IslandSnapshot> island = islandRepository.findByOwner(playerUuid);
            CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(IslandCatalogRoutes::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
            return;
        }
        if (method.equalsIgnoreCase("GET") && tail.endsWith("/islands")) {
            UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/islands".length()));
            ArrayList<IslandSnapshot> islands = new ArrayList<>();
            for (var member : metadataRepository.islandsForMember(playerUuid)) {
                islandRepository.findById(member.islandId()).ifPresent(islands::add);
            }
            CoreHttpResponses.write(exchange, 200, IslandCatalogRoutes.islandsJson(islands));
            return;
        }
        CoreHttpResponses.write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
    }

    static String runtimeJson(IslandRuntimeSnapshot runtime) {
        return "{\"islandId\":\"" + runtime.islandId()
            + "\",\"state\":\"" + runtime.state()
            + "\",\"activeNode\":" + nullable(runtime.activeNode())
            + ",\"activeWorld\":" + nullable(runtime.activeWorld())
            + ",\"cellX\":" + (runtime.cellX() == null ? "null" : runtime.cellX())
            + ",\"cellZ\":" + (runtime.cellZ() == null ? "null" : runtime.cellZ())
            + ",\"leaseOwner\":" + nullable(runtime.leaseOwner())
            + ",\"fencingToken\":" + runtime.fencingToken()
            + ",\"activatedAt\":" + nullable(runtime.activatedAt() == null ? null : runtime.activatedAt().toString())
            + ",\"lastHeartbeat\":" + nullable(runtime.lastHeartbeat() == null ? null : runtime.lastHeartbeat().toString())
            + "}";
    }

    private static String nullable(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static UUID queryUuid(HttpExchange exchange, String key, UUID fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return fallback;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            try {
                return UUID.fromString(part.substring(separator + 1));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static UUID uuidPath(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return new UUID(0L, 0L);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    public interface IslandDeleteRequester {
        boolean request(UUID islandId, UUID ownerUuid, UUID requesterUuid, String reason);
    }
}

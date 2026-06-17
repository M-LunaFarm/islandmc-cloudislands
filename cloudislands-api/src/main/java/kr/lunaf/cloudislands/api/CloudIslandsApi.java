package kr.lunaf.cloudislands.api;

import java.util.Map;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.IslandStatusService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;

public interface CloudIslandsApi {
    default String readConsistencyPolicy() {
        return "query-services-use-core-api-or-local-cache-snapshots-no-direct-storage-access";
    }

    default String writeAuthorityPolicy() {
        return "all-island-writes-go-through-core-api-transaction-endpoints";
    }

    default String synchronousEventPolicy() {
        return "synchronous-paper-events-must-use-local-protection-permission-caches";
    }

    default String addonStoragePolicy() {
        return "addons-use-addon-state-api-or-their-own-shared-database-never-cloudislands-internals";
    }

    default Map<String, String> contractMetadata() {
        return Map.of(
            "read-policy", readConsistencyPolicy(),
            "write-authority", writeAuthorityPolicy(),
            "sync-event-policy", synchronousEventPolicy(),
            "addon-storage-policy", addonStoragePolicy()
        );
    }

    IslandQueryService islands();
    PlayerIslandService players();
    IslandRoutingService routing();
    IslandPermissionService permissions();
    IslandRuntimeService runtime();
    IslandStatusService status();
    IslandEventService events();
    IslandAdminService admin();
    IslandCommandService commands();
    IslandAddonService addons();
}

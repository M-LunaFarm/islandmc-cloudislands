package kr.lunaf.cloudislands.api;

import java.util.Map;
import kr.lunaf.cloudislands.api.compat.ApiCompatibilityPolicy;
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
        return CloudIslandsApiContract.READ_POLICY;
    }

    default String writeAuthorityPolicy() {
        return CloudIslandsApiContract.WRITE_AUTHORITY;
    }

    default String synchronousEventPolicy() {
        return CloudIslandsApiContract.SYNC_EVENT_POLICY;
    }

    default String addonStoragePolicy() {
        return CloudIslandsApiContract.ADDON_STORAGE_POLICY;
    }

    default Map<String, String> contractMetadata() {
        return CloudIslandsApiContract.metadata();
    }

    default String apiRuntimeVersion() {
        return CloudIslandsApiContract.RUNTIME_API_VERSION;
    }

    default String apiCompatibilityStatus(String requestedApiVersion) {
        return ApiCompatibilityPolicy.evaluate(requestedApiVersion, apiRuntimeVersion()).status().code();
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

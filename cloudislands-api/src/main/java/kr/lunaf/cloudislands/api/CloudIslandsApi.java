package kr.lunaf.cloudislands.api;

import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.IslandStatusService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;

public interface CloudIslandsApi {
    IslandQueryService islands();
    PlayerIslandService players();
    IslandRoutingService routing();
    IslandPermissionService permissions();
    IslandRuntimeService runtime();
    IslandStatusService status();
    IslandEventService events();
    IslandAdminService admin();
    IslandCommandService commands();
}

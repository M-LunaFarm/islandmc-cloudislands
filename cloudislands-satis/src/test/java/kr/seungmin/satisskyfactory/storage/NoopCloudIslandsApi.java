package kr.seungmin.satisskyfactory.storage;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.IslandStatusService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;

final class NoopCloudIslandsApi implements CloudIslandsApi {
    @Override
    public IslandQueryService islands() {
        return null;
    }

    @Override
    public PlayerIslandService players() {
        return null;
    }

    @Override
    public IslandRoutingService routing() {
        return null;
    }

    @Override
    public IslandPermissionService permissions() {
        return null;
    }

    @Override
    public IslandRuntimeService runtime() {
        return null;
    }

    @Override
    public IslandStatusService status() {
        return null;
    }

    @Override
    public IslandEventService events() {
        return null;
    }

    @Override
    public IslandAdminService admin() {
        return null;
    }

    @Override
    public IslandCommandService commands() {
        return null;
    }

    @Override
    public IslandAddonService addons() {
        return null;
    }
}

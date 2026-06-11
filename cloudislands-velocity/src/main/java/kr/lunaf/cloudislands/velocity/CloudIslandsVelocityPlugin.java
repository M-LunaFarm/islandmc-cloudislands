package kr.lunaf.cloudislands.velocity;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class CloudIslandsVelocityPlugin {
    private static final List<String> ALIASES = List.of("is", "island", "섬");
    private final VelocityRoutingController routingController;

    public CloudIslandsVelocityPlugin(CoreApiClient coreApiClient) {
        this.routingController = new VelocityRoutingController(coreApiClient);
    }

    public List<String> aliases() {
        return ALIASES;
    }

    public void handleHome(UUID playerUuid) {
        routingController.routeHome(playerUuid);
    }

    public void handleVisit(UUID playerUuid, UUID targetIslandId) {
        routingController.routeVisit(playerUuid, targetIslandId);
    }

    public String hiddenNodeMessage() {
        return "섬을 준비하는 중입니다.";
    }
}

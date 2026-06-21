package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class VelocityTargetResolverTest {
    @Test
    void mapsDirectUuidToPendingInviteWhenAvailable() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID targetUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID inviteId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        CoreApiClient core = core(Map.of(
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"00000000-0000-0000-0000-000000000010\",\"inviterUuid\":\"" + targetUuid + "\"}]}"
        ));
        VelocityTargetResolver resolver = new VelocityTargetResolver(core);

        assertEquals(inviteId, resolver.resolveInviteTarget(playerUuid, targetUuid.toString()).join());
    }

    @Test
    void resolvesOnlinePlayerWithoutCoreLookup() {
        UUID onlineUuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        VelocityTargetResolver resolver = new VelocityTargetResolver(core(Map.of()), name -> Optional.of(onlineUuid));

        assertEquals(onlineUuid, resolver.resolvePlayerUuid("online-name").join());
    }

    @Test
    void resolvesIslandNameThroughCoreLookup() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        CoreApiClient core = core(Map.of(
            "islandInfoByName", "{\"islandId\":\"" + islandId + "\"}"
        ));
        VelocityTargetResolver resolver = new VelocityTargetResolver(core);

        assertEquals(islandId, resolver.resolveIslandId("spawn").join());
    }

    @Test
    void returnsEmptyUuidForInvalidUuidText() {
        assertEquals(new UUID(0L, 0L), VelocityTargetResolver.parseUuid("not-a-uuid"));
    }

    private static CoreApiClient core(Map<String, String> responses) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                String response = responses.get(method.getName());
                if (response != null) {
                    return CompletableFuture.completedFuture(response);
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}

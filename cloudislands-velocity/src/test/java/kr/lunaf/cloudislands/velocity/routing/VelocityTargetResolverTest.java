package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.IslandQueryClient;
import kr.lunaf.cloudislands.coreclient.MemberQueryClient;
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
            new Class<?>[] {CoreApiClient.class, MemberQueryClient.class, IslandQueryClient.class},
            (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                if (method.getName().equals("inviteSnapshots") && responses.containsKey("listPendingInvites")) {
                    return CompletableFuture.completedFuture(inviteSnapshots(responses.get("listPendingInvites")));
                }
                if (method.getName().equals("findIslandByName") && responses.containsKey("islandInfoByName")) {
                    String islandId = jsonValue(responses.get("islandInfoByName"), "islandId");
                    return CompletableFuture.completedFuture(new CoreGuiViews.IslandInfoView("", "ACTIVE", islandId, 0L, "", true, false, 0L, 0L, ""));
                }
                String response = responses.get(method.getName());
                if (response != null) {
                    return CompletableFuture.completedFuture(response);
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static java.util.List<IslandInviteSnapshot> inviteSnapshots(String json) {
        return java.util.List.of(new IslandInviteSnapshot(
            UUID.fromString(jsonValue(json, "inviteId")),
            UUID.fromString(jsonValue(json, "islandId")),
            UUID.fromString(jsonValue(json, "inviterUuid")),
            new UUID(0L, 0L),
            "PENDING",
            Instant.EPOCH,
            Instant.EPOCH
        ));
    }

    private static String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? "" : json.substring(valueStart, end);
    }
}

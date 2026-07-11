package kr.lunaf.cloudislands.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import org.junit.jupiter.api.Test;

class IslandCoopApiContractTest {
    @Test
    void exposesTypedCoopsAndIndependentDefaultLimit() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000151");
        UUID coopUuid = UUID.fromString("00000000-0000-0000-0000-000000000152");
        UUID memberUuid = UUID.fromString("00000000-0000-0000-0000-000000000153");
        Instant joinedAt = Instant.parse("2026-07-11T01:00:00Z");
        IslandQueryService queries = queries(
            List.of(
                new IslandMemberSnapshot(islandId, coopUuid, "TRUSTED", joinedAt, null),
                new IslandMemberSnapshot(islandId, memberUuid, "MEMBER", joinedAt, null)
            ),
            List.of()
        );

        assertEquals(List.of(coopUuid), queries.getCoops(islandId).join().stream().map(coop -> coop.playerUuid()).toList());
        assertEquals(List.of(memberUuid), queries.getTeamMembers(islandId).join().stream().map(member -> member.playerUuid()).toList());
        assertTrue(queries.isCoop(islandId, coopUuid).join());
        assertFalse(queries.isCoop(islandId, memberUuid).join());
        assertTrue(queries.isMember(islandId, memberUuid).join());
        assertFalse(queries.isMember(islandId, coopUuid).join());
        assertEquals(8L, queries.getCoopLimit(islandId).join());
    }

    @Test
    void exposesConfiguredCoopLimitAndLifecycleAliases() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000154");
        IslandQueryService queries = queries(List.of(), List.of(new IslandLimitSnapshot(islandId, "ROLE_LIMIT:TRUSTED", 12L, UUID.fromString("00000000-0000-0000-0000-000000000155"), Instant.EPOCH)));

        assertEquals(12L, queries.getCoopLimit(islandId).join());
        assertTrue(IslandCommandService.class.getMethod("addCoop", UUID.class, UUID.class, UUID.class).isDefault());
        assertTrue(IslandCommandService.class.getMethod("removeCoop", UUID.class, UUID.class, UUID.class).isDefault());
        assertTrue(IslandCommandService.class.getMethod("addCoopResult", UUID.class, UUID.class, UUID.class).isDefault());
        assertTrue(IslandCommandService.class.getMethod("removeCoopResult", UUID.class, UUID.class, UUID.class).isDefault());
    }

    private static IslandQueryService queries(List<IslandMemberSnapshot> members, List<IslandLimitSnapshot> limits) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            return switch (method.getName()) {
                case "getMembers" -> CompletableFuture.completedFuture(members);
                case "getLimits" -> CompletableFuture.completedFuture(limits);
                default -> throw new UnsupportedOperationException(method.getName());
            };
        };
        return (IslandQueryService) Proxy.newProxyInstance(IslandQueryService.class.getClassLoader(), new Class<?>[] {IslandQueryService.class}, handler);
    }
}

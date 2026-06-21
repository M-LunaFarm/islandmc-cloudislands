package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class IslandNavigationUseCaseTest {
    @Test
    void resolvesVisitTargetThroughPlayerPrimaryIslandAndOwnerTicket() {
        List<String> calls = new ArrayList<>();
        IslandNavigationUseCase useCase = new IslandNavigationUseCase(client(calls));
        UUID visitorUuid = uuid("00000000-0000-0000-0000-000000000001");

        RouteTicket ticket = useCase.resolveVisitTarget(visitorUuid, "Steve", mutationRunner(calls)).join();

        assertEquals(visitorUuid, ticket.playerUuid());
        assertEquals(List.of(
            "playerInfoByName:Steve",
            "audit:route.ticket.visit.owner",
            "createVisitTicketForOwner:00000000-0000-0000-0000-000000000030"
        ), calls);
    }

    @Test
    void fallsBackToIslandNameTicketWhenPlayerLookupFails() {
        List<String> calls = new ArrayList<>();
        IslandNavigationUseCase useCase = new IslandNavigationUseCase(client(calls));
        UUID visitorUuid = uuid("00000000-0000-0000-0000-000000000001");

        useCase.resolveVisitTarget(visitorUuid, "spawn", mutationRunner(calls)).join();

        assertEquals(List.of(
            "playerInfoByName:spawn",
            "audit:route.ticket.visit.name",
            "createVisitTicket:name:spawn"
        ), calls);
    }

    @Test
    void listAndReviewOperationsUseBoundedApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandNavigationUseCase useCase = new IslandNavigationUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");
        UUID reviewerUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals(publicIslandsJson(islandId), useCase.listPublicIslands(500).join());
        assertEquals("spawn", useCase.publicIslandViews(500).join().getFirst().name());
        assertEquals(reviewsJson(islandId, reviewerUuid), useCase.listReviews(islandId, 0).join());
        assertEquals(1L, useCase.reviewViews(islandId, 0).join().count());
        assertEquals("{\"accepted\":true}", useCase.setReview(islandId, reviewerUuid, 5, "nice", idempotentRunner(calls)).join());
        assertEquals(true, useCase.setReviewAction(islandId, reviewerUuid, 5, "nice", idempotentRunner(calls)).join().accepted());

        assertEquals(List.of(
            "listPublicIslands:100",
            "listPublicIslands:100",
            "listIslandReviews:1",
            "listIslandReviews:1",
            "audit-idempotent:island.review.set",
            "setIslandReview:5:nice",
            "audit-idempotent:island.review.set",
            "setIslandReview:5:nice"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "playerInfoByName" -> {
                    String target = (String) args[0];
                    calls.add("playerInfoByName:" + target);
                    if ("spawn".equals(target)) {
                        yield CompletableFuture.failedFuture(new IllegalStateException("missing player"));
                    }
                    yield CompletableFuture.completedFuture("{\"playerUuid\":\"00000000-0000-0000-0000-000000000030\",\"primaryIslandId\":\"00000000-0000-0000-0000-000000000020\"}");
                }
                case "createVisitTicketForOwner" -> {
                    calls.add("createVisitTicketForOwner:" + args[1]);
                    yield CompletableFuture.completedFuture(ticket((UUID) args[0]));
                }
                case "createVisitTicket" -> {
                    if (args[1] instanceof UUID islandId) {
                        calls.add("createVisitTicket:id:" + islandId);
                    } else {
                        calls.add("createVisitTicket:name:" + args[1]);
                    }
                    yield CompletableFuture.completedFuture(ticket((UUID) args[0]));
                }
                case "createRandomVisitTicket" -> {
                    calls.add("createRandomVisitTicket");
                    yield CompletableFuture.completedFuture(ticket((UUID) args[0]));
                }
                case "listPublicIslands" -> {
                    calls.add("listPublicIslands:" + args[0]);
                    yield CompletableFuture.completedFuture(publicIslandsJson(UUID.fromString("00000000-0000-0000-0000-000000000020")));
                }
                case "listIslandReviews" -> {
                    calls.add("listIslandReviews:" + args[1]);
                    yield CompletableFuture.completedFuture(reviewsJson((UUID) args[0], UUID.fromString("00000000-0000-0000-0000-000000000001")));
                }
                case "setIslandReview" -> {
                    calls.add("setIslandReview:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandNavigationUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandNavigationUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static IslandNavigationUseCase.IdempotentMutationRunner idempotentRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit-idempotent:" + auditAction);
            return operation.get();
        };
    }

    private static RouteTicket ticket(UUID playerUuid) {
        return new RouteTicket(UUID.randomUUID(), playerUuid, RouteAction.VISIT, UUID.randomUUID(), "node-1", "world", RouteTicketState.READY, java.time.Instant.EPOCH.plusSeconds(60), "nonce", java.util.Map.of());
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static String publicIslandsJson(UUID islandId) {
        return "{\"islands\":[{\"islandId\":\"" + islandId + "\",\"ownerUuid\":\"00000000-0000-0000-0000-000000000030\",\"name\":\"spawn\",\"level\":7,\"worth\":\"1200\"}]}";
    }

    private static String reviewsJson(UUID islandId, UUID reviewerUuid) {
        return "{\"reviews\":[{\"islandId\":\"" + islandId + "\",\"reviewerUuid\":\"" + reviewerUuid + "\",\"rating\":5,\"comment\":\"nice\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T04:05:06Z\"}],\"summary\":{\"count\":1,\"average\":5.00}}";
    }
}

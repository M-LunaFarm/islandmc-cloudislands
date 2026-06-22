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
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreNavigationCommandClient;
import kr.lunaf.cloudislands.coreclient.NavigationQueryClient;
import kr.lunaf.cloudislands.coreclient.ReviewListView;
import kr.lunaf.cloudislands.coreclient.ReviewView;
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

        assertEquals("spawn", useCase.publicIslandViews(500).join().getFirst().name());
        assertEquals(1L, useCase.reviewViews(islandId, 0).join().count());
        assertEquals(true, useCase.setReviewAction(islandId, reviewerUuid, 5, "nice", idempotentRunner(calls)).join().accepted());

        assertEquals(List.of(
            "listPublicIslands:100",
            "listIslandReviews:1",
            "audit-idempotent:island.review.set",
            "setIslandReview:5:nice"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, NavigationQueryClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "navigation" -> (NavigationQueryClient) _proxy;
                case "navigationCommands" -> new CoreNavigationCommandClient((CoreApiClient) _proxy);
                case "playerProfileByName" -> {
                    String target = (String) args[0];
                    calls.add("playerInfoByName:" + target);
                    if ("spawn".equals(target)) {
                        yield CompletableFuture.failedFuture(new IllegalStateException("missing player"));
                    }
                    yield CompletableFuture.completedFuture(new CoreGuiViews.PlayerProfileView("00000000-0000-0000-0000-000000000030", "00000000-0000-0000-0000-000000000020"));
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
                case "publicIslands" -> {
                    int limit = Math.max(1, Math.min((int) args[0], 100));
                    calls.add("listPublicIslands:" + limit);
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.PublicIslandView("00000000-0000-0000-0000-000000000020", "00000000-0000-0000-0000-000000000030", "spawn", 7L, "1200")));
                }
                case "listReviews" -> {
                    int limit = Math.max(1, Math.min((int) args[1], 100));
                    calls.add("listIslandReviews:" + limit);
                    UUID islandId = (UUID) args[0];
                    UUID reviewerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
                    yield CompletableFuture.completedFuture(new ReviewListView(1L, 5.0D, List.of(new ReviewView(islandId.toString(), reviewerUuid.toString(), 5L, "nice", "2026-01-02T03:04:05Z", "2026-01-03T04:05:06Z"))));
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
        return new IslandNavigationUseCase.IdempotentMutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit-idempotent:" + auditAction);
                return operation.get();
            }
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

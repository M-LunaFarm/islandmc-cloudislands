package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.IslandQueryClient;
import kr.lunaf.cloudislands.coreclient.NavigationQueryClient;
import org.junit.jupiter.api.Test;

class IslandTargetResolverTest {
    private static final UUID NAMED_ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PLAYER_ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void resolvesUuidThenIslandNameThenPlayerPrimaryIsland() {
        IslandQueryClient islands = proxy(IslandQueryClient.class, (method, args) -> {
            if (method.equals("findIslandByName") && "NamedIsland".equals(args[0])) {
                return CompletableFuture.completedFuture(new CoreGuiViews.IslandInfoView("NamedIsland", "ACTIVE", NAMED_ISLAND.toString(), 1, "1", true, false, 100, 50, "owner"));
            }
            if (method.equals("findIslandByName")) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("not found"));
            }
            throw new UnsupportedOperationException(method);
        });
        NavigationQueryClient navigation = proxy(NavigationQueryClient.class, (method, args) -> {
            if (method.equals("playerProfileByName") && "Player".equals(args[0])) {
                return CompletableFuture.completedFuture(new CoreGuiViews.PlayerProfileView("player", PLAYER_ISLAND.toString()));
            }
            throw new UnsupportedOperationException(method);
        });
        CoreApiClient client = proxy(CoreApiClient.class, (method, _args) -> switch (method) {
            case "islands" -> islands;
            case "navigation" -> navigation;
            default -> throw new UnsupportedOperationException(method);
        });
        IslandTargetResolver resolver = new IslandTargetResolver(client);

        assertEquals(NAMED_ISLAND, resolver.resolve(NAMED_ISLAND.toString()).join());
        assertEquals(NAMED_ISLAND, resolver.resolve("NamedIsland").join());
        assertEquals(PLAYER_ISLAND, resolver.resolve("Player").join());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (_proxy, method, args) -> invocation.call(method.getName(), args == null ? new Object[0] : args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] args);
    }
}

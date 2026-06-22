package kr.lunaf.cloudislands.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import org.junit.jupiter.api.Test;

class IslandCommandServiceWarpCategoryTest {
    @Test
    void warpCategoryMutationIsTheCanonicalCommandContract() throws Exception {
        var legacySignature = IslandCommandService.class.getMethod("setWarpResult", UUID.class, UUID.class, String.class, IslandLocation.class, boolean.class);
        var categorySignature = IslandCommandService.class.getMethod("setWarpResult", UUID.class, UUID.class, String.class, IslandLocation.class, boolean.class, String.class);

        assertTrue(legacySignature.isDefault(), "legacy warp mutation must adapt into the category-aware contract");
        assertFalse(categorySignature.isDefault(), "category-aware warp mutation must be implemented explicitly");
        assertTrue(Modifier.isAbstract(categorySignature.getModifiers()), "category-aware warp mutation must not silently discard category");
    }

    @Test
    void legacyWarpMutationDelegatesWithDefaultCategory() {
        List<String> calls = new ArrayList<>();
        IslandCommandService service = service(calls);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        IslandLocation location = new IslandLocation("world", 1.0d, 2.0d, 3.0d, 0.0f, 0.0f);

        assertEquals("WARP_SET", service.setWarpResult(islandId, actorUuid, "spawn", location, true).join().code());
        assertEquals("WARP_SET", service.setWarpResult(islandId, actorUuid, "market", location, true, "market").join().code());

        assertEquals(List.of(
            "setWarp:spawn:true:",
            "setWarp:market:true:market"
        ), calls);
    }

    private static IslandCommandService service(List<String> calls) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getName().equals("setWarpResult")) {
                calls.add("setWarp:" + args[2] + ":" + args[4] + ":" + args[5]);
                return CompletableFuture.completedFuture(new IslandActionResult(true, "WARP_SET"));
            }
            if (method.getName().equals("toString")) {
                return "warp-category-command-service";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (IslandCommandService) Proxy.newProxyInstance(
            IslandCommandService.class.getClassLoader(),
            new Class<?>[] {IslandCommandService.class},
            handler
        );
    }
}

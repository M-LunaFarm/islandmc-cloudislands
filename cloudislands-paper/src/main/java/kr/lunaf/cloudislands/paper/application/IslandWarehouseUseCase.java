package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandWarehouseUseCase {
    private final CoreApiClient coreApiClient;

    public IslandWarehouseUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> list(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.islandWarehouse(islandId, Math.max(1, Math.min(limit, 100)));
    }

    public CompletableFuture<WarehouseOperationResult> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireMaterial(materialKey);
        requireAmount(amount);
        requireRunner(runner);
        return runner.mutateIdempotent("island.warehouse.deposit", () -> coreApiClient.depositIslandWarehouse(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromBody);
    }

    public CompletableFuture<WarehouseOperationResult> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireMaterial(materialKey);
        requireAmount(amount);
        requireRunner(runner);
        return runner.mutateIdempotent("island.warehouse.withdraw", () -> coreApiClient.withdrawIslandWarehouse(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromBody);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireMaterial(String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
    }

    private static void requireAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("positive amount is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static String text(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json == null ? -1 : json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return "";
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return "";
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return "";
        }
        return json.substring(start + 1, end);
    }

    private static double decimal(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json == null ? -1 : json.indexOf(marker);
        if (keyIndex < 0) {
            return 0.0d;
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return 0.0d;
        }
        int end = colon + 1;
        while (end < json.length() && " \t\r\n".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        int start = end;
        while (end < json.length()) {
            char current = json.charAt(end);
            if (!(Character.isDigit(current) || current == '-' || current == '.')) {
                break;
            }
            end++;
        }
        if (start == end) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0.0d;
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record WarehouseOperationResult(boolean accepted, String code, String materialKey, long amount) {
        private static WarehouseOperationResult fromBody(String body) {
            boolean accepted = body == null || !body.contains("\"accepted\":false");
            return new WarehouseOperationResult(accepted, text(body, "code"), text(body, "materialKey"), (long) decimal(body, "amount"));
        }
    }
}

package kr.lunaf.cloudislands.paper.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class BankUseCase {
    private final CoreApiClient coreApiClient;
    private final EconomyBridge economyBridge;

    public BankUseCase(CoreApiClient coreApiClient, EconomyBridge economyBridge) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.economyBridge = economyBridge;
    }

    public CompletableFuture<BankOperationResult> bank(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandBank(coreApiClient, islandId)
            .thenApply(view -> BankOperationResult.success("", normalizedBalance(view.balance())));
    }

    public CompletableFuture<BankOperationResult> deposit(UUID islandId, UUID actorUuid, BigDecimal amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireAmount(amount);
        requireRunner(runner);
        if (economyBridge == null) {
            return CompletableFuture.completedFuture(BankOperationResult.economyUnavailable());
        }
        String normalizedAmount = amount.toPlainString();
        return economyBridge.withdraw(actorUuid, amount, "CloudIslands island bank deposit")
            .thenCompose(withdrawn -> {
                if (!withdrawn) {
                    return CompletableFuture.completedFuture(BankOperationResult.economyWithdrawDenied());
                }
                return runner.mutateIdempotent("island.bank.deposit", () -> coreApiClient.depositIslandBank(islandId, actorUuid, normalizedAmount))
                    .thenCompose(body -> {
                        if (rejected(body)) {
                            return refundPlayer(actorUuid, amount)
                                .thenApply(_ignored -> BankOperationResult.coreRejected(body, text(root(body), "code")));
                        }
                        return CompletableFuture.completedFuture(BankOperationResult.success(body, bankBalance(body)));
                    })
                    .exceptionallyCompose(error -> refundPlayer(actorUuid, amount).thenCompose(_ignored -> CompletableFuture.failedFuture(error)));
            });
    }

    public CompletableFuture<BankOperationResult> withdraw(UUID islandId, UUID actorUuid, BigDecimal amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireAmount(amount);
        requireRunner(runner);
        if (economyBridge == null) {
            return CompletableFuture.completedFuture(BankOperationResult.economyUnavailable());
        }
        String normalizedAmount = amount.toPlainString();
        return runner.mutateIdempotent("island.bank.withdraw", () -> coreApiClient.withdrawIslandBank(islandId, actorUuid, normalizedAmount))
            .thenCompose(body -> {
                if (rejected(body)) {
                    return CompletableFuture.completedFuture(BankOperationResult.coreRejected(body, text(root(body), "code")));
                }
                String balance = bankBalance(body);
                return economyBridge.deposit(actorUuid, amount, "CloudIslands island bank withdraw")
                    .thenApply(_ignored -> BankOperationResult.success(body, balance))
                    .exceptionallyCompose(error -> runner.mutateIdempotent("island.bank.withdraw.rollback", () -> coreApiClient.depositIslandBank(islandId, actorUuid, normalizedAmount))
                        .thenApply(_ignored -> BankOperationResult.rolledBackAfterEconomyDepositFailure(body, balance))
                        .exceptionally(_rollbackError -> BankOperationResult.rollbackFailedAfterEconomyDepositFailure(body, balance)));
            });
    }

    public static BigDecimal positiveAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(amount.trim());
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private CompletableFuture<Void> refundPlayer(UUID actorUuid, BigDecimal amount) {
        return economyBridge.deposit(actorUuid, amount, "CloudIslands island bank deposit rollback")
            .exceptionally(error -> null);
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

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("positive amount is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static boolean rejected(String body) {
        Map<?, ?> root = root(body);
        return root.containsKey("error")
            || Boolean.FALSE.equals(root.get("accepted"))
            || Boolean.FALSE.equals(root.get("applied"));
    }

    private static String bankBalance(String body) {
        return normalizedBalance(text(root(body), "balance"));
    }

    private static String normalizedBalance(String balance) {
        return balance == null || balance.isBlank() ? "0" : balance;
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static Map<?, ?> root(String body) {
        return SimpleJson.object(SimpleJson.parse(body));
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public enum Status {
        SUCCESS,
        ECONOMY_UNAVAILABLE,
        ECONOMY_WITHDRAW_DENIED,
        CORE_REJECTED,
        ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE,
        ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE
    }

    public record BankOperationResult(Status status, String body, String balance, String code) {
        private static BankOperationResult success(String body, String balance) {
            return new BankOperationResult(Status.SUCCESS, body, balance, "");
        }

        private static BankOperationResult economyUnavailable() {
            return new BankOperationResult(Status.ECONOMY_UNAVAILABLE, "", "", "");
        }

        private static BankOperationResult economyWithdrawDenied() {
            return new BankOperationResult(Status.ECONOMY_WITHDRAW_DENIED, "", "", "INSUFFICIENT_FUNDS");
        }

        private static BankOperationResult coreRejected(String body, String code) {
            return new BankOperationResult(Status.CORE_REJECTED, body, bankBalance(body), code == null ? "" : code);
        }

        private static BankOperationResult rolledBackAfterEconomyDepositFailure(String body, String balance) {
            return new BankOperationResult(Status.ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, body, balance, "");
        }

        private static BankOperationResult rollbackFailedAfterEconomyDepositFailure(String body, String balance) {
            return new BankOperationResult(Status.ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE, body, balance, "");
        }
    }
}

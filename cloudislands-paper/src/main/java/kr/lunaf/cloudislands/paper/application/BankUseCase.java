package kr.lunaf.cloudislands.paper.application;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.coreclient.BankCommandClient;
import kr.lunaf.cloudislands.coreclient.BankMutationView;
import kr.lunaf.cloudislands.coreclient.BankQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class BankUseCase {
    private final CoreApiClient coreApiClient;
    private final BankQueryClient bankQueries;
    private final BankCommandClient bankCommands;
    private final EconomyBridge economyBridge;

    public BankUseCase(CoreApiClient coreApiClient, EconomyBridge economyBridge) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.bankQueries = coreApiClient.bank();
        this.bankCommands = coreApiClient.bankCommands();
        this.economyBridge = economyBridge;
    }

    BankUseCase(CoreApiClient coreApiClient, BankQueryClient bankQueries, EconomyBridge economyBridge) {
        this(coreApiClient, bankQueries, coreApiClient.bankCommands(), economyBridge);
    }

    BankUseCase(CoreApiClient coreApiClient, BankQueryClient bankQueries, BankCommandClient bankCommands, EconomyBridge economyBridge) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (bankQueries == null) {
            throw new IllegalArgumentException("bankQueries is required");
        }
        if (bankCommands == null) {
            throw new IllegalArgumentException("bankCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.bankQueries = bankQueries;
        this.bankCommands = bankCommands;
        this.economyBridge = economyBridge;
    }

    public CompletableFuture<BankOperationResult> bank(UUID islandId) {
        requireIsland(islandId);
        return bankQueries.islandBank(islandId)
            .thenApply(view -> BankOperationResult.success(normalizedBalance(view.balance())));
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
                return runner.mutateIdempotent("island.bank.deposit", () -> bankCommands.deposit(islandId, actorUuid, normalizedAmount))
                    .thenCompose(mutation -> {
                        if (!mutation.accepted()) {
                            return refundPlayer(actorUuid, amount)
                                .thenApply(_ignored -> BankOperationResult.coreRejected(mutation.balance(), mutation.code()));
                        }
                        return CompletableFuture.completedFuture(BankOperationResult.success(mutation.balance()));
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
        return runner.mutateIdempotent("island.bank.withdraw", () -> bankCommands.withdraw(islandId, actorUuid, normalizedAmount))
            .thenCompose(mutation -> {
                if (!mutation.accepted()) {
                    return CompletableFuture.completedFuture(BankOperationResult.coreRejected(mutation.balance(), mutation.code()));
                }
                String balance = mutation.balance();
                return economyBridge.deposit(actorUuid, amount, "CloudIslands island bank withdraw")
                    .thenApply(_ignored -> BankOperationResult.success(balance))
                    .exceptionallyCompose(error -> runner.mutateIdempotent("island.bank.withdraw.rollback", () -> bankCommands.deposit(islandId, actorUuid, normalizedAmount))
                        .thenApply(_ignored -> BankOperationResult.rolledBackAfterEconomyDepositFailure(balance))
                        .exceptionally(_rollbackError -> BankOperationResult.rollbackFailedAfterEconomyDepositFailure(balance)));
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

    private static String normalizedBalance(String balance) {
        return balance == null || balance.isBlank() ? "0" : balance;
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<BankMutationView> mutateIdempotent(String auditAction, Supplier<CompletableFuture<BankMutationView>> operation);
    }

    public enum Status {
        SUCCESS,
        ECONOMY_UNAVAILABLE,
        ECONOMY_WITHDRAW_DENIED,
        CORE_REJECTED,
        ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE,
        ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE
    }

    public record BankOperationResult(Status status, String balance, String code) {
        private static BankOperationResult success(String balance) {
            return new BankOperationResult(Status.SUCCESS, balance, "");
        }

        private static BankOperationResult economyUnavailable() {
            return new BankOperationResult(Status.ECONOMY_UNAVAILABLE, "", "");
        }

        private static BankOperationResult economyWithdrawDenied() {
            return new BankOperationResult(Status.ECONOMY_WITHDRAW_DENIED, "", "INSUFFICIENT_FUNDS");
        }

        private static BankOperationResult coreRejected(String balance, String code) {
            return new BankOperationResult(Status.CORE_REJECTED, balance, code == null ? "" : code);
        }

        private static BankOperationResult rolledBackAfterEconomyDepositFailure(String balance) {
            return new BankOperationResult(Status.ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, balance, "");
        }

        private static BankOperationResult rollbackFailedAfterEconomyDepositFailure(String balance) {
            return new BankOperationResult(Status.ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE, balance, "");
        }
    }
}

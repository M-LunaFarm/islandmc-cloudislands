package kr.lunaf.cloudislands.paper.application;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.economy.EconomyProviderState;
import kr.lunaf.cloudislands.coreclient.BankCommandClient;
import kr.lunaf.cloudislands.coreclient.BankMutationView;
import kr.lunaf.cloudislands.coreclient.BankQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.ProgressionCommandClient;
import kr.lunaf.cloudislands.paper.mission.MissionProgressTriggers;

public final class BankUseCase {
    private final CoreApiClient coreApiClient;
    private final BankQueryClient bankQueries;
    private final BankCommandClient bankCommands;
    private final EconomyBridge economyBridge;
    private final ProgressionCommandClient progressionCommands;

    public BankUseCase(CoreApiClient coreApiClient, EconomyBridge economyBridge) {
        this(coreApiClient, economyBridge, progressionCommandsOrNull(coreApiClient));
    }

    public BankUseCase(CoreApiClient coreApiClient, EconomyBridge economyBridge, ProgressionCommandClient progressionCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.bankQueries = coreApiClient.bank();
        this.bankCommands = coreApiClient.bankCommands();
        this.economyBridge = economyBridge;
        this.progressionCommands = progressionCommands;
    }

    BankUseCase(CoreApiClient coreApiClient, BankQueryClient bankQueries, EconomyBridge economyBridge) {
        this(coreApiClient, bankQueries, coreApiClient.bankCommands(), economyBridge, null);
    }

    BankUseCase(CoreApiClient coreApiClient, BankQueryClient bankQueries, BankCommandClient bankCommands, EconomyBridge economyBridge) {
        this(coreApiClient, bankQueries, bankCommands, economyBridge, null);
    }

    BankUseCase(CoreApiClient coreApiClient, BankQueryClient bankQueries, BankCommandClient bankCommands, EconomyBridge economyBridge, ProgressionCommandClient progressionCommands) {
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
        this.progressionCommands = progressionCommands;
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
        BankOperationResult unavailable = unavailableEconomyResult();
        if (unavailable != null) {
            return CompletableFuture.completedFuture(unavailable);
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
                                .thenApply(_ignored -> BankOperationResult.coreRejected(mutation.balance(), mutation.code()))
                                .exceptionally(_refundError -> BankOperationResult.refundFailedAfterCoreRejection(mutation.balance(), mutation.code()));
                        }
                        recordBankBalanceProgress(islandId, actorUuid, mutation.balance());
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
        BankOperationResult unavailable = unavailableEconomyResult();
        if (unavailable != null) {
            return CompletableFuture.completedFuture(unavailable);
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
        return economyBridge.deposit(actorUuid, amount, "CloudIslands island bank deposit rollback");
    }

    private BankOperationResult unavailableEconomyResult() {
        if (economyBridge == null) {
            return BankOperationResult.economyUnavailable();
        }
        EconomyProviderState state = economyBridge.providerState();
        if (state == null) {
            return BankOperationResult.economyUnavailable();
        }
        return switch (state) {
            case NOT_INSTALLED, DETECTED -> BankOperationResult.economyUnavailable();
            case OPERATION_FAILED -> BankOperationResult.economyOperationFailed();
            case API_COMPATIBLE, ACTIVE -> null;
        };
    }

    private static ProgressionCommandClient progressionCommandsOrNull(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            return null;
        }
        try {
            ProgressionCommandClient commands = coreApiClient.progressionCommands();
            return commands;
        } catch (UnsupportedOperationException exception) {
            return null;
        }
    }

    private void recordBankBalanceProgress(UUID islandId, UUID actorUuid, String balance) {
        if (progressionCommands == null) {
            return;
        }
        long amount = balanceAmount(balance);
        for (MissionProgressTriggers.Trigger trigger : MissionProgressTriggers.bankBalance(amount)) {
            progressionCommands.progressMission(islandId, actorUuid, trigger.missionKey(), trigger.kind(), trigger.amount())
                .exceptionally(_error -> null);
        }
    }

    private static long balanceAmount(String balance) {
        if (balance == null || balance.isBlank()) {
            return 1L;
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(balance.trim());
            if (value.signum() <= 0) {
                return 1L;
            }
            return Math.max(1L, value.min(new java.math.BigDecimal(Long.MAX_VALUE)).longValue());
        } catch (NumberFormatException exception) {
            return 1L;
        }
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
        ECONOMY_OPERATION_FAILED,
        ECONOMY_WITHDRAW_DENIED,
        CORE_REJECTED,
        REFUND_FAILED_AFTER_CORE_REJECTION,
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

        private static BankOperationResult economyOperationFailed() {
            return new BankOperationResult(Status.ECONOMY_OPERATION_FAILED, "", "ECONOMY_OPERATION_FAILED");
        }

        private static BankOperationResult economyWithdrawDenied() {
            return new BankOperationResult(Status.ECONOMY_WITHDRAW_DENIED, "", "INSUFFICIENT_FUNDS");
        }

        private static BankOperationResult coreRejected(String balance, String code) {
            return new BankOperationResult(Status.CORE_REJECTED, balance, code == null ? "" : code);
        }

        private static BankOperationResult refundFailedAfterCoreRejection(String balance, String code) {
            return new BankOperationResult(Status.REFUND_FAILED_AFTER_CORE_REJECTION, balance, code == null ? "" : code);
        }

        private static BankOperationResult rolledBackAfterEconomyDepositFailure(String balance) {
            return new BankOperationResult(Status.ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, balance, "");
        }

        private static BankOperationResult rollbackFailedAfterEconomyDepositFailure(String balance) {
            return new BankOperationResult(Status.ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE, balance, "");
        }
    }
}

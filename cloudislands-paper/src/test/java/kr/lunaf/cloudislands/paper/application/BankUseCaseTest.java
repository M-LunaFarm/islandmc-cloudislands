package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.coreclient.BankCommandClient;
import kr.lunaf.cloudislands.coreclient.BankQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class BankUseCaseTest {
    @Test
    void depositCoreRejectionRefundsPlayerAndReturnsCoreCode() {
        FakeEconomy economy = new FakeEconomy(true);
        CoreApiClient client = coreApiClient(new ScriptedCoreBank());
        BankUseCase useCase = new BankUseCase(client, economy);
        List<String> auditActions = new ArrayList<>();

        BankUseCase.BankOperationResult result = useCase.deposit(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("12.50"),
            (auditAction, operation) -> {
                auditActions.add(auditAction);
                return operation.get();
            }
        ).join();

        assertEquals(BankUseCase.Status.CORE_REJECTED, result.status());
        assertEquals("BANK_LIMIT_REACHED", result.code());
        assertEquals(List.of("withdraw:12.50:CloudIslands island bank deposit", "deposit:12.50:CloudIslands island bank deposit rollback"), economy.calls);
        assertEquals(List.of("island.bank.deposit"), auditActions);
    }

    @Test
    void depositCoreRejectionReportsRefundFailure() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.failDepositRollback = true;
        CoreApiClient client = coreApiClient(new ScriptedCoreBank());
        BankUseCase useCase = new BankUseCase(client, economy);

        BankUseCase.BankOperationResult result = useCase.deposit(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("12.50"),
            (_auditAction, operation) -> operation.get()
        ).join();

        assertEquals(BankUseCase.Status.REFUND_FAILED_AFTER_CORE_REJECTION, result.status());
        assertEquals("BANK_LIMIT_REACHED", result.code());
        assertEquals(List.of("withdraw:12.50:CloudIslands island bank deposit", "deposit:12.50:CloudIslands island bank deposit rollback"), economy.calls);
    }

    @Test
    void depositCoreFailureRefundsPlayerAndPropagatesFailure() {
        FakeEconomy economy = new FakeEconomy(true);
        ScriptedCoreBank core = new ScriptedCoreBank();
        core.depositFailure = new IllegalStateException("core unavailable");
        BankUseCase useCase = new BankUseCase(coreApiClient(core), economy);

        assertThrows(CompletionException.class, () -> useCase.deposit(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.ONE,
            (_auditAction, operation) -> operation.get()
        ).join());
        assertEquals(List.of("withdraw:1:CloudIslands island bank deposit", "deposit:1:CloudIslands island bank deposit rollback"), economy.calls);
    }

    @Test
    void withdrawEconomyDepositFailureRollsBackCoreBankMutation() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.failWithdrawPayout = true;
        ScriptedCoreBank core = new ScriptedCoreBank();
        core.withdrawResult = ScriptedCoreBank.bankChange(true, "", "40");
        BankUseCase useCase = new BankUseCase(coreApiClient(core), economy);
        List<String> auditActions = new ArrayList<>();

        BankUseCase.BankOperationResult result = useCase.withdraw(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("9"),
            (auditAction, operation) -> {
                auditActions.add(auditAction);
                return operation.get();
            }
        ).join();

        assertEquals(BankUseCase.Status.ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, result.status());
        assertEquals("40", result.balance());
        assertEquals(List.of("deposit:9:CloudIslands island bank withdraw"), economy.calls);
        assertEquals(List.of("island.bank.withdraw", "island.bank.withdraw.rollback"), auditActions);
        assertEquals(List.of("withdraw:9", "deposit:9"), core.calls);
    }

    @Test
    void withdrawRollbackFailureReturnsEscalatedStatus() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.failWithdrawPayout = true;
        ScriptedCoreBank core = new ScriptedCoreBank();
        core.rollbackDepositFailure = new IllegalStateException("rollback failed");
        BankUseCase useCase = new BankUseCase(coreApiClient(core), economy);

        BankUseCase.BankOperationResult result = useCase.withdraw(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("4"),
            (_auditAction, operation) -> operation.get()
        ).join();

        assertEquals(BankUseCase.Status.ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE, result.status());
    }

    @Test
    void rejectsMissingEconomyAndInvalidAmountsBeforeMutation() {
        BankUseCase useCase = new BankUseCase(coreApiClient(new ScriptedCoreBank()), null);

        BankUseCase.BankOperationResult result = useCase.deposit(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.ONE,
            (_auditAction, operation) -> operation.get()
        ).join();

        assertEquals(BankUseCase.Status.ECONOMY_UNAVAILABLE, result.status());
        assertEquals(new BigDecimal("7.25"), BankUseCase.positiveAmount(" 7.25 "));
        assertNull(BankUseCase.positiveAmount("0"));
        assertNull(BankUseCase.positiveAmount("abc"));
    }

    @Test
    void bankBalanceUsesTypedCoreView() {
        BankUseCase useCase = new BankUseCase(coreApiClient(new ScriptedCoreBank()), null);

        BankUseCase.BankOperationResult result = useCase.bank(UUID.randomUUID()).join();

        assertEquals(BankUseCase.Status.SUCCESS, result.status());
        assertEquals("55", result.balance());
    }

    @Test
    void bankOperationResultDoesNotExposeRawCoreBody() {
        for (RecordComponent component : BankUseCase.BankOperationResult.class.getRecordComponents()) {
            assertFalse(component.getName().equals("body"));
        }
    }

    private CoreApiClient coreApiClient(ScriptedCoreBank core) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, BankQueryClient.class, BankCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "bank" -> (BankQueryClient) _proxy;
                case "bankCommands" -> (BankCommandClient) _proxy;
                case "snapshot" -> CompletableFuture.completedFuture(new IslandBankSnapshot((UUID) args[0], "55", Instant.EPOCH));
                case "islandBank" -> CompletableFuture.completedFuture(new kr.lunaf.cloudislands.coreclient.CoreGuiViews.BankView("55", Instant.EPOCH.toString()));
                case "depositSnapshot" -> core.deposit((UUID) args[0], (String) args[2]);
                case "withdrawSnapshot" -> core.withdraw((UUID) args[0], (String) args[2]);
                case "deposit" -> core.deposit((UUID) args[0], (String) args[2]).thenApply(change -> new kr.lunaf.cloudislands.coreclient.BankMutationView(
                    change.accepted(),
                    change.code(),
                    change.bank().islandId().toString(),
                    change.bank().balance(),
                    change.bank().updatedAt().toString()
                ));
                case "withdraw" -> core.withdraw((UUID) args[0], (String) args[2]).thenApply(change -> new kr.lunaf.cloudislands.coreclient.BankMutationView(
                    change.accepted(),
                    change.code(),
                    change.bank().islandId().toString(),
                    change.bank().balance(),
                    change.bank().updatedAt().toString()
                ));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private static final class ScriptedCoreBank {
        final List<String> calls = new ArrayList<>();
        IslandBankChangeSnapshot depositResult = bankChange(false, "BANK_LIMIT_REACHED", "10");
        IslandBankChangeSnapshot withdrawResult = bankChange(true, "", "90");
        RuntimeException depositFailure;
        RuntimeException rollbackDepositFailure;
        int deposits;

        CompletableFuture<IslandBankChangeSnapshot> deposit(UUID islandId, String amount) {
            calls.add("deposit:" + amount);
            deposits++;
            if (rollbackDepositFailure != null) {
                return CompletableFuture.failedFuture(rollbackDepositFailure);
            }
            if (depositFailure != null) {
                return CompletableFuture.failedFuture(depositFailure);
            }
            if (deposits > 1) {
                return CompletableFuture.completedFuture(bankChange(islandId, true, "", "99"));
            }
            return CompletableFuture.completedFuture(bankChange(islandId, depositResult.accepted(), depositResult.code(), depositResult.bank().balance()));
        }

        CompletableFuture<IslandBankChangeSnapshot> withdraw(UUID islandId, String amount) {
            calls.add("withdraw:" + amount);
            return CompletableFuture.completedFuture(bankChange(islandId, withdrawResult.accepted(), withdrawResult.code(), withdrawResult.bank().balance()));
        }

        private static IslandBankChangeSnapshot bankChange(boolean accepted, String code, String balance) {
            return bankChange(new UUID(0L, 0L), accepted, code, balance);
        }

        private static IslandBankChangeSnapshot bankChange(UUID islandId, boolean accepted, String code, String balance) {
            return new IslandBankChangeSnapshot(accepted, code, new IslandBankSnapshot(islandId, balance, Instant.EPOCH));
        }
    }

    private static final class FakeEconomy implements EconomyBridge {
        final List<String> calls = new ArrayList<>();
        final boolean withdrawResult;
        boolean failWithdrawPayout;
        boolean failDepositRollback;

        FakeEconomy(boolean withdrawResult) {
            this.withdrawResult = withdrawResult;
        }

        @Override
        public CompletableFuture<Boolean> withdraw(UUID playerUuid, BigDecimal amount, String reason) {
            calls.add("withdraw:" + amount.toPlainString() + ":" + reason);
            return CompletableFuture.completedFuture(withdrawResult);
        }

        @Override
        public CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason) {
            calls.add("deposit:" + amount.toPlainString() + ":" + reason);
            if (failDepositRollback && reason.equals("CloudIslands island bank deposit rollback")) {
                return CompletableFuture.failedFuture(new IllegalStateException("refund failed"));
            }
            if (failWithdrawPayout && reason.equals("CloudIslands island bank withdraw")) {
                return CompletableFuture.failedFuture(new IllegalStateException("payout failed"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<BigDecimal> balance(UUID playerUuid) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }
    }
}

package kr.lunaf.cloudislands.paper.command;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.application.BankUseCase;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLogMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandBankCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final BankUseCase bankUseCase;
    private final IslandTargetResolver targetResolver;
    private final Runtime runtime;
    private final PendingBankOperations pendingOperations = new PendingBankOperations();

    IslandBankCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.bankUseCase = new BankUseCase(coreApiClient, economyBridge);
        this.targetResolver = new IslandTargetResolver(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("bank") || subcommand.equals("은행")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("logs")) {
                openBankLogs(player);
                return true;
            }
            openBankMenu(player);
            return true;
        }
        if (subcommand.equals("bank-balance") || subcommand.equals("balance") || subcommand.equals("bal") || subcommand.equals("money") || subcommand.equals("은행잔액")) {
            if (args.length < 2) {
                showBank(player);
            } else {
                showTargetBank(player, args[1]);
            }
            return true;
        }
        if (subcommand.equals("bank-balance-target")) {
            if (args.length < 2) {
                showBank(player);
            } else {
                showTargetBank(player, args[1]);
            }
            return true;
        }
        if (subcommand.equals("deposit") || subcommand.equals("bank-deposit") || subcommand.equals("입금")) {
            if (args.length < 2) {
                runtime.message(player, message("input-deposit-amount-required", "입금할 금액을 입력해주세요."));
                return true;
            }
            deposit(player, args[1]);
            return true;
        }
        if (subcommand.equals("withdraw") || subcommand.equals("bank-withdraw") || subcommand.equals("출금")) {
            if (args.length < 2) {
                runtime.message(player, message("input-withdraw-amount-required", "출금할 금액을 입력해주세요."));
                return true;
            }
            withdraw(player, args[1]);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.BankAmount bankAmount) {
            if (bankAmount.deposit()) {
                deposit(player, bankAmount.amount().toPlainString());
            } else {
                withdraw(player, bankAmount.amount().toPlainString());
            }
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.BANK_OPEN) {
            openBankMenu(player);
            return true;
        }
        return false;
    }

    private void showBank(Player player) {
        runtime.currentIsland(player, message("bank-balance-island-required", "섬 안에서만 은행을 확인할 수 있습니다.")).ifPresent(islandId -> showBank(player, islandId));
    }

    private void openBankLogs(Player player) {
        runtime.currentIsland(player, message("bank-logs-island-required", "섬 안에서만 은행 거래 로그를 확인할 수 있습니다."))
            .ifPresent(islandId -> IslandLogMenu.openBankLogs(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void showTargetBank(Player player, String target) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        targetResolver.resolve(target)
            .thenAccept(islandId -> showBank(playerSession, islandId))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("bank-target-not-found", "은행을 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    private void showBank(Player player, UUID islandId) {
        showBank(PlayerConnectionSession.capture(player), islandId);
    }

    private void showBank(PlayerConnectionSession playerSession, UUID islandId) {
        bankUseCase.bank(islandId)
                .thenAccept(result -> deliverMessage(playerSession, message("bank-balance-prefix", "섬 은행 잔액: ") + result.balance()))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("bank-load-failed", "섬 은행을 불러오지 못했습니다."));
                    return null;
                });
    }

    private void openBankMenu(Player player) {
        runtime.currentIsland(player, message("bank-menu-island-required", "섬 안에서만 은행 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandBankMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void deposit(Player player, String amount) {
        runtime.currentIsland(player, message("bank-deposit-island-required", "섬 안에서만 은행에 입금할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.DEPOSIT_BANK)) {
                runtime.message(player, message("bank-deposit-denied", "섬 은행에 입금할 권한이 없습니다."));
                return;
            }
            boolean all = amount != null && amount.trim().equals("*");
            BigDecimal parsedAmount = all ? null : BankUseCase.positiveAmount(amount);
            if (!all && parsedAmount == null) {
                runtime.message(player, runtime.playerCodeMessage("INVALID_AMOUNT", message("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            UUID playerUuid = playerSession.playerUuid();
            if (!pendingOperations.acquire(playerUuid)) {
                runtime.message(player, message("bank-operation-pending", "진행 중인 은행 작업이 끝난 뒤 다시 시도해주세요."));
                return;
            }
            CompletableFuture<BankUseCase.BankOperationResult> operation = all
                ? bankUseCase.depositAll(islandId, playerUuid, runtime::mutateIdempotent)
                : bankUseCase.deposit(islandId, playerUuid, parsedAmount, runtime::mutateIdempotent);
            operation
                .thenAccept(result -> handleDepositResult(playerSession, result))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("bank-deposit-failed", "섬 은행에 입금하지 못했습니다."));
                    return null;
                })
                .whenComplete((ignored, error) -> pendingOperations.release(playerUuid));
        });
    }

    private void withdraw(Player player, String amount) {
        runtime.currentIsland(player, message("bank-withdraw-island-required", "섬 안에서만 은행에서 출금할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.WITHDRAW_BANK)) {
                runtime.message(player, message("bank-withdraw-denied", "섬 은행에서 출금할 권한이 없습니다."));
                return;
            }
            boolean all = amount != null && amount.trim().equals("*");
            BigDecimal parsedAmount = all ? null : BankUseCase.positiveAmount(amount);
            if (!all && parsedAmount == null) {
                runtime.message(player, runtime.playerCodeMessage("INVALID_AMOUNT", message("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            UUID playerUuid = playerSession.playerUuid();
            if (!pendingOperations.acquire(playerUuid)) {
                runtime.message(player, message("bank-operation-pending", "진행 중인 은행 작업이 끝난 뒤 다시 시도해주세요."));
                return;
            }
            CompletableFuture<BankUseCase.BankOperationResult> operation = all
                ? bankUseCase.withdrawAll(islandId, playerUuid, runtime::mutateIdempotent)
                : bankUseCase.withdraw(islandId, playerUuid, parsedAmount, runtime::mutateIdempotent);
            operation
                .thenAccept(result -> handleWithdrawResult(playerSession, result))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("bank-withdraw-failed", "섬 은행에서 출금하지 못했습니다."));
                    return null;
                })
                .whenComplete((ignored, error) -> pendingOperations.release(playerUuid));
        });
    }

    private void handleDepositResult(PlayerConnectionSession playerSession, BankUseCase.BankOperationResult result) {
        switch (result.status()) {
            case SUCCESS -> deliverMessage(playerSession, message("bank-deposit-success-prefix", "섬 은행에 입금했습니다. 잔액: ") + result.balance());
            case ECONOMY_UNAVAILABLE -> deliverMessage(playerSession, message("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
            case ECONOMY_OPERATION_FAILED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("economy-operation-failed", "경제 플러그인 작업에 실패했습니다.")));
            case ECONOMY_WITHDRAW_DENIED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("bank-insufficient-balance", "잔액이 부족합니다.")));
            case CORE_REJECTED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("bank-deposit-failed", "섬 은행에 입금하지 못했습니다.")));
            case REFUND_FAILED_AFTER_CORE_REJECTION -> deliverMessage(playerSession, message("bank-deposit-refund-failed", "섬 은행 입금이 거부되었고 경제 환불도 실패했습니다. 관리자에게 문의해주세요."));
            case REFUND_FAILED_AFTER_CORE_FAILURE -> deliverMessage(playerSession, message("bank-deposit-core-failure-refund-failed", "Core 입금 처리와 경제 환불이 모두 실패했습니다. 재시도하지 말고 관리자에게 문의해주세요."));
            case ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE ->
                deliverMessage(playerSession, message("bank-deposit-failed", "섬 은행에 입금하지 못했습니다."));
        }
    }

    private void handleWithdrawResult(PlayerConnectionSession playerSession, BankUseCase.BankOperationResult result) {
        switch (result.status()) {
            case SUCCESS -> deliverMessage(playerSession, message("bank-withdraw-success-prefix", "섬 은행에서 출금했습니다. 잔액: ") + result.balance());
            case ECONOMY_UNAVAILABLE -> deliverMessage(playerSession, message("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
            case ECONOMY_OPERATION_FAILED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("economy-operation-failed", "경제 플러그인 작업에 실패했습니다.")));
            case CORE_REJECTED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("bank-withdraw-failed", "섬 은행에서 출금하지 못했습니다.")));
            case ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE -> deliverMessage(playerSession, message("bank-withdraw-economy-rollback", "경제 지급에 실패해 출금을 되돌렸습니다."));
            case ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE -> deliverMessage(playerSession, message("bank-withdraw-rollback-failed", "경제 지급에 실패했고 은행 되돌림도 실패했습니다. 관리자에게 문의해주세요."));
            case ECONOMY_WITHDRAW_DENIED -> deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("bank-insufficient-balance", "잔액이 부족합니다.")));
            case REFUND_FAILED_AFTER_CORE_REJECTION, REFUND_FAILED_AFTER_CORE_FAILURE -> deliverMessage(playerSession, message("bank-operation-refund-failed", "섬 은행 작업이 실패했고 경제 환불도 실패했습니다. 관리자에게 문의해주세요."));
        }
    }

    private void deliverMessage(PlayerConnectionSession playerSession, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(playerSession.playerUuid());
            if (playerSession.isCurrent(activePlayer)) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}

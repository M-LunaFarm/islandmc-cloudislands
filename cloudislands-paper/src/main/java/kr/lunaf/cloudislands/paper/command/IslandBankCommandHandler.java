package kr.lunaf.cloudislands.paper.command;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.BankUseCase;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandBankCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final BankUseCase bankUseCase;
    private final Runtime runtime;

    IslandBankCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.bankUseCase = new BankUseCase(coreApiClient, economyBridge);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("bank") || subcommand.equals("은행")) {
            openBankMenu(player);
            return true;
        }
        if (subcommand.equals("bank-balance") || subcommand.equals("은행잔액")) {
            showBank(player);
            return true;
        }
        if (subcommand.equals("deposit") || subcommand.equals("bank-deposit") || subcommand.equals("입금")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-deposit-amount-required", "입금할 금액을 입력해주세요."));
                return true;
            }
            deposit(player, args[1]);
            return true;
        }
        if (subcommand.equals("withdraw") || subcommand.equals("bank-withdraw") || subcommand.equals("출금")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-withdraw-amount-required", "출금할 금액을 입력해주세요."));
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
        String actionId = action.actionId();
        return switch (actionId) {
            case "island.bank.open" -> {
                openBankMenu(player);
                yield true;
            }
            default -> false;
        };
    }

    private void showBank(Player player) {
        runtime.currentIsland(player, "섬 안에서만 은행을 확인할 수 있습니다.").ifPresent(islandId -> {
            bankUseCase.bank(islandId)
                .thenAccept(result -> runtime.message(player, "섬 은행 잔액: " + result.balance()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 은행을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openBankMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 은행 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBankMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void deposit(Player player, String amount) {
        runtime.currentIsland(player, "섬 안에서만 은행에 입금할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.DEPOSIT_BANK)) {
                runtime.message(player, runtime.routeMessage("bank-deposit-denied", "섬 은행에 입금할 권한이 없습니다."));
                return;
            }
            BigDecimal parsedAmount = BankUseCase.positiveAmount(amount);
            if (parsedAmount == null) {
                player.sendMessage(runtime.playerCodeMessage("INVALID_AMOUNT", runtime.routeMessage("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            UUID playerUuid = player.getUniqueId();
            bankUseCase.deposit(islandId, playerUuid, parsedAmount, runtime::mutateIdempotent)
                .thenAccept(result -> handleDepositResult(player, result))
                .exceptionally(error -> {
                    runtime.message(player, "섬 은행에 입금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void withdraw(Player player, String amount) {
        runtime.currentIsland(player, "섬 안에서만 은행에서 출금할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.WITHDRAW_BANK)) {
                runtime.message(player, runtime.routeMessage("bank-withdraw-denied", "섬 은행에서 출금할 권한이 없습니다."));
                return;
            }
            BigDecimal parsedAmount = BankUseCase.positiveAmount(amount);
            if (parsedAmount == null) {
                player.sendMessage(runtime.playerCodeMessage("INVALID_AMOUNT", runtime.routeMessage("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            UUID playerUuid = player.getUniqueId();
            bankUseCase.withdraw(islandId, playerUuid, parsedAmount, runtime::mutateIdempotent)
                .thenAccept(result -> handleWithdrawResult(player, result))
                .exceptionally(error -> {
                    runtime.message(player, "섬 은행에서 출금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void handleDepositResult(Player player, BankUseCase.BankOperationResult result) {
        switch (result.status()) {
            case SUCCESS -> runtime.message(player, "섬 은행에 입금했습니다. 잔액: " + result.balance());
            case ECONOMY_UNAVAILABLE -> runtime.message(player, runtime.routeMessage("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
            case ECONOMY_WITHDRAW_DENIED -> runtime.message(player, runtime.playerCodeMessage(result.code(), "잔액이 부족합니다."));
            case CORE_REJECTED -> runtime.message(player, runtime.playerCodeMessage(result.code(), "섬 은행에 입금하지 못했습니다."));
            case ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE, ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE ->
                runtime.message(player, "섬 은행에 입금하지 못했습니다.");
        }
    }

    private void handleWithdrawResult(Player player, BankUseCase.BankOperationResult result) {
        switch (result.status()) {
            case SUCCESS -> runtime.message(player, "섬 은행에서 출금했습니다. 잔액: " + result.balance());
            case ECONOMY_UNAVAILABLE -> runtime.message(player, runtime.routeMessage("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
            case CORE_REJECTED -> runtime.message(player, runtime.playerCodeMessage(result.code(), "섬 은행에서 출금하지 못했습니다."));
            case ROLLED_BACK_AFTER_ECONOMY_DEPOSIT_FAILURE -> runtime.message(player, "경제 지급에 실패해 출금을 되돌렸습니다.");
            case ROLLBACK_FAILED_AFTER_ECONOMY_DEPOSIT_FAILURE -> runtime.message(player, "경제 지급에 실패했고 은행 되돌림도 실패했습니다. 관리자에게 문의해주세요.");
            case ECONOMY_WITHDRAW_DENIED -> runtime.message(player, runtime.playerCodeMessage(result.code(), "잔액이 부족합니다."));
        }
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

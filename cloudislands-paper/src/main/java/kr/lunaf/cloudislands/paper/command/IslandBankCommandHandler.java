package kr.lunaf.cloudislands.paper.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandBankCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final EconomyBridge economyBridge;
    private final Runtime runtime;

    IslandBankCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.economyBridge = economyBridge;
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
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.bank.open" -> {
                openBankMenu(player);
                yield true;
            }
            case "island.bank.deposit" -> {
                deposit(player, data.getOrDefault("amount", "0"));
                yield true;
            }
            case "island.bank.withdraw" -> {
                withdraw(player, data.getOrDefault("amount", "0"));
                yield true;
            }
            default -> false;
        };
    }

    private void showBank(Player player) {
        runtime.currentIsland(player, "섬 안에서만 은행을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBank(islandId)
                .thenAccept(body -> runtime.message(player, "섬 은행 잔액: " + bankBalance(body)))
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
            BigDecimal parsedAmount = positiveAmount(amount);
            if (parsedAmount == null) {
                player.sendMessage(runtime.playerCodeMessage("INVALID_AMOUNT", runtime.routeMessage("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            if (economyBridge == null) {
                runtime.message(player, runtime.routeMessage("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
                return;
            }
            String normalizedAmount = parsedAmount.toPlainString();
            UUID playerUuid = player.getUniqueId();
            economyBridge.withdraw(playerUuid, parsedAmount, "CloudIslands island bank deposit")
                .thenCompose(withdrawn -> {
                    if (!withdrawn) {
                        runtime.message(player, runtime.playerCodeMessage("INSUFFICIENT_FUNDS", "잔액이 부족합니다."));
                        return CompletableFuture.completedFuture(null);
                    }
                    return runtime.mutateIdempotent("island.bank.deposit", () -> coreApiClient.depositIslandBank(islandId, playerUuid, normalizedAmount)).thenCompose(body -> {
                        if (body.contains("\"accepted\":false")) {
                            return refundPlayer(playerUuid, parsedAmount)
                                .thenRun(() -> runtime.message(player, runtime.playerCodeMessage(text(body, "code"), "섬 은행에 입금하지 못했습니다.")));
                        }
                        runtime.message(player, "섬 은행에 입금했습니다. 잔액: " + bankBalance(body));
                        return CompletableFuture.completedFuture(null);
                    }).exceptionallyCompose(error -> refundPlayer(playerUuid, parsedAmount).thenRun(() -> runtime.message(player, "섬 은행에 입금하지 못했습니다.")));
                })
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
            BigDecimal parsedAmount = positiveAmount(amount);
            if (parsedAmount == null) {
                player.sendMessage(runtime.playerCodeMessage("INVALID_AMOUNT", runtime.routeMessage("input-amount-invalid", "올바른 금액을 입력해주세요.")));
                return;
            }
            if (economyBridge == null) {
                runtime.message(player, runtime.routeMessage("economy-unavailable", "경제 플러그인을 찾을 수 없습니다."));
                return;
            }
            String normalizedAmount = parsedAmount.toPlainString();
            UUID playerUuid = player.getUniqueId();
            runtime.mutateIdempotent("island.bank.withdraw", () -> coreApiClient.withdrawIslandBank(islandId, playerUuid, normalizedAmount))
                .thenCompose(body -> {
                    if (body.contains("\"accepted\":false")) {
                        runtime.message(player, runtime.playerCodeMessage(text(body, "code"), "섬 은행에서 출금하지 못했습니다."));
                        return CompletableFuture.completedFuture(null);
                    }
                    String balance = bankBalance(body);
                    return economyBridge.deposit(playerUuid, parsedAmount, "CloudIslands island bank withdraw")
                        .thenRun(() -> runtime.message(player, "섬 은행에서 출금했습니다. 잔액: " + balance))
                        .exceptionallyCompose(error -> runtime.mutateIdempotent("island.bank.withdraw.rollback", () -> coreApiClient.depositIslandBank(islandId, playerUuid, normalizedAmount))
                            .thenRun(() -> runtime.message(player, "경제 지급에 실패해 출금을 되돌렸습니다."))
                            .exceptionally(rollbackError -> {
                                runtime.message(player, "경제 지급에 실패했고 은행 되돌림도 실패했습니다. 관리자에게 문의해주세요.");
                                return null;
                            }));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 은행에서 출금하지 못했습니다.");
                    return null;
                });
        });
    }

    private CompletableFuture<Void> refundPlayer(UUID playerUuid, BigDecimal amount) {
        if (economyBridge == null) {
            return CompletableFuture.completedFuture(null);
        }
        return economyBridge.deposit(playerUuid, amount, "CloudIslands island bank deposit rollback")
            .exceptionally(error -> null);
    }

    private static BigDecimal positiveAmount(String amount) {
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

    private static String bankBalance(String body) {
        String balance = text(body, "balance");
        return balance.isBlank() ? "0" : balance;
    }

    private static String text(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
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

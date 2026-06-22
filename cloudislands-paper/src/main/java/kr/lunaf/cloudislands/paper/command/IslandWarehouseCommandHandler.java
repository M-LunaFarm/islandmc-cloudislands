package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase.WarehouseItemView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandWarehouseMenu;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandWarehouseCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandWarehouseUseCase warehouseUseCase;
    private final Runtime runtime;

    IslandWarehouseCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.warehouseUseCase = new IslandWarehouseUseCase(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("warehouse") || subcommand.equals("warehouse-list") || subcommand.equals("storage-box") || subcommand.equals("창고") || subcommand.equals("창고목록")) {
            listWarehouse(player, args.length > 1 ? integer(args[1], 27) : 27);
            return true;
        }
        if (subcommand.equals("warehouse-deposit") || subcommand.equals("창고입금")) {
            if (args.length < 3) {
                runtime.message(player, runtime.routeMessage("input-warehouse-deposit-required", "창고에 넣을 재료와 수량을 입력해주세요."));
                return true;
            }
            changeWarehouse(player, args[1], longValue(args[2], 0L), true);
            return true;
        }
        if (subcommand.equals("warehouse-withdraw") || subcommand.equals("창고출금")) {
            if (args.length < 3) {
                runtime.message(player, runtime.routeMessage("input-warehouse-withdraw-required", "창고에서 뺄 재료와 수량을 입력해주세요."));
                return true;
            }
            changeWarehouse(player, args[1], longValue(args[2], 0L), false);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.WAREHOUSE_OPEN) {
            openWarehouseMenu(player);
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.WAREHOUSE_DEPOSIT_HELP) {
            runtime.message(player, runtime.routeMessage("warehouse-deposit-help", "사용법: /섬 창고입금 <재료> <수량>"));
            return true;
        }
        return false;
    }

    private void openWarehouseMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 창고 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandWarehouseMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void listWarehouse(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 창고를 확인할 수 있습니다.").ifPresent(islandId -> {
            warehouseUseCase.listItems(islandId, limit)
                .thenAccept(items -> runtime.message(player, warehouseListMessage(items)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 창고를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void changeWarehouse(Player player, String materialKey, long amount, boolean deposit) {
        runtime.currentIsland(player, deposit ? "섬 안에서만 창고에 입금할 수 있습니다." : "섬 안에서만 창고에서 출금할 수 있습니다.").ifPresent(islandId -> {
            IslandPermission permission = deposit ? IslandPermission.OPEN_CONTAINER : IslandPermission.WITHDRAW_BANK;
            if (!runtime.allowed(player, permission)) {
                runtime.message(player, deposit ? runtime.routeMessage("warehouse-deposit-denied", "섬 창고에 넣을 권한이 없습니다.") : runtime.routeMessage("warehouse-withdraw-denied", "섬 창고에서 뺄 권한이 없습니다."));
                return;
            }
            if (amount <= 0L) {
                runtime.message(player, runtime.playerCodeMessage("INVALID_AMOUNT", runtime.routeMessage("input-amount-invalid", "올바른 수량을 입력해주세요.")));
                return;
            }
            CompletableFuture<IslandWarehouseUseCase.WarehouseOperationResult> request = deposit
                ? warehouseUseCase.deposit(islandId, player.getUniqueId(), materialKey, amount, runtime::mutateIdempotent)
                : warehouseUseCase.withdraw(islandId, player.getUniqueId(), materialKey, amount, runtime::mutateIdempotent);
            request.thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), deposit ? "섬 창고에 넣지 못했습니다." : "섬 창고에서 빼지 못했습니다."));
                        return;
                    }
                    runtime.message(player, (deposit ? "섬 창고 입금 완료: " : "섬 창고 출금 완료: ") + result.materialKey() + " x" + result.amount());
                })
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, deposit ? "섬 창고에 넣지 못했습니다." : "섬 창고에서 빼지 못했습니다."));
                    return null;
                });
        });
    }

    private static String warehouseListMessage(List<WarehouseItemView> items) {
        List<String> entries = items.stream()
            .limit(20)
            .map(item -> item.materialKey() + " x" + item.amount())
            .toList();
        return entries.isEmpty() ? "섬 창고가 비어 있습니다." : "섬 창고: " + String.join(", ", entries);
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        kr.lunaf.cloudislands.paper.message.MessageRenderer messagesFor(Player player);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }
}

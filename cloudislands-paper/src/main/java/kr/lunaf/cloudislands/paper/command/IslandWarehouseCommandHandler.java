package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.coreclient.WarehouseCommandClient;
import kr.lunaf.cloudislands.coreclient.WarehouseQueryClient;
import kr.lunaf.cloudislands.coreclient.WarehouseSettlementResult;
import kr.lunaf.cloudislands.coreclient.WarehouseSettlementView;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase.WarehouseItemView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandWarehouseMenu;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class IslandWarehouseCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandWarehouseUseCase warehouseUseCase;
    private final WarehouseQueryClient warehouseQueries;
    private final WarehouseCommandClient warehouseCommands;
    private final String nodeId;
    private final Runtime runtime;
    private final PendingWarehouseOperations pendingOperations = new PendingWarehouseOperations();
    private final NamespacedKey settlementKey;

    IslandWarehouseCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this(plugin, coreApiClient, runtime, "");
    }

    IslandWarehouseCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime, String nodeId) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.warehouseUseCase = new IslandWarehouseUseCase(coreApiClient);
        this.warehouseQueries = coreApiClient.warehouse();
        this.warehouseCommands = coreApiClient.warehouseCommands();
        this.nodeId = nodeId == null ? "" : nodeId.trim();
        this.runtime = runtime;
        this.settlementKey = new NamespacedKey(plugin, "warehouse_settlement");
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (isWarehouseMenuCommand(subcommand)) {
            openWarehouseMenu(player);
            return true;
        }
        if (isWarehouseListCommand(subcommand)) {
            listWarehouse(player, args.length > 1 ? integer(args[1], 27) : 27);
            return true;
        }
        if (subcommand.equals("warehouse-deposit") || subcommand.equals("창고입금")) {
            if (args.length < 3) {
                runtime.message(player, message("input-warehouse-deposit-required", "창고에 넣을 재료와 수량을 입력해주세요."));
                return true;
            }
            changeWarehouse(player, args[1], longValue(args[2], 0L), true);
            return true;
        }
        if (subcommand.equals("warehouse-withdraw") || subcommand.equals("창고출금")) {
            if (args.length < 3) {
                runtime.message(player, message("input-warehouse-withdraw-required", "창고에서 뺄 재료와 수량을 입력해주세요."));
                return true;
            }
            changeWarehouse(player, args[1], longValue(args[2], 0L), false);
            return true;
        }
        return false;
    }

    private static boolean isWarehouseMenuCommand(String subcommand) {
        return subcommand.equals("warehouse")
            || subcommand.equals("storage-box")
            || subcommand.equals("chest")
            || subcommand.equals("vault")
            || subcommand.equals("island-chest")
            || subcommand.equals("islandchest")
            || subcommand.equals("창고");
    }

    private static boolean isWarehouseListCommand(String subcommand) {
        return subcommand.equals("warehouse-list") || subcommand.equals("창고목록");
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.WarehousePage page) {
            IslandWarehouseMenu.open(plugin, coreApiClient, player, page.islandId(), runtime.messagesFor(player), page.page());
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.WAREHOUSE_OPEN) {
            openWarehouseMenu(player);
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.WAREHOUSE_DEPOSIT_HELP) {
            runtime.message(player, message("warehouse-deposit-help", "사용법: /섬 창고입금 <재료> <수량>"));
            return true;
        }
        return false;
    }

    private void openWarehouseMenu(Player player) {
        runtime.currentIsland(player, message("warehouse-menu-island-required", "섬 안에서만 창고 메뉴를 열 수 있습니다.")).ifPresent(islandId -> {
            if (!canOpenWarehouse(player)) {
                return;
            }
            IslandWarehouseMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player));
        });
    }

    private void listWarehouse(Player player, int limit) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        runtime.currentIsland(player, message("warehouse-list-island-required", "섬 안에서만 창고를 확인할 수 있습니다.")).ifPresent(islandId -> {
            if (!canOpenWarehouse(player)) {
                return;
            }
            warehouseUseCase.listItems(islandId, limit)
                .thenAccept(items -> deliverMessage(playerSession, warehouseListMessage(items)))
                .exceptionally(error -> {
                    deliverMessage(playerSession, message("warehouse-list-load-failed", "섬 창고를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private boolean canOpenWarehouse(Player player) {
        if (runtime.allowed(player, IslandPermission.OPEN_CONTAINER)) {
            return true;
        }
        runtime.message(player, message("warehouse-open-denied", "섬 창고를 열 권한이 없습니다."));
        return false;
    }

    private void changeWarehouse(Player player, String materialKey, long amount, boolean deposit) {
        if (resumePendingSettlement(player, true)) {
            return;
        }
        runtime.currentIsland(player, deposit ? message("warehouse-deposit-island-required", "섬 안에서만 창고에 입금할 수 있습니다.") : message("warehouse-withdraw-island-required", "섬 안에서만 창고에서 출금할 수 있습니다.")).ifPresent(islandId -> {
            IslandPermission permission = IslandPermission.OPEN_CONTAINER;
            if (!runtime.allowed(player, permission)) {
                runtime.message(player, deposit ? message("warehouse-deposit-denied", "섬 창고에 넣을 권한이 없습니다.") : message("warehouse-withdraw-denied", "섬 창고에서 뺄 권한이 없습니다."));
                return;
            }
            if (amount <= 0L) {
                runtime.message(player, runtime.playerCodeMessage("INVALID_AMOUNT", message("warehouse-amount-invalid", "올바른 수량을 입력해주세요.")));
                return;
            }
            Material material = material(materialKey);
            if (material == null || material.isAir()) {
                runtime.message(player, runtime.playerCodeMessage("INVALID_MATERIAL", message("input-material-invalid", "올바른 재료를 입력해주세요.")));
                return;
            }
            if (deposit) {
                long storableAmount = countStorableMaterial(player, material);
                if (storableAmount < amount) {
                    String fallback = countMaterial(player, material) >= amount
                        ? message("warehouse-item-metadata-unsupported", "이름, 인챈트, 내구도, 내용물 등 추가 정보가 없는 일반 아이템만 창고에 넣을 수 있습니다.")
                        : message("warehouse-not-enough-items", "인벤토리에 넣을 아이템이 부족합니다.");
                    runtime.message(player, runtime.playerCodeMessage("NOT_ENOUGH_ITEMS", fallback));
                    return;
                }
            }
            if (!deposit && inventorySpace(player, material) < amount) {
                runtime.message(player, runtime.playerCodeMessage("INVENTORY_FULL", message("warehouse-inventory-full", "인벤토리 공간이 부족합니다.")));
                return;
            }
            UUID playerUuid = player.getUniqueId();
            if (!pendingOperations.acquire(playerUuid)) {
                runtime.message(player, message("warehouse-operation-pending", "진행 중인 창고 작업이 끝난 뒤 다시 시도해주세요."));
                return;
            }
            CoreMutationMetadata mutation = CoreMutationMetadata.idempotent(deposit ? "island.warehouse.deposit" : "island.warehouse.withdraw");
            WarehouseSettlement settlement = new WarehouseSettlement(UUID.randomUUID(), islandId, material.name(), amount, deposit, mutation.idempotencyKey(), WarehouseSettlement.Phase.ESCROWED);
            prepareSettlement(PlayerConnectionSession.capture(player), material, settlement);
        });
    }

    void resumePendingSettlement(Player player) {
        if (!resumePendingSettlement(player, false)) {
            resumeSharedSettlement(player);
        }
    }

    private boolean resumePendingSettlement(Player player, boolean explicitRequest) {
        String encoded = player.getPersistentDataContainer().get(settlementKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return false;
        }
        Optional<WarehouseSettlement> decoded = WarehouseSettlement.decode(encoded);
        if (decoded.isEmpty()) {
            runtime.message(player, message("warehouse-settlement-corrupt", "복구할 창고 작업 정보가 손상되었습니다. 운영자에게 문의해주세요."));
            return true;
        }
        if (!pendingOperations.acquire(player.getUniqueId())) {
            if (explicitRequest) {
                runtime.message(player, message("warehouse-operation-pending", "진행 중인 창고 작업이 끝난 뒤 다시 시도해주세요."));
            }
            return true;
        }
        WarehouseSettlement settlement = decoded.get();
        runtime.message(player, message("warehouse-settlement-resuming", "완료되지 않은 창고 작업을 안전하게 복구하고 있습니다."));
        if (settlement.phase() == WarehouseSettlement.Phase.SETTLED) {
            clearSharedSettlement(PlayerConnectionSession.capture(player), settlement, true);
        } else {
            prepareRecoveryThenEscrow(PlayerConnectionSession.capture(player), settlement);
        }
        return true;
    }

    private void prepareRecoveryThenEscrow(PlayerConnectionSession playerSession, WarehouseSettlement settlement) {
        UUID playerUuid = playerSession.playerUuid();
        WarehouseSettlementView requested = settlementView(playerUuid, settlement, "PREPARED");
        CompletableFuture<WarehouseSettlementResult> request;
        try {
            request = runtime.mutateIdempotent("island.warehouse.settlement.prepare", settlement.idempotencyKey() + ".prepare", () -> warehouseCommands.prepareSettlement(requested));
        } catch (RuntimeException error) {
            pendingOperations.release(playerUuid);
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, message("warehouse-settlement-pending", "창고 작업을 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다."));
            }
            return;
        }
        request.whenComplete((result, error) -> PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                releaseAndResumeDurableSettlement(playerSession);
                return;
            }
            if (error != null || result == null || !result.accepted()) {
                pendingOperations.release(playerUuid);
                runtime.message(activePlayer, message("warehouse-settlement-pending", "창고 작업을 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다."));
                return;
            }
            escrowThenExecute(playerSession, settlement);
        }));
    }

    private void prepareSettlement(PlayerConnectionSession playerSession, Material material, WarehouseSettlement settlement) {
        UUID playerUuid = playerSession.playerUuid();
        WarehouseSettlementView requested = settlementView(playerUuid, settlement, "PREPARED");
        CompletableFuture<WarehouseSettlementResult> request;
        try {
            request = runtime.mutateIdempotent("island.warehouse.settlement.prepare", settlement.idempotencyKey() + ".prepare", () -> warehouseCommands.prepareSettlement(requested));
        } catch (RuntimeException error) {
            pendingOperations.release(playerUuid);
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, runtime.coreWriteFailureMessage(error, warehouseFailureMessage(settlement.deposit())));
            }
            return;
        }
        request.whenComplete((result, error) -> PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                releaseAndResumeDurableSettlement(playerSession);
                return;
            }
            if (error != null || result == null || !result.accepted()) {
                pendingOperations.release(playerUuid);
                runtime.message(activePlayer, error == null
                    ? runtime.playerCodeMessage(result == null ? "" : result.code(), warehouseFailureMessage(settlement.deposit()))
                    : runtime.coreWriteFailureMessage(error, warehouseFailureMessage(settlement.deposit())));
                return;
            }
            if (settlement.deposit() && countStorableMaterial(activePlayer, material) < settlement.amount()) {
                pendingOperations.release(playerUuid);
                clearPreparedSettlement(activePlayer, settlement);
                runtime.message(activePlayer, message("warehouse-not-enough-items", "인벤토리에 넣을 아이템이 부족합니다."));
                return;
            }
            if (!settlement.deposit() && inventorySpace(activePlayer, material) < settlement.amount()) {
                pendingOperations.release(playerUuid);
                clearPreparedSettlement(activePlayer, settlement);
                runtime.message(activePlayer, message("warehouse-inventory-full", "인벤토리 공간이 부족합니다."));
                return;
            }
            storeSettlement(activePlayer, settlement);
            if (settlement.deposit()) {
                removeMaterial(activePlayer, material, settlement.amount());
            }
            escrowThenExecute(playerSession, settlement);
        }));
    }

    private void escrowThenExecute(PlayerConnectionSession playerSession, WarehouseSettlement settlement) {
        UUID playerUuid = playerSession.playerUuid();
        CompletableFuture<WarehouseSettlementResult> request;
        try {
            request = runtime.mutateIdempotent("island.warehouse.settlement.escrow", settlement.idempotencyKey() + ".escrow", () -> warehouseCommands.escrowSettlement(playerUuid, settlement.settlementId()));
        } catch (RuntimeException error) {
            pendingOperations.release(playerUuid);
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, runtime.coreWriteFailureMessage(error, message("warehouse-settlement-pending", "창고 작업을 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다.")));
            }
            return;
        }
        request.whenComplete((result, error) -> PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                releaseAndResumeDurableSettlement(playerSession);
                return;
            }
            if (error != null || result == null || !result.accepted()) {
                pendingOperations.release(playerUuid);
                runtime.message(activePlayer, message("warehouse-settlement-pending", "창고 작업을 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다."));
                return;
            }
            executeSettlement(playerSession, settlement);
        }));
    }

    private void resumeSharedSettlement(Player player) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID playerUuid = playerSession.playerUuid();
        if (!pendingOperations.acquire(playerUuid)) {
            return;
        }
        warehouseQueries.pendingSettlement(playerUuid).whenComplete((pending, error) -> PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                releaseAndResumeDurableSettlement(playerSession);
                return;
            }
            if (error != null || pending == null || pending.isEmpty()) {
                pendingOperations.release(playerUuid);
                return;
            }
            WarehouseSettlementView view = pending.get();
            if (!"ESCROWED".equals(view.state())) {
                pendingOperations.release(playerUuid);
                if ("PREPARED".equals(view.state()) && !nodeId.isBlank() && nodeId.equals(view.ownerNodeId())) {
                    clearPreparedSettlement(activePlayer, settlement(view));
                    runtime.message(activePlayer, message("warehouse-settlement-pending", "중단된 창고 준비 작업을 취소했습니다. 다시 시도해주세요."));
                    return;
                }
                runtime.message(activePlayer, message("warehouse-settlement-pending", "이전 서버에서 창고 작업을 준비 중입니다. 잠시 후 다시 접속해주세요."));
                return;
            }
            WarehouseSettlement settlement = settlement(view);
            if (settlement == null) {
                pendingOperations.release(playerUuid);
                runtime.message(activePlayer, message("warehouse-settlement-corrupt", "복구할 창고 작업 정보가 손상되었습니다. 운영자에게 문의해주세요."));
                return;
            }
            storeSettlement(activePlayer, settlement);
            runtime.message(activePlayer, message("warehouse-settlement-resuming", "다른 서버에서 완료되지 않은 창고 작업을 안전하게 복구하고 있습니다."));
            executeSettlement(playerSession, settlement);
        }));
    }

    private void executeSettlement(PlayerConnectionSession playerSession, WarehouseSettlement settlement) {
        UUID playerUuid = playerSession.playerUuid();
        Material material = material(settlement.materialKey());
        if (material == null || material.isAir()) {
            pendingOperations.release(playerUuid);
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, message("warehouse-settlement-corrupt", "복구할 창고 작업 정보가 손상되었습니다. 운영자에게 문의해주세요."));
            }
            return;
        }
        CompletableFuture<IslandWarehouseUseCase.WarehouseOperationResult> request;
        try {
            IslandWarehouseUseCase.MutationRunner runner = (auditAction, operation) -> runtime.mutateIdempotent(
                auditAction,
                settlement.idempotencyKey(),
                operation
            );
            request = settlement.deposit()
                ? warehouseUseCase.deposit(settlement.islandId(), playerUuid, settlement.materialKey(), settlement.amount(), runner)
                : warehouseUseCase.withdraw(settlement.islandId(), playerUuid, settlement.materialKey(), settlement.amount(), runner);
        } catch (RuntimeException error) {
            pendingOperations.release(playerUuid);
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, runtime.coreWriteFailureMessage(error, message("warehouse-settlement-pending", "창고 응답을 확정하지 못했습니다. 아이템은 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다.")));
            }
            return;
        }
        request.whenComplete((result, error) -> PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                releaseAndResumeDurableSettlement(playerSession);
                return;
            }
            try {
                if (error != null) {
                    runtime.message(activePlayer, runtime.coreWriteFailureMessage(error, message(
                        "warehouse-settlement-pending",
                        "창고 응답을 확정하지 못했습니다. 아이템은 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다."
                    )));
                    return;
                }
                handleWarehouseResult(activePlayer, material, settlement, result);
            } finally {
                pendingOperations.release(playerUuid);
            }
        }));
    }

    private void handleWarehouseResult(Player player, Material material, WarehouseSettlement settlement, IslandWarehouseUseCase.WarehouseOperationResult result) {
        if (result == null || recoveryPending(result.code())) {
            runtime.message(player, message("warehouse-settlement-pending", "창고 응답을 확정하지 못했습니다. 아이템은 보호 중이며 다음 시도나 재접속 때 자동 복구됩니다."));
            return;
        }
        if (!result.accepted()) {
            if (settlement.deposit()) {
                giveMaterial(player, material, settlement.amount());
            }
            storeSettlement(player, settlement.settled());
            clearSharedSettlement(PlayerConnectionSession.capture(player), settlement, false);
            runtime.message(player, runtime.playerCodeMessage(result.code(), warehouseFailureMessage(settlement.deposit())));
            return;
        }
        if (!settlement.deposit()) {
            giveMaterial(player, material, settlement.amount());
        }
        storeSettlement(player, settlement.settled());
        clearSharedSettlement(PlayerConnectionSession.capture(player), settlement, false);
        runtime.message(player, warehouseSuccessPrefix(settlement.deposit()) + settlement.materialKey() + " x" + settlement.amount());
    }

    private void storeSettlement(Player player, WarehouseSettlement settlement) {
        player.getPersistentDataContainer().set(settlementKey, PersistentDataType.STRING, settlement.encode());
    }

    private void clearLocalSettlement(Player player, WarehouseSettlement completed) {
        String encoded = player.getPersistentDataContainer().get(settlementKey, PersistentDataType.STRING);
        Optional<WarehouseSettlement> current = WarehouseSettlement.decode(encoded);
        if (current.isPresent() && current.get().settlementId().equals(completed.settlementId())) {
            player.getPersistentDataContainer().remove(settlementKey);
        }
    }

    private void clearPreparedSettlement(Player player, WarehouseSettlement settlement) {
        if (settlement == null) {
            return;
        }
        runtime.mutateIdempotent("island.warehouse.settlement.clear", settlement.idempotencyKey() + ".clear", () -> warehouseCommands.clearSettlement(player.getUniqueId(), settlement.settlementId()))
            .exceptionally(error -> null);
    }

    private void clearSharedSettlement(PlayerConnectionSession playerSession, WarehouseSettlement settlement, boolean releaseWhenDone) {
        UUID playerUuid = playerSession.playerUuid();
        CompletableFuture<WarehouseSettlementResult> request;
        try {
            request = runtime.mutateIdempotent("island.warehouse.settlement.clear", settlement.idempotencyKey() + ".clear", () -> warehouseCommands.clearSettlement(playerUuid, settlement.settlementId()));
        } catch (RuntimeException error) {
            if (releaseWhenDone) {
                pendingOperations.release(playerUuid);
            }
            return;
        }
        request.whenComplete((result, error) -> PaperSchedulers.run(plugin, () -> {
            try {
                Player activePlayer = currentPlayer(playerSession);
                if (activePlayer != null && error == null && result != null && result.accepted()) {
                    clearLocalSettlement(activePlayer, settlement);
                }
            } finally {
                if (releaseWhenDone) {
                    pendingOperations.release(playerUuid);
                    resumeReplacementSettlement(playerSession);
                }
            }
        }));
    }

    private void deliverMessage(PlayerConnectionSession playerSession, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private Player currentPlayer(PlayerConnectionSession playerSession) {
        Player activePlayer = plugin.getServer().getPlayer(playerSession.playerUuid());
        return playerSession.isCurrent(activePlayer) ? activePlayer : null;
    }

    private void releaseAndResumeDurableSettlement(PlayerConnectionSession playerSession) {
        pendingOperations.release(playerSession.playerUuid());
        resumeReplacementSettlement(playerSession);
    }

    private void resumeReplacementSettlement(PlayerConnectionSession playerSession) {
        Player replacement = plugin.getServer().getPlayer(playerSession.playerUuid());
        if (replacement != null && replacement != playerSession.expectedPlayer() && replacement.isOnline()) {
            resumePendingSettlement(replacement);
        }
    }

    private static WarehouseSettlementView settlementView(UUID playerUuid, WarehouseSettlement settlement, String state) {
        return new WarehouseSettlementView(
            settlement.settlementId(),
            playerUuid,
            settlement.islandId(),
            settlement.materialKey(),
            settlement.amount(),
            settlement.deposit() ? "DEPOSIT" : "WITHDRAW",
            state,
            settlement.idempotencyKey(),
            "",
            "",
            ""
        );
    }

    private static WarehouseSettlement settlement(WarehouseSettlementView view) {
        if (view == null || view.settlementId() == null || view.islandId() == null || view.idempotencyKey().isBlank()) {
            return null;
        }
        boolean deposit;
        if ("DEPOSIT".equals(view.direction())) {
            deposit = true;
        } else if ("WITHDRAW".equals(view.direction())) {
            deposit = false;
        } else {
            return null;
        }
        try {
            return new WarehouseSettlement(view.settlementId(), view.islandId(), view.materialKey(), view.amount(), deposit, view.idempotencyKey(), WarehouseSettlement.Phase.ESCROWED);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean recoveryPending(String code) {
        return code == null || code.isBlank()
            || code.equals("IDEMPOTENCY_IN_PROGRESS")
            || code.equals("IDEMPOTENCY_UNAVAILABLE")
            || code.equals("IDEMPOTENCY_COMMIT_FAILED")
            || code.equals("IDEMPOTENCY_RECEIPT_INVALID")
            || code.equals("IDEMPOTENCY_KEY_REUSED");
    }

    private String warehouseListMessage(List<WarehouseItemView> items) {
        List<String> entries = items.stream()
            .limit(20)
            .map(item -> item.materialKey() + " x" + item.amount())
            .toList();
        return entries.isEmpty() ? message("warehouse-list-empty", "섬 창고가 비어 있습니다.") : message("warehouse-list-prefix", "섬 창고: ") + String.join(", ", entries);
    }

    private String warehouseFailureMessage(boolean deposit) {
        return deposit ? message("warehouse-deposit-failed", "섬 창고에 넣지 못했습니다.") : message("warehouse-withdraw-failed", "섬 창고에서 빼지 못했습니다.");
    }

    private String warehouseSuccessPrefix(boolean deposit) {
        return deposit ? message("warehouse-deposit-success-prefix", "섬 창고 입금 완료: ") : message("warehouse-withdraw-success-prefix", "섬 창고 출금 완료: ");
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
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

    private static Material material(String materialKey) {
        String normalized = materialKey == null ? "" : materialKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.isBlank() ? null : Material.matchMaterial(normalized);
    }

    private static long countMaterial(Player player, Material material) {
        long count = 0L;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static long countStorableMaterial(Player player, Material material) {
        long count = 0L;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (WarehouseItemPolicy.storable(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static long inventorySpace(Player player, Material material) {
        long space = 0L;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                space += maxStack;
            } else if (WarehouseItemPolicy.storable(item, material) && item.getAmount() < maxStack) {
                space += maxStack - item.getAmount();
            }
        }
        return space;
    }

    private static void removeMaterial(Player player, Material material, long amount) {
        long remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0L; index++) {
            ItemStack item = contents[index];
            if (!WarehouseItemPolicy.storable(item, material)) {
                continue;
            }
            int taken = (int) Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - taken);
            remaining -= taken;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private static void giveMaterial(Player player, Material material, long amount) {
        long remaining = amount;
        int maxStack = material.getMaxStackSize();
        while (remaining > 0L) {
            int stackAmount = (int) Math.min(maxStack, remaining);
            java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, stackAmount));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
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

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, String idempotencyKey, Supplier<CompletableFuture<T>> operation);
    }
}

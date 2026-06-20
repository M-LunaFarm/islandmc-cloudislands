package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.entity.Player;

final class IslandWarehouseCommandHandler {
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandWarehouseCommandHandler(CoreApiClient coreApiClient, Runtime runtime) {
        this.coreApiClient = coreApiClient;
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

    private void listWarehouse(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 창고를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandWarehouse(islandId, Math.max(1, Math.min(limit, 100)))
                .thenAccept(body -> runtime.message(player, warehouseListMessage(body)))
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
            CompletableFuture<String> request = deposit
                ? runtime.mutateIdempotent("island.warehouse.deposit", () -> coreApiClient.depositIslandWarehouse(islandId, player.getUniqueId(), materialKey, amount))
                : runtime.mutateIdempotent("island.warehouse.withdraw", () -> coreApiClient.withdrawIslandWarehouse(islandId, player.getUniqueId(), materialKey, amount));
            request.thenAccept(body -> {
                    String code = text(body, "code");
                    if (body.contains("\"accepted\":false")) {
                        runtime.message(player, runtime.playerCodeMessage(code, deposit ? "섬 창고에 넣지 못했습니다." : "섬 창고에서 빼지 못했습니다."));
                        return;
                    }
                    runtime.message(player, (deposit ? "섬 창고 입금 완료: " : "섬 창고 출금 완료: ") + text(body, "materialKey") + " x" + (long) decimal(body, "amount"));
                })
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, deposit ? "섬 창고에 넣지 못했습니다." : "섬 창고에서 빼지 못했습니다."));
                    return null;
                });
        });
    }

    private static String warehouseListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 창고가 비어 있습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"items\"");
        while (index >= 0 && index < body.length() && entries.size() < 20) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String materialKey = text(object, "materialKey");
            long amount = (long) decimal(object, "amount");
            if (!materialKey.isBlank() && amount > 0L) {
                entries.add(materialKey + " x" + amount);
            }
            index = objectEnd + 1;
        }
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

    private static double decimal(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
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

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }
}

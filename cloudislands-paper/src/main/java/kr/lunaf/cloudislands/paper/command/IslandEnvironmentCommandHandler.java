package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.application.IslandEnvironmentUseCase;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBiomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLimitMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandEnvironmentCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandEnvironmentUseCase environmentUseCase;
    private final ProtectionController protection;
    private final Runtime runtime;

    IslandEnvironmentCommandHandler(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.environmentUseCase = new IslandEnvironmentUseCase(coreApiClient);
        this.protection = protection;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("biome") || subcommand.equals("바이옴")) {
            if (args.length > 1) {
                setBiome(player, args[1]);
            } else {
                openBiomeMenu(player);
            }
            return true;
        }
        if (subcommand.equals("biome-menu")) {
            openBiomeMenu(player);
            return true;
        }
        if (subcommand.equals("biome-info") || subcommand.equals("바이옴정보")) {
            showBiome(player);
            return true;
        }
        if (subcommand.equals("size") || subcommand.equals("크기")) {
            showSize(player);
            return true;
        }
        if (subcommand.equals("border") || subcommand.equals("border-ui") || subcommand.equals("경계")) {
            handleBorder(player, args);
            return true;
        }
        if (subcommand.equals("border-color") || subcommand.equals("경계색상")) {
            if (args.length < 2) {
                runtime.message(player, "경계 색상을 입력해주세요. 예: /섬 경계색상 blue");
                return true;
            }
            setBorderFlag(player, IslandFlag.BORDER_COLOR, normalizeBorderColor(args[1]), true);
            return true;
        }
        if (subcommand.equals("border-visible") || subcommand.equals("경계표시")) {
            if (args.length < 2) {
                runtime.message(player, "경계 표시 여부를 입력해주세요. 예: /섬 경계표시 켜기");
                return true;
            }
            setBorderFlag(player, IslandFlag.BORDER_VISIBLE, toggleValue(args, 1), true);
            return true;
        }
        if (subcommand.equals("limit") || subcommand.equals("limits") || subcommand.equals("limit-list") || subcommand.equals("제한") || subcommand.equals("제한목록")) {
            if (args.length > 2) {
                setLimit(player, args[1], longValue(args[2], 0L));
            } else if (subcommand.equals("limit-list") || subcommand.equals("제한목록")) {
                listLimits(player);
            } else {
                openLimitMenu(player);
            }
            return true;
        }
        if (subcommand.equals("limit-menu")) {
            openLimitMenu(player);
            return true;
        }
        if (subcommand.equals("setlimit") || subcommand.equals("limit-set") || subcommand.equals("제한설정")) {
            if (args.length < 3) {
                runtime.message(player, runtime.routeMessage("input-limit-key-value-required", "제한 키와 값을 입력해주세요."));
                return true;
            }
            setLimit(player, args[1], longValue(args[2], 0L));
            return true;
        }
        if (subcommand.equals("hoppers") || subcommand.equals("호퍼")) {
            setNamedLimit(player, "HOPPER", args);
            return true;
        }
        if (subcommand.equals("spawners") || subcommand.equals("스포너")) {
            setNamedLimit(player, "SPAWNER", args);
            return true;
        }
        if (subcommand.equals("entities") || subcommand.equals("엔티티")) {
            setNamedLimit(player, "ENTITY", args);
            return true;
        }
        if (subcommand.equals("redstone") || subcommand.equals("레드스톤")) {
            setNamedLimit(player, "REDSTONE", args);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.BiomeSet biomeSet) {
            setBiome(player, biomeSet.biomeKey());
            return true;
        }
        if (action instanceof GuiAction.LimitSet limitSet) {
            setLimit(player, limitSet.limitKey(), limitSet.value());
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case BIOME_OPEN -> {
                    openBiomeMenu(player);
                    yield true;
                }
                case BIOME_SHOW -> {
                    showBiome(player);
                    yield true;
                }
                case LIMITS_OPEN -> {
                    openLimitMenu(player);
                    yield true;
                }
                case LIMITS_LIST -> {
                    listLimits(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void showBiome(Player player) {
        runtime.currentIsland(player, "섬 안에서만 바이옴을 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.islandBiome(islandId)
                .thenAccept(body -> runtime.message(player, "섬 바이옴: " + text(body, "biomeKey")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 바이옴을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openBiomeMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 바이옴 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBiomeMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setBiome(Player player, String biomeKey) {
        runtime.currentIsland(player, "섬 안에서만 바이옴을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.SET_BIOME)) {
                runtime.message(player, runtime.routeMessage("biome-set-denied", "섬 바이옴을 변경할 권한이 없습니다."));
                return;
            }
            environmentUseCase.setBiome(islandId, player.getUniqueId(), biomeKey, runtime::mutate)
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 바이옴 변경 " + biomeKey, biomeKey, body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 바이옴을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showSize(Player player) {
        runtime.currentIsland(player, "섬 안에서만 크기를 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.islandInfo(islandId)
                .thenAccept(body -> runtime.message(player, "섬 크기: " + (long) decimal(body, "size")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 크기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showBorder(Player player) {
        runtime.currentIsland(player, "섬 안에서만 경계를 확인할 수 있습니다.").ifPresent(islandId -> {
            CompletableFuture<String> info = environmentUseCase.islandInfo(islandId);
            CompletableFuture<String> flags = environmentUseCase.listFlags(islandId);
            info.thenCombine(flags, IslandEnvironmentCommandHandler::borderSummary)
                .thenAccept(summary -> runtime.message(player, summary))
                .exceptionally(error -> {
                    runtime.message(player, "섬 경계를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void handleBorder(Player player, String[] args) {
        if (args.length < 2) {
            showBorder(player);
            applyBorder(player, false);
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("apply") || mode.equals("적용")) {
            applyBorder(player, true);
            return;
        }
        if (mode.equals("hide") || mode.equals("hidden") || mode.equals("숨김")) {
            setBorderFlag(player, IslandFlag.BORDER_VISIBLE, "false", true);
            return;
        }
        if (mode.equals("show") || mode.equals("visible") || mode.equals("표시")) {
            String value = args.length > 2 ? toggleValue(args, 2) : "true";
            setBorderFlag(player, IslandFlag.BORDER_VISIBLE, value, true);
            return;
        }
        if (mode.equals("color") || mode.equals("색상")) {
            if (args.length < 3) {
                runtime.message(player, "경계 색상을 입력해주세요. 예: /섬 경계 색상 blue");
                return;
            }
            setBorderFlag(player, IslandFlag.BORDER_COLOR, normalizeBorderColor(args[2]), true);
            return;
        }
        if (mode.equals("warning") || mode.equals("경고")) {
            if (args.length < 3) {
                runtime.message(player, "경계 경고 거리를 입력해주세요. 예: /섬 경계 경고 8");
                return;
            }
            setBorderFlag(player, IslandFlag.BORDER_WARNING_BLOCKS, Long.toString(Math.max(0L, longValue(args[2], 0L))), true);
            return;
        }
        if (mode.equals("policy") || mode.equals("정책")) {
            if (args.length < 3) {
                runtime.message(player, "경계 정책을 입력해주세요. 예: /섬 경계 정책 visible");
                return;
            }
            setBorderFlag(player, IslandFlag.BORDER_POLICY, normalizeBorderPolicy(args[2]), true);
            return;
        }
        showBorder(player);
    }

    private void setBorderFlag(Player player, IslandFlag flag, String value, boolean applyAfterSave) {
        runtime.currentIsland(player, "섬 안에서만 경계 정책을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, runtime.routeMessage("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다."));
                return;
            }
            environmentUseCase.setFlag(islandId, player.getUniqueId(), flag, value, runtime::mutate)
                .thenAccept(body -> {
                    runtime.message(player, runtime.actionResultMessage("섬 경계 정책 변경 " + flag.name() + "=" + value, flag.name(), body));
                    if (applyAfterSave && !resultRejected(body)) {
                        applyBorder(player, true);
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 경계 정책을 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void applyBorder(Player player, boolean announce) {
        runtime.currentIsland(player, "섬 안에서만 경계를 적용할 수 있습니다.").ifPresent(islandId -> {
            Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
            if (region.isEmpty()) {
                runtime.message(player, "섬 경계 위치를 확인하지 못했습니다.");
                return;
            }
            CompletableFuture<String> info = environmentUseCase.islandInfo(islandId);
            CompletableFuture<String> flags = environmentUseCase.listFlags(islandId);
            info.thenCombine(flags, (infoBody, flagBody) -> new BorderView(infoBody, flagBody, region.get()))
                .thenAccept(view -> PaperSchedulers.run(plugin, () -> applyBorderSync(player, view, announce)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 경계 UI를 적용하지 못했습니다.");
                    return null;
                });
        });
    }

    private void applyBorderSync(Player player, BorderView view, boolean announce) {
        boolean visible = borderVisible(view.flags());
        String policy = flagValue(view.flags(), IslandFlag.BORDER_POLICY, visible ? "visible" : "hidden");
        if (!visible || policy.equalsIgnoreCase("hidden")) {
            player.setWorldBorder(null);
            if (announce) {
                runtime.message(player, "섬 경계 UI를 숨겼습니다.");
            }
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(view.region().originX(), view.region().originZ());
        border.setSize(Math.max(1.0D, decimal(view.info(), "border")));
        border.setWarningDistance(Math.max(0, (int) longValue(flagValue(view.flags(), IslandFlag.BORDER_WARNING_BLOCKS, "8"), 8L)));
        border.setWarningTime(5);
        player.setWorldBorder(border);
        if (announce) {
            runtime.message(player, "섬 경계 UI 적용: 색상=" + flagValue(view.flags(), IslandFlag.BORDER_COLOR, "blue") + ", 정책=" + policy + ", 크기=" + (long) decimal(view.info(), "border"));
        }
    }

    private void listLimits(Player player) {
        runtime.currentIsland(player, "섬 안에서만 제한을 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.listLimits(islandId)
                .thenAccept(body -> runtime.message(player, limitListMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 제한을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openLimitMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 제한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandLimitMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setNamedLimit(Player player, String limitKey, String[] args) {
        if (args.length < 2) {
            runtime.message(player, runtime.routeMessage("input-limit-value-required", "제한 값을 입력해주세요."));
            return;
        }
        setLimit(player, limitKey, longValue(args[1], 0L));
    }

    private void setLimit(Player player, String limitKey, long value) {
        runtime.currentIsland(player, "섬 안에서만 제한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                runtime.message(player, runtime.routeMessage("limit-set-denied", "섬 제한을 변경할 권한이 없습니다."));
                return;
            }
            environmentUseCase.setLimit(islandId, player.getUniqueId(), limitKey, value, runtime::mutate)
                .thenAccept(body -> {
                    if (resultRejected(body)) {
                        runtime.message(player, runtime.playerCodeMessage(text(body, "code"), "섬 제한을 변경하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, "섬 제한 변경 완료: " + text(body, "limitKey") + " = " + (long) decimal(body, "value"));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 제한을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private static String borderSummary(String infoBody, String flagBody) {
        return "섬 경계: 크기=" + (long) decimal(infoBody, "border")
            + ", 표시=" + (borderVisible(flagBody) ? "켜짐" : "꺼짐")
            + ", 색상=" + flagValue(flagBody, IslandFlag.BORDER_COLOR, "blue")
            + ", 정책=" + flagValue(flagBody, IslandFlag.BORDER_POLICY, "visible")
            + ", 경고거리=" + flagValue(flagBody, IslandFlag.BORDER_WARNING_BLOCKS, "8");
    }

    private static boolean borderVisible(String flagBody) {
        String value = flagValue(flagBody, IslandFlag.BORDER_VISIBLE, "true");
        return !value.equalsIgnoreCase("false")
            && !value.equalsIgnoreCase("off")
            && !value.equals("0")
            && !value.equalsIgnoreCase("hide")
            && !value.equalsIgnoreCase("hidden")
            && !value.equals("숨김");
    }

    private static String flagValue(String body, IslandFlag flag, String fallback) {
        String value = text(body, flag.name());
        return value.isBlank() ? fallback : value;
    }

    private static String normalizeBorderColor(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "red", "빨강" -> "red";
            case "green", "초록" -> "green";
            case "aqua", "cyan", "하늘" -> "aqua";
            case "yellow", "노랑" -> "yellow";
            case "purple", "보라" -> "purple";
            default -> "blue";
        };
    }

    private static String normalizeBorderPolicy(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("hidden") || normalized.equals("hide") || normalized.equals("숨김")) {
            return "hidden";
        }
        if (normalized.equals("warning") || normalized.equals("warn") || normalized.equals("경고")) {
            return "warning";
        }
        return "visible";
    }

    private static String limitListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "limitKey");
            if (!key.isBlank()) {
                entries.add(key + " 값=" + (long) decimal(object, "value"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 제한이 없습니다." : "섬 제한: " + String.join(", ", entries);
    }

    private static String toggleValue(String[] args, int index) {
        if (args.length <= index) {
            return "true";
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("false") || value.equals("off") || value.equals("0") || value.equals("끄기") || value.equals("비활성")) {
            return "false";
        }
        return "true";
    }

    private static boolean resultRejected(String body) {
        return body == null || body.contains("\"error\"") || body.contains("\"accepted\":false") || body.contains("\"applied\":false");
    }

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private static double decimal(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length()) {
            char current = json.charAt(end);
            if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.') {
                end++;
                continue;
            }
            break;
        }
        try {
            return Double.parseDouble(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaping = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                default -> builder.append(current);
            }
            escaping = false;
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        String actionResultMessage(String label, UUID targetId, String body);

        String actionResultMessage(String label, String targetId, String body);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }

    private record BorderView(String info, String flags, IslandRegion region) {}
}

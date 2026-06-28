package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LimitView;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.application.IslandBorderRuntimePolicy;
import kr.lunaf.cloudislands.paper.application.IslandEnvironmentUseCase;
import kr.lunaf.cloudislands.paper.application.IslandEnvironmentUseCase.EnvironmentActionResult;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandBorderMenu;
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
            setBorderFlag(player, IslandFlag.BORDER_COLOR, IslandBorderRuntimePolicy.normalizeColor(args[1]), true);
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
        if (action instanceof GuiAction.BorderColorSet colorSet) {
            setBorderFlag(player, IslandFlag.BORDER_COLOR, IslandBorderRuntimePolicy.normalizeColor(colorSet.color()), true);
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
                case BORDER_OPEN -> {
                    openBorderMenu(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void openBorderMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 보더 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBorderMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void showBiome(Player player) {
        runtime.currentIsland(player, "섬 안에서만 바이옴을 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.islandBiomeValue(islandId)
                .thenAccept(biome -> runtime.message(player, "섬 바이옴: " + biome.key()))
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
            environmentUseCase.setBiomeAction(islandId, player.getUniqueId(), biomeKey, runtime::mutate)
                .thenAccept(result -> runtime.message(player, biomeActionMessage(result, biomeKey)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 바이옴을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showSize(Player player) {
        runtime.currentIsland(player, "섬 안에서만 크기를 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.islandInfoView(islandId)
                .thenAccept(info -> runtime.message(player, "섬 크기: " + info.size()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 크기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showBorder(Player player) {
        runtime.currentIsland(player, "섬 안에서만 경계를 확인할 수 있습니다.").ifPresent(islandId -> {
            CompletableFuture<IslandInfoView> info = environmentUseCase.islandInfoView(islandId);
            CompletableFuture<Map<IslandFlag, String>> flags = environmentUseCase.flagValues(islandId);
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
            setBorderFlag(player, IslandFlag.BORDER_COLOR, IslandBorderRuntimePolicy.normalizeColor(args[2]), true);
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
            setBorderFlag(player, IslandFlag.BORDER_POLICY, IslandBorderRuntimePolicy.normalizePolicy(args[2]), true);
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
            environmentUseCase.setFlagAction(islandId, player.getUniqueId(), flag, value, runtime::mutate)
                .thenAccept(result -> {
                    runtime.message(player, environmentActionMessage(result, "섬 경계 정책 변경 완료: " + flag.name() + "=" + value, "섬 경계 정책을 변경하지 못했습니다."));
                    if (applyAfterSave && result.accepted()) {
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
            CompletableFuture<IslandInfoView> info = environmentUseCase.islandInfoView(islandId);
            CompletableFuture<Map<IslandFlag, String>> flags = environmentUseCase.flagValues(islandId);
            info.thenCombine(flags, (infoView, flagValues) -> new BorderView(infoView, flagValues, region.get()))
                .thenAccept(view -> PaperSchedulers.run(plugin, () -> applyBorderSync(player, view, announce)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 경계 UI를 적용하지 못했습니다.");
                    return null;
                });
        });
    }

    private void applyBorderSync(Player player, BorderView view, boolean announce) {
        IslandBorderRuntimePolicy.BorderSettings settings = IslandBorderRuntimePolicy.settings(view.info().border(), view.flags(), view.region());
        if (!settings.visible()) {
            player.setWorldBorder(null);
            if (announce) {
                runtime.message(player, "섬 경계 UI를 숨겼습니다.");
            }
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(settings.centerX(), settings.centerZ());
        border.setSize(settings.size());
        border.setWarningDistance(settings.warningDistance());
        border.setWarningTime(5);
        player.setWorldBorder(border);
        if (announce) {
            runtime.message(player, "섬 경계 UI 적용: 색상=" + settings.color() + ", 정책=" + settings.policy() + ", 크기=" + view.info().border());
        }
    }

    private void listLimits(Player player) {
        runtime.currentIsland(player, "섬 안에서만 제한을 확인할 수 있습니다.").ifPresent(islandId -> {
            environmentUseCase.limitViews(islandId)
                .thenAccept(limits -> runtime.message(player, limitListMessage(limits)))
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
            environmentUseCase.setLimitAction(islandId, player.getUniqueId(), limitKey, value, runtime::mutate)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), "섬 제한을 변경하지 못했습니다."));
                        return;
                    }
                    String key = result.key().isBlank() ? limitKey : result.key();
                    runtime.message(player, "섬 제한 변경 완료: " + key + " = " + result.value());
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 제한을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private String environmentActionMessage(EnvironmentActionResult result, String successMessage, String failureMessage) {
        return result.accepted() ? successMessage : runtime.playerCodeMessage(result.code(), failureMessage);
    }

    private String biomeActionMessage(EnvironmentActionResult result, String requestedBiomeKey) {
        if (!result.accepted()) {
            return runtime.playerCodeMessage(result.code(), "섬 바이옴을 변경하지 못했습니다.");
        }
        String biomeKey = result.key().isBlank() ? requestedBiomeKey : result.key();
        if (result.code().equals("BIOME_UNCHANGED")) {
            return "이미 적용된 바이옴입니다: " + biomeKey;
        }
        return "섬 바이옴 변경 완료: " + biomeKey;
    }

    private static String borderSummary(IslandInfoView info, Map<IslandFlag, String> flags) {
        return "섬 경계: 크기=" + info.border()
            + ", 표시=" + (IslandBorderRuntimePolicy.visible(flags) ? "켜짐" : "꺼짐")
            + ", 색상=" + IslandBorderRuntimePolicy.flagValue(flags, IslandFlag.BORDER_COLOR, "blue")
            + ", 정책=" + IslandBorderRuntimePolicy.flagValue(flags, IslandFlag.BORDER_POLICY, "visible")
            + ", 경고거리=" + IslandBorderRuntimePolicy.flagValue(flags, IslandFlag.BORDER_WARNING_BLOCKS, "8");
    }

    private static String limitListMessage(List<LimitView> limits) {
        List<String> entries = limits.stream()
            .map(limit -> limit.key() + " 값=" + limit.value())
            .toList();
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

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
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

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }

    private record BorderView(IslandInfoView info, Map<IslandFlag, String> flags, IslandRegion region) {}
}

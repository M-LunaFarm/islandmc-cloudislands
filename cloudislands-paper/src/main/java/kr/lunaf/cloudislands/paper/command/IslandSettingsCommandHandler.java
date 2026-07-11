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
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandSettingsUseCase;
import kr.lunaf.cloudislands.paper.application.IslandSettingsUseCase.SettingsActionResult;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandFlagMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandSettingsCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandSettingsUseCase settingsUseCase;
    private final Runtime runtime;
    private final PlayerLocaleCache locales;

    IslandSettingsCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime, PlayerLocaleCache locales) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.settingsUseCase = new IslandSettingsUseCase(coreApiClient);
        this.runtime = runtime;
        this.locales = locales;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("public") || subcommand.equals("open") || subcommand.equals("공개")) {
            setPublicAccess(player, true);
            return true;
        }
        if (subcommand.equals("private") || subcommand.equals("close") || subcommand.equals("비공개")) {
            setPublicAccess(player, false);
            return true;
        }
        if (subcommand.equals("lock") || subcommand.equals("잠금")) {
            setLocked(player, true);
            return true;
        }
        if (subcommand.equals("unlock") || subcommand.equals("잠금해제")) {
            setLocked(player, false);
            return true;
        }
        if (subcommand.equals("settings") || subcommand.equals("setting") || subcommand.equals("설정")) {
            openSettings(player);
            return true;
        }
        if (subcommand.equals("language") || subcommand.equals("locale") || subcommand.equals("lang") || subcommand.equals("언어")) {
            if (args.length < 2) {
                runtime.message(player, message("input-locale-required", "언어 코드를 입력해주세요. 예: /섬 언어 ko_kr"));
                return true;
            }
            setPlayerLocale(player, args[1]);
            return true;
        }
        if (subcommand.equals("name") || subcommand.equals("setname") || subcommand.equals("rename") || subcommand.equals("이름") || subcommand.equals("이름설정")) {
            if (args.length < 2) {
                runtime.message(player, message("input-island-name-required", "새 섬 이름을 입력해주세요."));
                return true;
            }
            setName(player, joined(args, 1));
            return true;
        }
        if (subcommand.equals("setdiscord") || subcommand.equals("discord") || subcommand.equals("디스코드")) {
            if (args.length < 2) {
                runtime.message(player, message("input-social-value-required", "저장할 소셜 값을 입력해주세요. clear로 비울 수 있습니다."));
                return true;
            }
            setSocialFlag(player, IslandFlag.SOCIAL_DISCORD, "social-discord-action-label", "섬 Discord 설정", joined(args, 1));
            return true;
        }
        if (subcommand.equals("setpaypal") || subcommand.equals("paypal") || subcommand.equals("페이팔")) {
            if (args.length < 2) {
                runtime.message(player, message("input-social-value-required", "저장할 소셜 값을 입력해주세요. clear로 비울 수 있습니다."));
                return true;
            }
            setSocialFlag(player, IslandFlag.SOCIAL_PAYPAL, "social-paypal-action-label", "섬 PayPal 설정", joined(args, 1));
            return true;
        }
        if (subcommand.equals("fly") || subcommand.equals("비행")) {
            setFlag(player, "FLY", toggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("keepinventory") || subcommand.equals("keepinv") || subcommand.equals("인벤보존")) {
            setFlag(player, "KEEP_INVENTORY", toggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("pvp") || subcommand.equals("피빕")) {
            setFlag(player, "PVP", toggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("publicwarps") || subcommand.equals("공개워프")) {
            setFlag(player, "PUBLIC_WARPS", toggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("flags") || subcommand.equals("flag-menu") || subcommand.equals("flag") || subcommand.equals("플래그")) {
            if (args.length > 2) {
                setFlag(player, args[1], args[2]);
            } else {
                openFlagMenu(player);
            }
            return true;
        }
        if (subcommand.equals("flag-list") || subcommand.equals("플래그목록")) {
            listFlags(player);
            return true;
        }
        if (subcommand.equals("setflag") || subcommand.equals("flag-set") || subcommand.equals("플래그설정")) {
            if (args.length < 3) {
                runtime.message(player, message("input-flag-value-required", "플래그와 값을 입력해주세요."));
                return true;
            }
            setFlag(player, args[1], args[2]);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, boolean rightClick) {
        if (action instanceof GuiAction.FlagSet flagSet) {
            setFlag(player, flagSet.flag(), rightClick ? "false" : "true");
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case SETTINGS_OPEN -> {
                    openSettings(player);
                    yield true;
                }
                case PUBLIC_TOGGLE -> {
                    setPublicAccess(player, !rightClick);
                    yield true;
                }
                case LOCK_TOGGLE -> {
                    setLocked(player, rightClick);
                    yield true;
                }
                case FLAGS_OPEN -> {
                    openFlagMenu(player);
                    yield true;
                }
                case FLAGS_LIST -> {
                    listFlags(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void openSettings(Player player) {
        runtime.currentIsland(player, message("settings-menu-island-required", "섬 안에서만 설정 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandSettingsMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setPublicAccess(Player player, boolean publicAccess) {
        runtime.currentIsland(player, message("access-change-island-required", "섬 안에서만 공개 상태를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("access-change-denied", "섬 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            settingsUseCase.setPublicAccessAction(islandId, player.getUniqueId(), publicAccess, runtime::mutate)
                .thenAccept(result -> {
                    runtime.message(player, settingsActionMessage(publicAccess ? "access-public-action-label" : "access-private-action-label", publicAccess ? "섬 공개 설정" : "섬 비공개 설정", islandId.toString(), result));
                    if (result.accepted()) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, message("access-change-failed", "섬 공개 상태를 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void setLocked(Player player, boolean locked) {
        runtime.currentIsland(player, message("lock-change-island-required", "섬 안에서만 잠금 상태를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("lock-change-denied", "섬 잠금 상태를 변경할 권한이 없습니다."));
                return;
            }
            settingsUseCase.setLockedAction(islandId, player.getUniqueId(), locked, runtime::mutate)
                .thenAccept(result -> {
                    runtime.message(player, settingsActionMessage(locked ? "lock-action-label" : "unlock-action-label", locked ? "섬 잠금 설정" : "섬 잠금 해제", islandId.toString(), result));
                    if (result.accepted()) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, message("lock-change-failed", "섬 잠금 상태를 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void setName(Player player, String name) {
        runtime.currentIsland(player, message("name-change-island-required", "섬 안에서만 이름을 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("name-change-denied", "섬 이름을 변경할 권한이 없습니다."));
                return;
            }
            settingsUseCase.setNameAction(islandId, player.getUniqueId(), name, runtime::mutate)
                .thenAccept(result -> {
                    runtime.message(player, settingsActionMessage("name-change-action-label", "섬 이름 변경", name, result));
                    if (result.accepted()) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, message("name-change-failed", "섬 이름을 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void listFlags(Player player) {
        runtime.currentIsland(player, message("flag-list-island-required", "섬 안에서만 플래그를 확인할 수 있습니다.")).ifPresent(islandId -> {
            settingsUseCase.flagValues(islandId)
                .thenAccept(flags -> runtime.message(player, flagListMessage(flags)))
                .exceptionally(error -> {
                    runtime.message(player, message("flag-list-load-failed", "섬 플래그를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void openFlagMenu(Player player) {
        runtime.currentIsland(player, message("flag-menu-island-required", "섬 안에서만 플래그 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandFlagMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setFlag(Player player, String flagName, String value) {
        IslandFlag flag = islandFlag(flagName);
        if (flag == null) {
            runtime.message(player, message("input-flag-invalid", "올바른 섬 플래그를 입력해주세요."));
            return;
        }
        setFlag(player, flag, value);
    }

    private void setFlag(Player player, IslandFlag flag, String value) {
        runtime.currentIsland(player, message("flag-set-island-required", "섬 안에서만 플래그를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다."));
                return;
            }
            settingsUseCase.setFlagAction(islandId, player.getUniqueId(), flag, value, runtime::mutate)
                .thenAccept(result -> runtime.message(player, settingsActionMessage(message("flag-set-action-label", "섬 플래그 변경 ") + flag.name() + "=" + value, flag.name(), result)))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, message("flag-set-failed", "섬 플래그를 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void setSocialFlag(Player player, IslandFlag flag, String labelKey, String labelFallback, String rawValue) {
        String value = normalizeSocialValue(rawValue);
        if (value == null) {
            runtime.message(player, message("input-social-value-invalid", "소셜 값은 128자 이하의 일반 텍스트여야 합니다."));
            return;
        }
        runtime.currentIsland(player, message("social-set-island-required", "섬 안에서만 소셜 정보를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("social-set-denied", "섬 소셜 정보를 변경할 권한이 없습니다."));
                return;
            }
            settingsUseCase.setFlagAction(islandId, player.getUniqueId(), flag, value, runtime::mutate)
                .thenAccept(result -> runtime.message(player, settingsActionMessage(labelKey, labelFallback, socialActionTarget(value), result)))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, message("social-set-failed", "섬 소셜 정보를 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void setPlayerLocale(Player player, String value) {
        String locale = PlayerIslandProfile.normalizeLocale(value);
        runtime.mutate("player.locale.set", () -> coreApiClient.playerProfileCommands().setLocale(player.getUniqueId(), locale))
            .thenAccept(profile -> {
                String applied = profile.locale().isBlank() ? locale : PlayerIslandProfile.normalizeLocale(profile.locale());
                if (locales != null) {
                    locales.remember(player.getUniqueId(), applied);
                }
                runtime.message(player, message("player-locale-updated", "언어 설정을 변경했습니다.") + " locale=" + applied);
            })
            .exceptionally(error -> {
                runtime.message(player, runtime.coreWriteFailureMessage(error, message("player-locale-update-failed", "언어 설정을 변경하지 못했습니다.")));
                return null;
            });
    }

    private String flagListMessage(Map<IslandFlag, String> flags) {
        List<String> entries = flags.entrySet().stream()
            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .toList();
        return entries.isEmpty() ? message("flag-list-empty", "섬 플래그가 없습니다.") : message("flag-list-prefix", "섬 플래그: ") + String.join(", ", entries);
    }

    private String settingsActionMessage(String labelKey, String labelFallback, String targetId, SettingsActionResult result) {
        StringBuilder builder = new StringBuilder(message(labelKey, labelFallback))
            .append(' ')
            .append(result.accepted() ? message("settings-action-complete", "완료") : message("settings-action-failed", "실패"));
        if (targetId != null && !targetId.isBlank()) {
            builder.append(message("settings-action-target-prefix", ": 대상=")).append(compactId(targetId));
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(message("settings-action-reason-prefix", " 사유=")).append(result.code());
        }
        return builder.toString();
    }

    private String settingsActionMessage(String label, String targetId, SettingsActionResult result) {
        StringBuilder builder = new StringBuilder(label)
            .append(' ')
            .append(result.accepted() ? message("settings-action-complete", "완료") : message("settings-action-failed", "실패"));
        if (targetId != null && !targetId.isBlank()) {
            builder.append(message("settings-action-target-prefix", ": 대상=")).append(compactId(targetId));
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(message("settings-action-reason-prefix", " 사유=")).append(result.code());
        }
        return builder.toString();
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
    }

    private static IslandFlag islandFlag(String value) {
        try {
            return IslandFlag.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String toggleValue(String[] args, int index) {
        if (args.length <= index) {
            return "true";
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("enable") || value.equals("enabled") || value.equals("켜기") || value.equals("허용") || value.equals("활성")) {
            return "true";
        }
        if (value.equals("off") || value.equals("false") || value.equals("no") || value.equals("0") || value.equals("disable") || value.equals("disabled") || value.equals("끄기") || value.equals("거부") || value.equals("비활성")) {
            return "false";
        }
        return args[index];
    }

    private static String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private static String normalizeSocialValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("clear") || lower.equals("reset") || lower.equals("remove") || lower.equals("none") || lower.equals("삭제") || lower.equals("초기화")) {
            return "";
        }
        if (trimmed.length() > 128 || trimmed.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return trimmed;
    }

    private String socialActionTarget(String value) {
        return value == null || value.isBlank() ? message("social-value-cleared", "비움") : value;
    }

    private static String compactId(String value) {
        return value != null && value.length() == 36 && value.indexOf('-') > 0 ? value.substring(0, 8) : value;
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}

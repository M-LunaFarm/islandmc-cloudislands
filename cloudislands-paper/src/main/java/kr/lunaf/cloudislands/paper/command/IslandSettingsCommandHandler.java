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
import kr.lunaf.cloudislands.paper.PlayerIslandFlightService;
import kr.lunaf.cloudislands.paper.application.IslandSettingsUseCase.SettingsActionResult;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
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
    private final PlayerIslandFlightService flightService;

    IslandSettingsCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime, PlayerLocaleCache locales) {
        this(plugin, coreApiClient, runtime, locales, null);
    }

    IslandSettingsCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime, PlayerLocaleCache locales, PlayerIslandFlightService flightService) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.settingsUseCase = new IslandSettingsUseCase(coreApiClient);
        this.runtime = runtime;
        this.locales = locales;
        this.flightService = flightService;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("public") || subcommand.equals("open") || subcommand.equals("공개")) {
            setPublicAccess(player, true, false);
            return true;
        }
        if (subcommand.equals("private") || subcommand.equals("close") || subcommand.equals("비공개")) {
            setPublicAccess(player, false, false);
            return true;
        }
        if (subcommand.equals("lock") || subcommand.equals("잠금")) {
            setLocked(player, true, false);
            return true;
        }
        if (subcommand.equals("unlock") || subcommand.equals("잠금해제")) {
            setLocked(player, false, false);
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
        if (subcommand.equals("description") || subcommand.equals("setdescription") || subcommand.equals("desc") || subcommand.equals("설명")) {
            if (args.length < 2) {
                runtime.message(player, message("input-description-required", "섬 설명을 입력해주세요. clear로 비울 수 있습니다."));
                return true;
            }
            setProfileFlag(player, IslandFlag.PROFILE_DESCRIPTION, "description-action-label", "섬 설명 설정", joined(args, 1), 256);
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
            setPersonalFlight(player, args);
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
                    setPublicAccess(player, !rightClick, true);
                    yield true;
                }
                case LOCK_TOGGLE -> {
                    setLocked(player, rightClick, true);
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

    private void setPublicAccess(Player player, boolean publicAccess, boolean reopenSettings) {
        runtime.currentIsland(player, message("access-change-island-required", "섬 안에서만 공개 상태를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("access-change-denied", "섬 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            UUID actorUuid = player.getUniqueId();
            GuiSession session = beginSettingsMutation(player, reopenSettings);
            settingsUseCase.setPublicAccessAction(islandId, actorUuid, publicAccess, runtime::mutate)
                .thenAccept(result -> completeSettingsMutation(actorUuid, session, result,
                    settingsActionMessage(publicAccess ? "access-public-action-label" : "access-private-action-label", publicAccess ? "섬 공개 설정" : "섬 비공개 설정", islandId.toString(), result)))
                .exceptionally(error -> {
                    failSettingsMutation(actorUuid, session, error, message("access-change-failed", "섬 공개 상태를 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void setLocked(Player player, boolean locked, boolean reopenSettings) {
        runtime.currentIsland(player, message("lock-change-island-required", "섬 안에서만 잠금 상태를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("lock-change-denied", "섬 잠금 상태를 변경할 권한이 없습니다."));
                return;
            }
            UUID actorUuid = player.getUniqueId();
            GuiSession session = beginSettingsMutation(player, reopenSettings);
            settingsUseCase.setLockedAction(islandId, actorUuid, locked, runtime::mutate)
                .thenAccept(result -> completeSettingsMutation(actorUuid, session, result,
                    settingsActionMessage(locked ? "lock-action-label" : "unlock-action-label", locked ? "섬 잠금 설정" : "섬 잠금 해제", islandId.toString(), result)))
                .exceptionally(error -> {
                    failSettingsMutation(actorUuid, session, error, message("lock-change-failed", "섬 잠금 상태를 변경하지 못했습니다."));
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
            UUID actorUuid = player.getUniqueId();
            settingsUseCase.setNameAction(islandId, actorUuid, name, runtime::mutate)
                .thenAccept(result -> deliverMessage(actorUuid, settingsActionMessage("name-change-action-label", "섬 이름 변경", name, result)))
                .exceptionally(error -> {
                    deliverMessage(actorUuid, runtime.coreWriteFailureMessage(error, message("name-change-failed", "섬 이름을 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    private GuiSession beginSettingsMutation(Player player, boolean reopenSettings) {
        if (!reopenSettings) {
            return null;
        }
        return GuiStateMenus.openSaving(plugin, player, runtime.messagesFor(player), message("settings-mutation-title", "섬 설정 저장"));
    }

    private void completeSettingsMutation(UUID actorUuid, GuiSession session, SettingsActionResult result, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(actorUuid);
            if (activePlayer == null || !activePlayer.isOnline()) {
                return;
            }
            runtime.message(activePlayer, detail);
            if (session == null || !GuiSessions.isCurrent(activePlayer, session)) {
                return;
            }
            if (result.accepted()) {
                openSettings(activePlayer);
                return;
            }
            GuiStateMenus.openConflict(plugin, activePlayer, session, runtime.messagesFor(activePlayer),
                message("settings-mutation-title", "섬 설정 저장"), detail, "island.settings.open", "island.main.open");
        });
    }

    private void failSettingsMutation(UUID actorUuid, GuiSession session, Throwable error, String fallback) {
        String detail = runtime.coreWriteFailureMessage(error, fallback);
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(actorUuid);
            if (activePlayer == null || !activePlayer.isOnline()) {
                return;
            }
            runtime.message(activePlayer, detail);
            if (session != null && GuiSessions.isCurrent(activePlayer, session)) {
                GuiStateMenus.openError(plugin, activePlayer, session, runtime.messagesFor(activePlayer),
                    message("settings-mutation-title", "섬 설정 저장"), detail, "island.settings.open", "island.main.open");
            }
        });
    }

    private void deliverMessage(UUID actorUuid, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(actorUuid);
            if (activePlayer != null && activePlayer.isOnline()) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private void listFlags(Player player) {
        runtime.currentIsland(player, message("flag-list-island-required", "섬 안에서만 플래그를 확인할 수 있습니다.")).ifPresent(islandId -> {
            UUID actorUuid = player.getUniqueId();
            settingsUseCase.flagValues(islandId)
                .thenAccept(flags -> deliverMessage(actorUuid, flagListMessage(flags)))
                .exceptionally(error -> {
                    deliverMessage(actorUuid, message("flag-list-load-failed", "섬 플래그를 불러오지 못했습니다."));
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
            UUID actorUuid = player.getUniqueId();
            settingsUseCase.setFlagAction(islandId, actorUuid, flag, value, runtime::mutate)
                .thenAccept(result -> deliverMessage(actorUuid, settingsActionMessage(message("flag-set-action-label", "섬 플래그 변경 ") + flag.name() + "=" + value, flag.name(), result)))
                .exceptionally(error -> {
                    deliverMessage(actorUuid, runtime.coreWriteFailureMessage(error, message("flag-set-failed", "섬 플래그를 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void setSocialFlag(Player player, IslandFlag flag, String labelKey, String labelFallback, String rawValue) {
        setProfileFlag(player, flag, labelKey, labelFallback, rawValue, 128);
    }

    private void setProfileFlag(Player player, IslandFlag flag, String labelKey, String labelFallback, String rawValue, int maxLength) {
        String value = normalizeProfileValue(rawValue, maxLength);
        if (value == null) {
            runtime.message(player, message("input-profile-value-invalid", "프로필 값은 허용 길이 이내의 일반 텍스트여야 합니다."));
            return;
        }
        runtime.currentIsland(player, message("profile-set-island-required", "섬 안에서만 프로필 정보를 변경할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, message("profile-set-denied", "섬 프로필 정보를 변경할 권한이 없습니다."));
                return;
            }
            UUID actorUuid = player.getUniqueId();
            settingsUseCase.setFlagAction(islandId, actorUuid, flag, value, runtime::mutate)
                .thenAccept(result -> deliverMessage(actorUuid, settingsActionMessage(labelKey, labelFallback, socialActionTarget(value), result)))
                .exceptionally(error -> {
                    deliverMessage(actorUuid, runtime.coreWriteFailureMessage(error, message("profile-set-failed", "섬 프로필 정보를 변경하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void setPlayerLocale(Player player, String value) {
        UUID playerUuid = player.getUniqueId();
        String locale = PlayerIslandProfile.normalizeLocale(value);
        runtime.mutate("player.locale.set", () -> coreApiClient.playerProfileCommands().setLocale(playerUuid, locale))
            .thenAccept(profile -> deliverLocaleUpdate(playerUuid,
                profile.locale().isBlank() ? locale : PlayerIslandProfile.normalizeLocale(profile.locale())))
            .exceptionally(error -> {
                deliverMessage(playerUuid, runtime.coreWriteFailureMessage(error, message("player-locale-update-failed", "언어 설정을 변경하지 못했습니다.")));
                return null;
            });
    }

    private void deliverLocaleUpdate(UUID playerUuid, String applied) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(playerUuid);
            if (activePlayer == null || !activePlayer.isOnline()) {
                return;
            }
            if (locales != null) {
                locales.remember(playerUuid, applied);
            }
            runtime.message(activePlayer, message("player-locale-updated", "언어 설정을 변경했습니다.") + " locale=" + applied);
        });
    }

    private void setPersonalFlight(Player player, String[] args) {
        if (flightService == null) {
            runtime.message(player, message("player-flight-update-failed", "개인 비행 설정을 변경하지 못했습니다."));
            return;
        }
        UUID playerUuid = player.getUniqueId();
        if (!flightService.beginUpdate(playerUuid)) {
            runtime.message(player, message("player-flight-update-pending", "개인 비행 설정을 이미 변경하고 있습니다."));
            return;
        }
        if (args.length <= 1 && !flightService.preferenceKnown(playerUuid)) {
            coreApiClient.playerProfiles().profile(playerUuid)
                .thenAccept(profile -> PaperSchedulers.run(plugin, () -> {
                    Player activePlayer = plugin.getServer().getPlayer(playerUuid);
                    if (activePlayer == null || !activePlayer.isOnline()) {
                        flightService.finishUpdate(playerUuid);
                        return;
                    }
                    flightService.rememberPreference(playerUuid, profile.islandFlyEnabled());
                    persistPersonalFlight(activePlayer, !profile.islandFlyEnabled());
                }))
                .exceptionally(error -> {
                    PaperSchedulers.run(plugin, () -> finishFlightFailure(playerUuid, error));
                    return null;
                });
            return;
        }
        boolean current = flightService.preferenceEnabled(player);
        Boolean enabled = flightToggleValue(args, 1, current);
        if (enabled == null) {
            flightService.finishUpdate(playerUuid);
            runtime.message(player, message("input-flight-state-invalid", "비행 상태는 on 또는 off로 입력해주세요."));
            return;
        }
        persistPersonalFlight(player, enabled);
    }

    private void persistPersonalFlight(Player player, boolean enabled) {
        UUID playerUuid = player.getUniqueId();
        if (enabled && !flightService.canEnable(player)) {
            flightService.finishUpdate(playerUuid);
            runtime.message(player, message("player-flight-denied", "현재 섬에서는 개인 비행을 사용할 권한이 없거나 섬 비행이 비활성화되어 있습니다."));
            return;
        }
        runtime.mutate("player.island-fly.set", () -> coreApiClient.playerProfileCommands().setIslandFlyEnabled(playerUuid, enabled))
            .thenAccept(profile -> PaperSchedulers.run(plugin, () -> {
                Player activePlayer = plugin.getServer().getPlayer(playerUuid);
                if (activePlayer == null || !activePlayer.isOnline()) {
                    flightService.finishUpdate(playerUuid);
                    return;
                }
                boolean applied = profile.islandFlyEnabled();
                flightService.applyPreference(activePlayer, applied);
                flightService.finishUpdate(playerUuid);
                runtime.message(activePlayer, applied
                    ? message("player-flight-enabled", "개인 섬 비행을 켰습니다.")
                    : message("player-flight-disabled", "개인 섬 비행을 껐습니다."));
            }))
            .exceptionally(error -> {
                PaperSchedulers.run(plugin, () -> finishFlightFailure(playerUuid, error));
                return null;
            });
    }

    private void finishFlightFailure(UUID playerUuid, Throwable error) {
        flightService.finishUpdate(playerUuid);
        Player activePlayer = plugin.getServer().getPlayer(playerUuid);
        if (activePlayer != null && activePlayer.isOnline()) {
            runtime.message(activePlayer, runtime.coreWriteFailureMessage(error, message("player-flight-update-failed", "개인 비행 설정을 변경하지 못했습니다.")));
        }
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

    private static Boolean flightToggleValue(String[] args, int index, boolean current) {
        if (args.length <= index) {
            return !current;
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("enable") || value.equals("enabled") || value.equals("켜기") || value.equals("허용") || value.equals("활성")) {
            return true;
        }
        if (value.equals("off") || value.equals("false") || value.equals("no") || value.equals("0") || value.equals("disable") || value.equals("disabled") || value.equals("끄기") || value.equals("거부") || value.equals("비활성")) {
            return false;
        }
        return null;
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

    private static String normalizeProfileValue(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("clear") || lower.equals("reset") || lower.equals("remove") || lower.equals("none") || lower.equals("삭제") || lower.equals("초기화")) {
            return "";
        }
        if (trimmed.length() > maxLength || trimmed.chars().anyMatch(Character::isISOControl)) {
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

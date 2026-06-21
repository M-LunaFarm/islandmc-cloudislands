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
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandFlagMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandSettingsCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandSettingsCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("public") || subcommand.equals("공개")) {
            setPublicAccess(player, true);
            return true;
        }
        if (subcommand.equals("private") || subcommand.equals("비공개")) {
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
        if (subcommand.equals("name") || subcommand.equals("setname") || subcommand.equals("rename") || subcommand.equals("이름") || subcommand.equals("이름설정")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-island-name-required", "새 섬 이름을 입력해주세요."));
                return true;
            }
            setName(player, joined(args, 1));
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
                runtime.message(player, runtime.routeMessage("input-flag-value-required", "플래그와 값을 입력해주세요."));
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
        String actionId = action.actionId();
        return switch (actionId) {
            case "island.settings.open" -> {
                openSettings(player);
                yield true;
            }
            case "island.public.toggle" -> {
                setPublicAccess(player, !rightClick);
                yield true;
            }
            case "island.lock.toggle" -> {
                setLocked(player, rightClick);
                yield true;
            }
            case "island.flags.open" -> {
                openFlagMenu(player);
                yield true;
            }
            case "island.flags.list" -> {
                listFlags(player);
                yield true;
            }
            default -> false;
        };
    }

    private void openSettings(Player player) {
        runtime.currentIsland(player, "섬 안에서만 설정 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandSettingsMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setPublicAccess(Player player, boolean publicAccess) {
        runtime.currentIsland(player, "섬 안에서만 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, runtime.routeMessage("access-change-denied", "섬 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            runtime.mutate("island.public-access.set", () -> coreApiClient.setIslandPublicAccessResult(islandId, player.getUniqueId(), publicAccess))
                .thenAccept(body -> {
                    runtime.message(player, runtime.actionResultMessage(publicAccess ? "섬 공개 설정" : "섬 비공개 설정", islandId, body));
                    if (!resultRejected(body)) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setLocked(Player player, boolean locked) {
        runtime.currentIsland(player, "섬 안에서만 잠금 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, runtime.routeMessage("lock-change-denied", "섬 잠금 상태를 변경할 권한이 없습니다."));
                return;
            }
            runtime.mutate("island.locked.set", () -> coreApiClient.setIslandLockedResult(islandId, player.getUniqueId(), locked))
                .thenAccept(body -> {
                    runtime.message(player, runtime.actionResultMessage(locked ? "섬 잠금 설정" : "섬 잠금 해제", islandId, body));
                    if (!resultRejected(body)) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 잠금 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setName(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 이름을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, runtime.routeMessage("name-change-denied", "섬 이름을 변경할 권한이 없습니다."));
                return;
            }
            runtime.mutate("island.name.set", () -> coreApiClient.setIslandNameResult(islandId, player.getUniqueId(), name))
                .thenAccept(body -> {
                    runtime.message(player, runtime.actionResultMessage("섬 이름 변경", name, body));
                    if (!resultRejected(body)) {
                        PaperSchedulers.run(plugin, () -> openSettings(player));
                    }
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 이름을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listFlags(Player player) {
        runtime.currentIsland(player, "섬 안에서만 플래그를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandFlags(islandId)
                .thenAccept(body -> runtime.message(player, flagListMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 플래그를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openFlagMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 플래그 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandFlagMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void setFlag(Player player, String flagName, String value) {
        IslandFlag flag = islandFlag(flagName);
        if (flag == null) {
            runtime.message(player, runtime.routeMessage("input-flag-invalid", "올바른 섬 플래그를 입력해주세요."));
            return;
        }
        setFlag(player, flag, value);
    }

    private void setFlag(Player player, IslandFlag flag, String value) {
        runtime.currentIsland(player, "섬 안에서만 플래그를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_FLAGS)) {
                runtime.message(player, runtime.routeMessage("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다."));
                return;
            }
            runtime.mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, player.getUniqueId(), flag, value))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 플래그 변경 " + flag.name() + "=" + value, flag.name(), body)))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 플래그를 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private static String flagListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 플래그가 없습니다.";
        }
        int flagsStart = body.indexOf("\"flags\":{");
        if (flagsStart < 0) {
            return "섬 플래그가 없습니다.";
        }
        int objectStart = body.indexOf('{', flagsStart);
        int objectEnd = body.indexOf('}', objectStart);
        if (objectStart < 0 || objectEnd < 0) {
            return "섬 플래그가 없습니다.";
        }
        String flags = body.substring(objectStart + 1, objectEnd);
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < flags.length()) {
            int keyStart = flags.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = flags.indexOf('"', keyStart + 1);
            int valueStart = flags.indexOf('"', keyEnd + 1);
            int valueEnd = valueStart < 0 ? -1 : flags.indexOf('"', valueStart + 1);
            if (keyEnd < 0 || valueStart < 0 || valueEnd < 0) {
                break;
            }
            entries.add(flags.substring(keyStart + 1, keyEnd) + "=" + unescape(flags.substring(valueStart + 1, valueEnd)));
            index = valueEnd + 1;
        }
        return entries.isEmpty() ? "섬 플래그가 없습니다." : "섬 플래그: " + String.join(", ", entries);
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

    private static boolean resultRejected(String body) {
        return body == null || body.contains("\"error\"") || body.contains("\"accepted\":false") || body.contains("\"applied\":false");
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

        String actionResultMessage(String label, UUID targetId, String body);

        String actionResultMessage(String label, String targetId, String body);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}

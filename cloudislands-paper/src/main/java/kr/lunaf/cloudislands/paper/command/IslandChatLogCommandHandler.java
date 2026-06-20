package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLogMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandChatLogCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandChatLogCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("chat") || subcommand.equals("islandchat") || subcommand.equals("채팅")) {
            if (args.length < 2) {
                openChatMenu(player);
                return true;
            }
            sendChat(player, "ISLAND", joined(args, 1), "섬 채팅");
            return true;
        }
        if (subcommand.equals("chat-menu")) {
            openChatMenu(player);
            return true;
        }
        if (subcommand.equals("teamchat") || subcommand.equals("team-chat") || subcommand.equals("팀채팅")) {
            if (args.length < 2) {
                openChatMenu(player);
                return true;
            }
            sendChat(player, "TEAM", joined(args, 1), "팀 채팅");
            return true;
        }
        if (subcommand.equals("log") || subcommand.equals("log-menu") || subcommand.equals("로그")) {
            openLogMenu(player);
            return true;
        }
        if (subcommand.equals("logs") || subcommand.equals("log-list") || subcommand.equals("로그목록")) {
            listLogs(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.chat.open" -> {
                openChatMenu(player);
                yield true;
            }
            case "island.logs.open" -> {
                openLogMenu(player);
                yield true;
            }
            case "island.logs.list" -> {
                listLogs(player, 10);
                yield true;
            }
            case "island.log.detail" -> {
                runtime.message(player, runtime.routeMessage("log-menu-detail-title", "섬 로그 상세"));
                runtime.message(player, "- " + runtime.routeMessage("log-menu-action", "작업: ") + data.getOrDefault("action", "unknown"));
                runtime.message(player, "- " + runtime.routeMessage("log-menu-time", "시간: ") + data.getOrDefault("createdAt", "unknown"));
                runtime.message(player, "- " + runtime.routeMessage("log-menu-actor", "처리자: ") + data.getOrDefault("actorUuid", "unknown"));
                runtime.message(player, "- " + runtime.routeMessage("log-menu-payload", "payload: ") + data.getOrDefault("payload", runtime.routeMessage("log-menu-payload-empty", "없음")));
                yield true;
            }
            default -> false;
        };
    }

    private void openChatMenu(Player player) {
        IslandChatMenu.open(player, runtime.messagesFor(player));
    }

    private void sendChat(Player player, String channel, String chatMessage, String label) {
        runtime.currentIsland(player, "섬 안에서만 " + label + "을 사용할 수 있습니다.").ifPresent(islandId -> {
            runtime.mutate("island.chat.send", () -> coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, chatMessage))
                .thenAccept(body -> {
                    if (body == null || body.isBlank() || !body.contains("\"accepted\":true")) {
                        runtime.message(player, label + "을 전송하지 못했습니다.");
                        return;
                    }
                    runtime.message(player, label + "을 전송했습니다.");
                })
                .exceptionally(error -> {
                    runtime.message(player, label + "을 전송하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listLogs(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 로그를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandLogs(islandId, Math.max(1, Math.min(limit, 30)))
                .thenAccept(body -> runtime.message(player, logListMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 로그를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openLogMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 로그를 확인할 수 있습니다.").ifPresent(islandId -> IslandLogMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private static String logListMessage(String body) {
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
            String action = text(object, "action");
            if (!action.isBlank()) {
                String actor = text(object, "actorUuid");
                entries.add(action + (actor.isBlank() ? "" : " by " + actor));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 로그가 없습니다." : "섬 로그: " + String.join(" | ", entries);
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

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
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

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}

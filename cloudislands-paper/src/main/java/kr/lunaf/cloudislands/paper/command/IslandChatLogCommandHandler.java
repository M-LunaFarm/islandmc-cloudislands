package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LogEntryView;
import kr.lunaf.cloudislands.paper.application.IslandCommunicationUseCase;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLogMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.TeamChatModeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandChatLogCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandCommunicationUseCase communicationUseCase;
    private final Runtime runtime;
    private final TeamChatModeRegistry teamChatModes;

    IslandChatLogCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this(plugin, coreApiClient, runtime, new TeamChatModeRegistry());
    }

    IslandChatLogCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime, TeamChatModeRegistry teamChatModes) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.communicationUseCase = new IslandCommunicationUseCase(coreApiClient);
        this.runtime = runtime;
        this.teamChatModes = teamChatModes == null ? new TeamChatModeRegistry() : teamChatModes;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("chat") || subcommand.equals("islandchat") || subcommand.equals("채팅")) {
            if (args.length < 2) {
                openChatMenu(player);
                return true;
            }
            sendChat(player, "ISLAND", joined(args, 1), "chat-island-label", "섬 채팅", "chat-island-required", "섬 안에서만 섬 채팅을 사용할 수 있습니다.");
            return true;
        }
        if (subcommand.equals("chat-menu")) {
            openChatMenu(player);
            return true;
        }
        if (subcommand.equals("teamchat") || subcommand.equals("team-chat") || subcommand.equals("teamchat-toggle") || subcommand.equals("tc") || subcommand.equals("팀채팅")) {
            if (args.length < 2) {
                setTeamChatMode(player, teamChatModes.toggle(player.getUniqueId()));
                return true;
            }
            if (isTeamChatToggle(args[1])) {
                boolean enabled = args[1].equalsIgnoreCase("on") ? teamChatModes.set(player.getUniqueId(), true)
                    : args[1].equalsIgnoreCase("off") ? teamChatModes.set(player.getUniqueId(), false)
                    : teamChatModes.toggle(player.getUniqueId());
                setTeamChatMode(player, enabled);
                return true;
            }
            sendChat(player, "TEAM", joined(args, 1), "chat-team-label", "팀 채팅", "chat-team-required", "섬 안에서만 팀 채팅을 사용할 수 있습니다.");
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

    private void setTeamChatMode(Player player, boolean enabled) {
        runtime.message(player, enabled
            ? message("chat-team-mode-enabled", "팀 채팅 모드를 켰습니다. 일반 채팅이 팀 채널로 전송됩니다.")
            : message("chat-team-mode-disabled", "팀 채팅 모드를 껐습니다."));
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.ChatOpen) {
            openChatMenu(player);
            return true;
        }
        if (action instanceof GuiAction.LogsOpen) {
            openLogMenu(player);
            return true;
        }
        if (action instanceof GuiAction.LogsList) {
            listLogs(player, 10);
            return true;
        }
        if (action instanceof GuiAction.LogPage page) {
            IslandLogMenu.open(plugin, coreApiClient, player, page.islandId(), runtime.messagesFor(player), page.mode(), page.page());
            return true;
        }
        if (action instanceof GuiAction.LogDetail detail) {
            showLogDetail(player, detail.logAction(), detail.createdAt(), detail.actorUuid(), detail.payload());
            return true;
        }
        return false;
    }

    private void showLogDetail(Player player, String action, String createdAt, String actorUuid, String payload) {
        runtime.message(player, runtime.routeMessage("log-menu-detail-title", "섬 로그 상세"));
        runtime.message(player, "- " + runtime.routeMessage("log-menu-action", "작업: ") + action);
        runtime.message(player, "- " + runtime.routeMessage("log-menu-time", "시간: ") + (createdAt == null || createdAt.isBlank() ? "unknown" : createdAt));
        runtime.message(player, "- " + runtime.routeMessage("log-menu-actor", "처리자: ") + (actorUuid == null || actorUuid.isBlank() ? "unknown" : actorUuid));
        runtime.message(player, "- " + runtime.routeMessage("log-menu-payload", "payload: ") + (payload == null || payload.isBlank() ? runtime.routeMessage("log-menu-payload-empty", "없음") : payload));
    }

    private void openChatMenu(Player player) {
        IslandChatMenu.open(player, runtime.messagesFor(player));
    }

    private void sendChat(Player player, String channel, String chatMessage, String labelKey, String labelFallback, String missingKey, String missingFallback) {
        runtime.currentIsland(player, message(missingKey, missingFallback)).ifPresent(islandId -> {
            String label = message(labelKey, labelFallback);
            communicationUseCase.sendChatAction(islandId, player.getUniqueId(), channel, chatMessage, runtime::mutate)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, label + message("chat-send-failed-suffix", "을 전송하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, label + message("chat-send-success-suffix", "을 전송했습니다."));
                })
                .exceptionally(error -> {
                    runtime.message(player, label + message("chat-send-failed-suffix", "을 전송하지 못했습니다."));
                    return null;
                });
        });
    }

    private static boolean isTeamChatToggle(String value) {
        return value.equalsIgnoreCase("toggle") || value.equalsIgnoreCase("mode") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off") || value.equals("전환") || value.equals("모드");
    }

    private void listLogs(Player player, int limit) {
        runtime.currentIsland(player, message("log-list-island-required", "섬 안에서만 로그를 확인할 수 있습니다.")).ifPresent(islandId -> {
            communicationUseCase.logViews(islandId, limit)
                .thenAccept(logs -> runtime.message(player, logListMessage(logs)))
                .exceptionally(error -> {
                    runtime.message(player, message("log-list-load-failed", "섬 로그를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void openLogMenu(Player player) {
        runtime.currentIsland(player, message("log-menu-island-required", "섬 안에서만 로그를 확인할 수 있습니다.")).ifPresent(islandId -> IslandLogMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private String logListMessage(List<LogEntryView> logs) {
        List<String> entries = logs.stream()
            .filter(log -> !log.action().isBlank())
            .map(log -> log.action() + (log.actorUuid().isBlank() ? "" : message("log-list-actor-prefix", " by ") + log.actorUuid()))
            .toList();
        return entries.isEmpty() ? message("log-list-empty", "섬 로그가 없습니다.") : message("log-list-prefix", "섬 로그: ") + String.join(" | ", entries);
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
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

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}

package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CommunicationCommandClient;
import kr.lunaf.cloudislands.coreclient.CommunicationQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.TeamChatModeRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class IslandChatLogCommandHandlerTest {
    @Test
    void logActorUsesProfileNameWithCompactUuidFallback() {
        assertEquals("IslandOwner", IslandChatLogCommandHandler.actorDisplay("11111111-1111-1111-1111-111111111111", " IslandOwner "));
        assertEquals("11111111", IslandChatLogCommandHandler.actorDisplay("11111111-1111-1111-1111-111111111111", ""));
        assertEquals("unknown", IslandChatLogCommandHandler.actorDisplay("", ""));
    }

    @Test
    void explicitLocalChatModesDoNotSendTheirArgumentsAsMessages() {
        UUID playerUuid = UUID.randomUUID();
        TeamChatModeRegistry modes = new TeamChatModeRegistry();
        TestRuntime runtime = new TestRuntime(Optional.of(UUID.randomUUID()));
        IslandChatLogCommandHandler handler = new IslandChatLogCommandHandler(null, coreClient(), runtime, modes);
        Player player = player(playerUuid);

        assertTrue(handler.handleCommand(player, "localchat", new String[]{"localchat", "on"}));
        assertTrue(modes.islandEnabled(playerUuid));
        assertTrue(runtime.messages.getLast().contains("켰습니다"));

        assertTrue(handler.handleCommand(player, "localchat", new String[]{"localchat", "off"}));
        assertFalse(modes.islandEnabled(playerUuid));
        assertTrue(runtime.messages.getLast().contains("껐습니다"));
    }

    @Test
    void localChatCannotBeEnabledOutsideAnIslandButCanAlwaysBeDisabled() {
        UUID playerUuid = UUID.randomUUID();
        TeamChatModeRegistry modes = new TeamChatModeRegistry();
        TestRuntime runtime = new TestRuntime(Optional.empty());
        IslandChatLogCommandHandler handler = new IslandChatLogCommandHandler(null, coreClient(), runtime, modes);
        Player player = player(playerUuid);

        handler.handleCommand(player, "localchat", new String[]{"localchat", "켜기"});
        assertFalse(modes.islandEnabled(playerUuid));

        modes.setIsland(playerUuid, true);
        handler.handleCommand(player, "localchat", new String[]{"localchat", "끄기"});
        assertFalse(modes.islandEnabled(playerUuid));
    }

    @Test
    void teamChatCannotBeEnabledOutsideAnIslandButCanAlwaysBeDisabled() {
        UUID playerUuid = UUID.randomUUID();
        TeamChatModeRegistry modes = new TeamChatModeRegistry();
        TestRuntime runtime = new TestRuntime(Optional.empty());
        IslandChatLogCommandHandler handler = new IslandChatLogCommandHandler(null, coreClient(), runtime, modes);
        Player player = player(playerUuid);

        handler.handleCommand(player, "teamchat", new String[]{"teamchat"});
        assertFalse(modes.enabled(playerUuid));

        handler.handleCommand(player, "teamchat", new String[]{"teamchat", "on"});
        assertFalse(modes.enabled(playerUuid));

        modes.set(playerUuid, true);
        handler.handleCommand(player, "teamchat", new String[]{"teamchat", "끄기"});
        assertFalse(modes.enabled(playerUuid));
    }

    @Test
    void teamChatCanBeEnabledWhenThePlayerIsOnAnIsland() {
        UUID playerUuid = UUID.randomUUID();
        TeamChatModeRegistry modes = new TeamChatModeRegistry();
        TestRuntime runtime = new TestRuntime(Optional.of(UUID.randomUUID()));
        IslandChatLogCommandHandler handler = new IslandChatLogCommandHandler(null, coreClient(), runtime, modes);
        Player player = player(playerUuid);

        handler.handleCommand(player, "teamchat", new String[]{"teamchat", "켜기"});
        assertTrue(modes.enabled(playerUuid));
        assertTrue(runtime.messages.getLast().contains("켰습니다"));
    }

    private static CoreApiClient coreClient() {
        CommunicationQueryClient queries = proxy(CommunicationQueryClient.class);
        CommunicationCommandClient commands = proxy(CommunicationCommandClient.class);
        return (CoreApiClient) Proxy.newProxyInstance(CoreApiClient.class.getClassLoader(), new Class<?>[]{CoreApiClient.class}, (_proxy, method, _args) -> switch (method.getName()) {
            case "communication" -> queries;
            case "communicationCommands" -> commands;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Player player(UUID playerUuid) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (_proxy, method, _args) -> switch (method.getName()) {
            case "getUniqueId" -> playerUuid;
            case "isOnline" -> true;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (_proxy, method, _args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestRuntime implements IslandChatLogCommandHandler.Runtime {
        private final Optional<UUID> currentIsland;
        private final List<String> messages = new ArrayList<>();

        private TestRuntime(Optional<UUID> currentIsland) {
            this.currentIsland = currentIsland;
        }

        @Override
        public Optional<UUID> currentIsland(Player player, String missingMessage) {
            return currentIsland;
        }

        @Override
        public void message(Player player, String message) {
            messages.add(message);
        }

        @Override
        public String routeMessage(String key, String fallback) {
            return fallback;
        }

        @Override
        public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
            return operation.get();
        }

        @Override
        public MessageRenderer messagesFor(Player player) {
            return null;
        }
    }
}

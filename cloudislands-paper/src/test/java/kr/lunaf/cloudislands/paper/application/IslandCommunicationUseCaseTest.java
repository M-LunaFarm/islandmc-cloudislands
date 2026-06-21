package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreCommunicationCommandClient;
import kr.lunaf.cloudislands.coreclient.CoreCommunicationQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LogEntryView;
import kr.lunaf.cloudislands.paper.application.IslandCommunicationUseCase.ChatActionResult;
import org.junit.jupiter.api.Test;

class IslandCommunicationUseCaseTest {
    @Test
    void chatAndLogOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandCommunicationUseCase useCase = new IslandCommunicationUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        ChatActionResult chat = useCase.sendChatAction(islandId, actorUuid, "team", " hello ", mutationRunner(calls)).join();
        assertEquals(true, chat.accepted());
        assertEquals("CHAT_SENT", chat.code());
        List<LogEntryView> logs = useCase.logViews(islandId, 500).join();
        assertEquals("CREATE", logs.get(0).action());
        assertEquals("00000000-0000-0000-0000-000000000001", logs.get(0).actorUuid());

        assertEquals(List.of(
            "audit:island.chat.send",
            "sendIslandChat:TEAM:hello",
            "listIslandLogs:30"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
	            new Class<?>[] {CoreApiClient.class},
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "communication" -> new CoreCommunicationQueryClient((CoreApiClient) _proxy);
	                case "communicationCommands" -> new CoreCommunicationCommandClient((CoreApiClient) _proxy);
	                case "sendIslandChat" -> {
                    calls.add("sendIslandChat:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true}");
                }
                case "listIslandLogs" -> {
                    calls.add("listIslandLogs:" + args[1]);
                    yield CompletableFuture.completedFuture("{\"logs\":[{\"actorUuid\":\"00000000-0000-0000-0000-000000000001\",\"action\":\"CREATE\",\"createdAt\":\"now\",\"payload\":{\"target\":\"island\"}}]}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandCommunicationUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandCommunicationUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}

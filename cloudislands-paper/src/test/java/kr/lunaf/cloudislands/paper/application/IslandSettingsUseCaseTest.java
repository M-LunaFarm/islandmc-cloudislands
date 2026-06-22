package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.coreclient.IslandSettingsCommandClient;
import kr.lunaf.cloudislands.coreclient.SettingsActionView;
import org.junit.jupiter.api.Test;

class IslandSettingsUseCaseTest {
    @Test
    void settingsOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandSettingsUseCase useCase = new IslandSettingsUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000040");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        Map<IslandFlag, String> flags = useCase.flagValues(islandId).join();
        assertEquals("true", flags.get(IslandFlag.PVP));
        assertEquals("PUBLIC_ACCESS_ENABLED", useCase.setPublicAccessAction(islandId, actorUuid, true, mutationRunner(calls)).join().code());
        assertEquals("ISLAND_UNLOCKED", useCase.setLockedAction(islandId, actorUuid, false, mutationRunner(calls)).join().code());
        assertEquals("ISLAND_RENAMED", useCase.setNameAction(islandId, actorUuid, "My Island", mutationRunner(calls)).join().code());
        assertEquals("FLAG_SET", useCase.setFlagAction(islandId, actorUuid, IslandFlag.PVP, "false", mutationRunner(calls)).join().code());

        assertEquals(List.of(
            "listIslandFlags",
            "audit:island.public-access.set",
            "setPublicAccess:true",
            "audit:island.locked.set",
            "setLocked:false",
            "audit:island.name.set",
            "setName:My Island",
            "audit:island.flag.set",
            "setFlag:PVP:false"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
	            new Class<?>[] {CoreApiClient.class, IslandEnvironmentQueryClient.class, IslandSettingsCommandClient.class},
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "environment" -> (IslandEnvironmentQueryClient) _proxy;
	                case "settingsCommands" -> (IslandSettingsCommandClient) _proxy;
	                case "setPublicAccess" -> {
                    calls.add("setPublicAccess:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "PUBLIC_ACCESS_ENABLED"));
                }
                case "setLocked" -> {
                    calls.add("setLocked:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "ISLAND_UNLOCKED"));
                }
                case "setName" -> {
                    calls.add("setName:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "ISLAND_RENAMED"));
                }
                case "flags" -> {
                    calls.add("listIslandFlags");
                    EnumMap<IslandFlag, String> flags = new EnumMap<>(IslandFlag.class);
                    flags.put(IslandFlag.PVP, "true");
                    flags.put(IslandFlag.FLY, "false");
                    yield CompletableFuture.completedFuture(new IslandFlagsSnapshot(uuid("00000000-0000-0000-0000-000000000040"), flags));
                }
                case "flagValues" -> {
                    calls.add("listIslandFlags");
                    EnumMap<IslandFlag, String> flags = new EnumMap<>(IslandFlag.class);
                    flags.put(IslandFlag.PVP, "true");
                    flags.put(IslandFlag.FLY, "false");
                    yield CompletableFuture.completedFuture(flags);
                }
                case "setFlag" -> {
                    calls.add("setFlag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "FLAG_SET"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandSettingsUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandSettingsUseCase.MutationRunner() {
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

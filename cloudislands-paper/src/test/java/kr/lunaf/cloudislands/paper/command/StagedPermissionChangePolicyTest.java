package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionChange;
import org.junit.jupiter.api.Test;

class StagedPermissionChangePolicyTest {
    @Test
    void removesOnlyValuesThatWereActuallySaved() {
        PermissionChange savedBuild = new PermissionChange("MEMBER", IslandPermission.BUILD, true, "v1");
        PermissionChange newerBuild = new PermissionChange("MEMBER", IslandPermission.BUILD, false, "v2");
        PermissionChange savedContainer = new PermissionChange("MEMBER", IslandPermission.OPEN_CONTAINER, true, "v1");
        Map<String, PermissionChange> current = new ConcurrentHashMap<>(Map.of(
            newerBuild.key(), newerBuild,
            savedContainer.key(), savedContainer
        ));

        StagedPermissionChangePolicy.removeSaved(current, current, List.of(savedBuild, savedContainer));

        assertEquals(newerBuild, current.get(newerBuild.key()));
        assertFalse(current.containsKey(savedContainer.key()));
    }

    @Test
    void doesNotClearEqualChangesFromReplacementConnectionSession() {
        PermissionChange savedBuild = new PermissionChange("MEMBER", IslandPermission.BUILD, true, "v1");
        Map<String, PermissionChange> originalSession = new ConcurrentHashMap<>(Map.of(savedBuild.key(), savedBuild));
        Map<String, PermissionChange> replacementSession = new ConcurrentHashMap<>(Map.of(savedBuild.key(), savedBuild));

        StagedPermissionChangePolicy.removeSaved(replacementSession, originalSession, List.of(savedBuild));

        assertEquals(savedBuild, replacementSession.get(savedBuild.key()));
    }
}

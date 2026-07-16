package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionChange;

final class StagedPermissionChangePolicy {
    private StagedPermissionChangePolicy() {
    }

    static void removeSaved(Map<String, PermissionChange> current, Map<String, PermissionChange> stagedSession, List<PermissionChange> saved) {
        if (current == null || current != stagedSession || current.isEmpty() || saved == null || saved.isEmpty()) {
            return;
        }
        saved.forEach(change -> current.remove(change.key(), change));
    }
}

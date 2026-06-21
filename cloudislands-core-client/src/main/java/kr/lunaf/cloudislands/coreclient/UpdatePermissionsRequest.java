package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public record UpdatePermissionsRequest(
    UUID islandId,
    UUID actorUuid,
    List<Change> changes
) {
    public UpdatePermissionsRequest {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public record Change(RoleId roleId, IslandPermission permission, boolean allowed, String expectedVersion) {
        public Change {
            if (roleId == null) {
                throw new IllegalArgumentException("roleId is required");
            }
            if (permission == null) {
                throw new IllegalArgumentException("permission is required");
            }
            expectedVersion = expectedVersion == null ? "" : expectedVersion.trim();
        }

        public Change(String roleKey, IslandPermission permission, boolean allowed, String expectedVersion) {
            this(RoleId.of(roleKey), permission, allowed, expectedVersion);
        }
    }
}

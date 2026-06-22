package kr.lunaf.cloudislands.api.model;

/**
 * Legacy fixed role adapter.
 *
 * <p>Use {@link RoleId}, {@link RoleDefinition}, and {@link SystemRole} for new role APIs so custom
 * island roles can move through API, Core, GUI, and command surfaces without enum changes.</p>
 */
@Deprecated(since = "0.2", forRemoval = false)
public enum IslandRole {
    OWNER,
    CO_OWNER,
    MODERATOR,
    MEMBER,
    TRUSTED,
    VISITOR,
    BANNED;

    public boolean islandMemberRole() {
        return this != VISITOR && this != BANNED;
    }
}

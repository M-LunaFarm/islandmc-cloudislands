package kr.lunaf.cloudislands.api.model;

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

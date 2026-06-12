package kr.lunaf.cloudislands.api.model;

public enum IslandRole {
    OWNER,
    CO_OWNER,
    MODERATOR,
    MEMBER,
    TRUSTED,
    CUSTOM_1,
    CUSTOM_2,
    CUSTOM_3,
    CUSTOM_4,
    CUSTOM_5,
    VISITOR,
    BANNED;

    public boolean islandMemberRole() {
        return this != VISITOR && this != BANNED;
    }
}

package kr.lunaf.cloudislands.api.model;

public enum IslandRole {
    OWNER,
    CO_OWNER,
    MODERATOR,
    MEMBER,
    TRUSTED,
    @Deprecated(forRemoval = false, since = "1.0.0")
    CUSTOM_1,
    @Deprecated(forRemoval = false, since = "1.0.0")
    CUSTOM_2,
    @Deprecated(forRemoval = false, since = "1.0.0")
    CUSTOM_3,
    @Deprecated(forRemoval = false, since = "1.0.0")
    CUSTOM_4,
    @Deprecated(forRemoval = false, since = "1.0.0")
    CUSTOM_5,
    VISITOR,
    BANNED;

    public boolean islandMemberRole() {
        return this != VISITOR && this != BANNED;
    }
}

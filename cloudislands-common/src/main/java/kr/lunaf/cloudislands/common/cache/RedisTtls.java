package kr.lunaf.cloudislands.common.cache;

public final class RedisTtls {
    public static final long SERVER_HEARTBEAT_MILLIS = 5_000L;
    public static final long ROUTE_TICKET_MILLIS = 30_000L;
    public static final long PLAYER_ISLAND_MILLIS = 300_000L;
    public static final long PLAYER_PROFILE_MILLIS = 300_000L;
    public static final long ISLAND_SUMMARY_MILLIS = 60_000L;
    public static final long ISLAND_METADATA_MILLIS = 60_000L;
    public static final long ISLAND_RUNTIME_MILLIS = 30_000L;
    public static final long ISLAND_PERMISSIONS_MILLIS = 30_000L;
    public static final long LOCK_MIN_MILLIS = 10_000L;
    public static final long LOCK_MAX_MILLIS = 60_000L;

    private RedisTtls() {}
}

package kr.lunaf.cloudislands.common.security;

public record FencingToken(long value) implements Comparable<FencingToken> {
    public static final String WRITE_POLICY = "current-fencing-token-required-before-snapshot-or-runtime-write";
    public static final String NEXT_TOKEN_POLICY = "new-runtime-owner-uses-current-token-plus-one-after-row-lock";
    public static final String REDIS_LOCK_POLICY = "redis-lock-is-advisory-postgresql-row-lock-and-fencing-token-are-authoritative";

    public FencingToken next() {
        return new FencingToken(value + 1L);
    }

    public boolean accepts(FencingToken presented) {
        return presented != null && presented.value == value;
    }

    public boolean stale(FencingToken presented) {
        return presented == null || presented.value < value;
    }

    public String writeDecision(FencingToken presented) {
        if (accepts(presented)) {
            return "ALLOW_CURRENT_OWNER";
        }
        if (stale(presented)) {
            return "DENY_STALE_OWNER";
        }
        return "DENY_FUTURE_TOKEN_WITHOUT_ROW_LOCK";
    }

    public static FencingToken initial() {
        return new FencingToken(0L);
    }

    public static FencingToken nextAfter(FencingToken current) {
        return current == null ? new FencingToken(1L) : current.next();
    }

    @Override
    public int compareTo(FencingToken other) {
        return Long.compare(value, other.value);
    }
}

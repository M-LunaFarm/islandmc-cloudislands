package kr.lunaf.cloudislands.coreclient;

public record MemberCursor(int offset, int limit) {
    public MemberCursor {
        offset = Math.max(0, offset);
        limit = limit <= 0 ? 45 : Math.min(limit, 200);
    }

    public static MemberCursor firstPage(int limit) {
        return new MemberCursor(0, limit);
    }
}

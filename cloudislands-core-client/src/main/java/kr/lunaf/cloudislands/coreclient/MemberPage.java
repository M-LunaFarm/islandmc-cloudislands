package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record MemberPage(
    List<CoreGuiViews.MemberView> members,
    MemberCursor cursor,
    MemberCursor nextCursor,
    int total
) {
    public MemberPage {
        members = members == null ? List.of() : List.copyOf(members);
        cursor = cursor == null ? MemberCursor.firstPage(45) : cursor;
    }

    public boolean hasNext() {
        return nextCursor != null;
    }
}

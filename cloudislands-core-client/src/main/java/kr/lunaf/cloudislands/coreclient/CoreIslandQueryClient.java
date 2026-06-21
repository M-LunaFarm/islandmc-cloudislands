package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreIslandQueryClient implements IslandQueryClient {
    private final CoreApiClient delegate;

    public CoreIslandQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandInfo(delegate, islandId);
    }

    @Override
    public CompletableFuture<MemberPage> listMembers(UUID islandId, MemberCursor cursor) {
        requireIsland(islandId);
        MemberCursor safeCursor = cursor == null ? MemberCursor.firstPage(45) : cursor;
        return CoreGuiViews.islandMembers(delegate, islandId).thenApply(members -> page(members, safeCursor));
    }

    private static MemberPage page(List<CoreGuiViews.MemberView> members, MemberCursor cursor) {
        List<CoreGuiViews.MemberView> safeMembers = members == null ? List.of() : members;
        int total = safeMembers.size();
        int from = Math.min(cursor.offset(), total);
        int to = Math.min(from + cursor.limit(), total);
        MemberCursor next = to < total ? new MemberCursor(to, cursor.limit()) : null;
        return new MemberPage(safeMembers.subList(from, to), cursor, next, total);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}

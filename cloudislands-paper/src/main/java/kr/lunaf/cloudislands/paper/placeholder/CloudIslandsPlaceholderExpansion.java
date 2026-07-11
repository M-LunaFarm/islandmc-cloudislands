package kr.lunaf.cloudislands.paper.placeholder;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

public final class CloudIslandsPlaceholderExpansion extends PlaceholderExpansion {
    private static final long CACHE_TTL_MILLIS = 15_000L;
    private static final long MISS_TTL_MILLIS = 5_000L;
    private static final long STALE_RETENTION_MILLIS = 300_000L;
    private static final long CACHE_MAINTENANCE_INTERVAL_MILLIS = 30_000L;
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final Plugin plugin;
    private final CoreApiClient client;
    private final BoundedStaleCache<UUID, Snapshot> cache = new BoundedStaleCache<>(
        MAX_CACHE_ENTRIES,
        STALE_RETENTION_MILLIS,
        CACHE_MAINTENANCE_INTERVAL_MILLIS,
        Snapshot::expiresAtMillis
    );
    private final java.util.Set<UUID> refreshing = ConcurrentHashMap.newKeySet();
    private final Object rankingLock = new Object();
    private volatile RankingCache rankingCache;

    public CloudIslandsPlaceholderExpansion(Plugin plugin, CoreApiClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cloudislands";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Snapshot snapshot = cache.get(playerUuid);
        if (snapshot == null || snapshot.expiresAtMillis() < now) {
            refresh(playerUuid);
        }
        if (snapshot == null) {
            return "";
        }
        return value(snapshot, params);
    }

    private void refresh(UUID playerUuid) {
        if (!refreshing.add(playerUuid)) {
            return;
        }
        CompletableFuture<kr.lunaf.cloudislands.coreclient.PlayerProfileView> profile = client.playerProfiles().profile(playerUuid).exceptionally(_error -> null);
        CompletableFuture<List<CoreGuiViews.PlayerIslandView>> memberships = client.navigation().playerIslands(playerUuid).exceptionally(_error -> List.of());
        profile.thenCombine(memberships, (playerProfile, islands) -> selectIsland(playerUuid, playerProfile, islands))
            .thenCompose(selection -> selection == null
                ? CompletableFuture.completedFuture(Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS))
                : client.islands().getIsland(selection.islandId()).thenCompose(island -> snapshotWithDetails(playerUuid, selection.role(), island)))
            .handle((snapshot, error) -> {
                long now = System.currentTimeMillis();
                if (error == null) {
                    cache.put(playerUuid, snapshot, now);
                } else {
                    Snapshot stale = cache.get(playerUuid);
                    cache.put(playerUuid, stale == null ? Snapshot.empty(now + MISS_TTL_MILLIS) : stale.retryAfter(now + MISS_TTL_MILLIS), now);
                }
                return null;
            })
            .whenComplete((_result, _error) -> refreshing.remove(playerUuid));
    }

    private SelectedIsland selectIsland(UUID playerUuid, kr.lunaf.cloudislands.coreclient.PlayerProfileView profile, List<CoreGuiViews.PlayerIslandView> islands) {
        if (islands == null || islands.isEmpty()) {
            return null;
        }
        String primaryIslandId = profile == null ? "" : profile.primaryIslandId();
        CoreGuiViews.PlayerIslandView selected = islands.stream()
            .filter(island -> island != null && island.islandId() != null && !island.islandId().isBlank())
            .min(Comparator
                .comparingInt((CoreGuiViews.PlayerIslandView island) -> island.islandId().equals(primaryIslandId) ? 0 : teamRole(island.role()) ? 1 : 2)
                .thenComparing(CoreGuiViews.PlayerIslandView::islandId))
            .orElse(null);
        UUID islandId = selected == null ? null : uuid(selected.islandId());
        return islandId == null ? null : new SelectedIsland(islandId, normalizedRole(selected.role()));
    }

    private CompletableFuture<Snapshot> snapshotWithDetails(UUID playerUuid, String selectedRole, CoreGuiViews.IslandInfoView island) {
        long expiresAt = System.currentTimeMillis() + CACHE_TTL_MILLIS;
        String islandId = island == null ? "" : island.islandId();
        if (islandId == null || islandId.isBlank()) {
            return CompletableFuture.completedFuture(Snapshot.empty(System.currentTimeMillis() + MISS_TTL_MILLIS));
        }
        UUID parsedIslandId = uuid(islandId);
        if (parsedIslandId == null) {
            return CompletableFuture.completedFuture(new Snapshot(island, null, selectedRole, List.of(), 3L, 8L, 0, 0, expiresAt));
        }
        CompletableFuture<CoreGuiViews.BankView> bank = client.bank().islandBank(parsedIslandId).exceptionally(_error -> null);
        CompletableFuture<List<CoreGuiViews.MemberView>> members = client.islands().listMembers(parsedIslandId).exceptionally(_error -> List.of());
        CompletableFuture<List<CoreGuiViews.LimitView>> limits = client.environment().limitViews(parsedIslandId).exceptionally(_error -> List.of());
        CompletableFuture<CoreGuiViews.RankingData> rankings = rankings();
        return CompletableFuture.allOf(bank, members, limits, rankings).thenApply(_ignored -> {
            List<CoreGuiViews.MemberView> memberValues = members.join();
            String role = memberValues.stream()
                .filter(member -> playerUuid.toString().equals(member.playerUuid()))
                .map(CoreGuiViews.MemberView::role)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(CloudIslandsPlaceholderExpansion::normalizedRole)
                .orElseGet(() -> island.ownerUuid().equals(playerUuid.toString()) ? "OWNER" : selectedRole);
            long memberLimit = limit(limits.join(), "MEMBERS", 3L);
            long coopLimit = limit(limits.join(), "ROLE_LIMIT:TRUSTED", 8L);
            CoreGuiViews.RankingData rankingValues = rankings.join();
            return new Snapshot(island, bank.join(), role, memberValues, memberLimit, coopLimit,
                CloudIslandsPlaceholderRanks.worthRank(rankingValues, islandId),
                CloudIslandsPlaceholderRanks.levelRank(rankingValues, islandId), expiresAt);
        });
    }

    private CompletableFuture<CoreGuiViews.RankingData> rankings() {
        long now = System.currentTimeMillis();
        RankingCache current = rankingCache;
        if (current != null && current.expiresAtMillis() > now) {
            return current.value();
        }
        synchronized (rankingLock) {
            current = rankingCache;
            if (current != null && current.expiresAtMillis() > now) {
                return current.value();
            }
            CompletableFuture<CoreGuiViews.RankingData> loaded = client.progression().rankings(100).exceptionally(_error -> null);
            rankingCache = new RankingCache(loaded, now + CACHE_TTL_MILLIS);
            return loaded;
        }
    }

    private String value(Snapshot snapshot, String params) {
        CoreGuiViews.IslandInfoView island = snapshot.island();
        CoreGuiViews.BankView bank = snapshot.bank();
        List<CloudIslandsPlaceholderValues.Member> members = snapshot.members().stream()
            .map(member -> new CloudIslandsPlaceholderValues.Member(member.playerUuid(), member.playerName(), member.role()))
            .toList();
        CloudIslandsPlaceholderValues.Data data = island == null ? null : new CloudIslandsPlaceholderValues.Data(
            island.islandId(), island.name(), island.ownerUuid(), island.state(), island.size(), island.border(), island.level(),
            island.worth(), island.publicAccess(), island.locked(), island.createdAt(), island.updatedAt(),
            bank == null ? "" : bank.balance(), snapshot.role(), members, snapshot.memberLimit(), snapshot.coopLimit(),
            snapshot.worthRank(), snapshot.levelRank());
        return CloudIslandsPlaceholderValues.value(data, params);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static long limit(List<CoreGuiViews.LimitView> limits, String key, long fallback) {
        if (limits == null) {
            return fallback;
        }
        return limits.stream().filter(limit -> key.equalsIgnoreCase(limit.key())).mapToLong(CoreGuiViews.LimitView::value).findFirst().orElse(fallback);
    }

    private static String normalizedRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean teamRole(String role) {
        String normalized = normalizedRole(role);
        return !normalized.isBlank() && !"TRUSTED".equals(normalized);
    }

    private record SelectedIsland(UUID islandId, String role) {
    }

    private record RankingCache(CompletableFuture<CoreGuiViews.RankingData> value, long expiresAtMillis) {
    }

    private record Snapshot(CoreGuiViews.IslandInfoView island, CoreGuiViews.BankView bank, String role,
                            List<CoreGuiViews.MemberView> members, long memberLimit, long coopLimit,
                            int worthRank, int levelRank, long expiresAtMillis) {
        private static Snapshot empty(long expiresAtMillis) {
            return new Snapshot(null, null, "", List.of(), 3L, 8L, 0, 0, expiresAtMillis);
        }

        private Snapshot retryAfter(long retryAtMillis) {
            return new Snapshot(island, bank, role, members, memberLimit, coopLimit, worthRank, levelRank, retryAtMillis);
        }
    }
}

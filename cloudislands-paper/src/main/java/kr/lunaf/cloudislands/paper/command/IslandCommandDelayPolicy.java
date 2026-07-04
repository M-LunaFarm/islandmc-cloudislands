package kr.lunaf.cloudislands.paper.command;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class IslandCommandDelayPolicy {
    static final String BYPASS_COOLDOWN_PERMISSION = "cloudislands.bypass.cooldown";
    static final String BYPASS_WARMUP_PERMISSION = "cloudislands.bypass.warmup";
    static final String COOLDOWN_MESSAGE_KEY = "island-command-cooldown";
    static final String WARMUP_MESSAGE_KEY = "island-command-warmup";

    private static final Map<DelaySubject, Long> COOLDOWNS_MILLIS = new EnumMap<>(DelaySubject.class);
    private static final Map<DelaySubject, Long> WARMUPS_MILLIS = new EnumMap<>(DelaySubject.class);

    static {
        COOLDOWNS_MILLIS.put(DelaySubject.CREATE, 10_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.HOME, 3_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.VISIT, 5_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.DELETE, 10_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.RESET, 10_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.SNAPSHOT, 10_000L);
        COOLDOWNS_MILLIS.put(DelaySubject.RESTORE, 10_000L);

        WARMUPS_MILLIS.put(DelaySubject.HOME, 3_000L);
        WARMUPS_MILLIS.put(DelaySubject.VISIT, 3_000L);
        WARMUPS_MILLIS.put(DelaySubject.CREATE, 2_000L);
    }

    private final Map<UUID, Map<DelaySubject, Long>> nextAllowedAt = new ConcurrentHashMap<>();

    Decision evaluate(UUID playerUuid, String subcommand, boolean bypassCooldown, boolean bypassWarmup, long nowMillis) {
        DelaySubject subject = DelaySubject.fromSubcommand(subcommand);
        if (playerUuid == null || subject == null) {
            return Decision.allowed(subject, false, 0L);
        }
        long cooldownMillis = COOLDOWNS_MILLIS.getOrDefault(subject, 0L);
        long warmupMillis = WARMUPS_MILLIS.getOrDefault(subject, 0L);
        if (!bypassCooldown && cooldownMillis > 0L) {
            long next = nextAllowedAt
                .computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
                .getOrDefault(subject, 0L);
            if (next > nowMillis) {
                return Decision.blocked(subject, Math.max(1L, (next - nowMillis + 999L) / 1000L));
            }
            nextAllowedAt.get(playerUuid).put(subject, nowMillis + cooldownMillis);
        }
        return Decision.allowed(subject, !bypassWarmup && warmupMillis > 0L, Math.max(1L, warmupMillis / 1000L));
    }

    void clear(UUID playerUuid) {
        if (playerUuid != null) {
            nextAllowedAt.remove(playerUuid);
        }
    }

    enum DelaySubject {
        CREATE("create", "생성"),
        HOME("home", "홈"),
        VISIT("visit", "randomvisit", "random-visit", "방문", "랜덤방문"),
        DELETE("delete", "삭제"),
        RESET("reset", "리셋"),
        SNAPSHOT("snapshot", "snapshot-create", "snapshot-request", "스냅샷", "스냅샷생성"),
        RESTORE("restore", "snapshot-restore", "rollback", "복원", "스냅샷복원", "롤백");

        private final Set<String> aliases;

        DelaySubject(String... aliases) {
            this.aliases = Set.of(aliases);
        }

        static DelaySubject fromSubcommand(String subcommand) {
            String normalized = subcommand == null ? "" : subcommand.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                return null;
            }
            for (DelaySubject subject : values()) {
                if (subject.aliases.contains(normalized)) {
                    return subject;
                }
            }
            return null;
        }
    }

    record Decision(boolean allowed, DelaySubject subject, boolean warmupRequired, long secondsRemaining) {
        static Decision allowed(DelaySubject subject, boolean warmupRequired, long warmupSeconds) {
            return new Decision(true, subject, warmupRequired, warmupSeconds);
        }

        static Decision blocked(DelaySubject subject, long cooldownSeconds) {
            return new Decision(false, subject, false, cooldownSeconds);
        }
    }
}

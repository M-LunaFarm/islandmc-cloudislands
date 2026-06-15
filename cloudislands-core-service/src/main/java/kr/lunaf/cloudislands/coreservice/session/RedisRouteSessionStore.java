package kr.lunaf.cloudislands.coreservice.session;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class RedisRouteSessionStore implements RouteSessionStore {
    private final URI redisUri;
    private final InMemoryRouteSessionStore fallback = new InMemoryRouteSessionStore(Clock.systemUTC());
    private final AtomicLong failures = new AtomicLong();

    public RedisRouteSessionStore(URI redisUri) {
        this.redisUri = redisUri;
    }

    @Override
    public PlayerRouteSession put(RouteTicket ticket) {
        PlayerRouteSession session = new PlayerRouteSession(
            ticket.playerUuid(),
            ticket.ticketId(),
            ticket.targetNode(),
            ticket.payload().getOrDefault("targetServerName", ticket.targetNode()),
            ticket.nonce(),
            ticket.expiresAt()
        );
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            Optional<PlayerRouteSession> current = findAny(ticket.playerUuid());
            if (current.isPresent() && current.get().expiresAt().isAfter(session.expiresAt())) {
                return current.get();
            }
            long ttlMillis = Math.max(1L, session.expiresAt().toEpochMilli() - System.currentTimeMillis());
            redis.command("SET", key(ticket.playerUuid()), encode(session), "PX", Long.toString(ttlMillis));
            redis.command("SET", legacyKey(ticket.playerUuid()), encode(session), "PX", Long.toString(ttlMillis));
            fallback.put(ticket);
            return session;
        } catch (IOException | RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.put(ticket);
        }
    }

    @Override
    public Optional<PlayerRouteSession> find(UUID playerUuid, String nodeId) {
        Optional<PlayerRouteSession> session = findAny(playerUuid);
        if (session.isEmpty() || !session.get().targetNode().equals(nodeId)) {
            return Optional.empty();
        }
        return session;
    }

    @Override
    public Optional<PlayerRouteSession> consume(UUID playerUuid, String nodeId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String sessionKey = key(playerUuid);
            String value = redis.command("GETDEL", sessionKey);
            if (value == null || value.isBlank()) {
                sessionKey = legacyKey(playerUuid);
                value = redis.command("GETDEL", sessionKey);
            } else {
                redis.command("DEL", legacyKey(playerUuid));
            }
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            PlayerRouteSession session = decode(value);
            if (session.expiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (!session.targetNode().equals(nodeId)) {
                long ttlMillis = Math.max(1L, session.expiresAt().toEpochMilli() - System.currentTimeMillis());
                redis.command("SET", sessionKey, encode(session), "PX", Long.toString(ttlMillis));
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException | RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.consume(playerUuid, nodeId);
        }
    }

    @Override
    public Optional<PlayerRouteSession> findAny(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", key(playerUuid));
            if (value == null || value.isBlank()) {
                value = redis.command("GET", legacyKey(playerUuid));
            }
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            PlayerRouteSession session = decode(value);
            if (session.expiresAt().isBefore(Instant.now())) {
                redis.command("DEL", key(playerUuid));
                redis.command("DEL", legacyKey(playerUuid));
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException | RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.findAny(playerUuid);
        }
    }

    public boolean clear(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            fallback.clear(playerUuid);
            boolean clearedCurrent = !"0".equals(redis.command("DEL", key(playerUuid)));
            boolean clearedLegacy = !"0".equals(redis.command("DEL", legacyKey(playerUuid)));
            return clearedCurrent || clearedLegacy;
        } catch (IOException | RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.clear(playerUuid);
        }
    }

    @Override
    public int clearForNode(String nodeId) {
        int cleared = 0;
        try {
            for (String key : sessionKeys()) {
                try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                    String value = redis.command("GET", key);
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    PlayerRouteSession session = decode(value);
                    if (session.expiresAt().isBefore(Instant.now()) || session.targetNode().equals(nodeId)) {
                        if (!"0".equals(redis.command("DEL", key))) {
                            cleared++;
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    failures.incrementAndGet();
                }
            }
            return Math.max(cleared, fallback.clearForNode(nodeId));
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.clearForNode(nodeId);
        }
    }

    public int clearAll() {
        int cleared = 0;
        try {
            for (String key : sessionKeys()) {
                try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                    if (!"0".equals(redis.command("DEL", key))) {
                        cleared++;
                    }
                } catch (IOException | RuntimeException exception) {
                    failures.incrementAndGet();
                }
            }
            return Math.max(cleared, fallback.clearAll());
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.clearAll();
        }
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"sessions\":[");
        boolean first = true;
        try {
            for (String key : sessionKeys()) {
                try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                    String value = redis.command("GET", key);
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    PlayerRouteSession session = decode(value);
                    if (session.expiresAt().isBefore(Instant.now())) {
                        redis.command("DEL", key);
                        continue;
                    }
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    builder.append("{\"playerUuid\":\"").append(session.playerUuid())
                        .append("\",\"ticketId\":\"").append(session.ticketId())
                        .append("\",\"targetNode\":\"").append(escape(session.targetNode()))
                        .append("\",\"targetServerName\":\"").append(escape(session.targetServerName()))
                        .append("\",\"expiresAt\":\"").append(session.expiresAt())
                        .append("\"}");
                } catch (IOException | RuntimeException exception) {
                    failures.incrementAndGet();
                }
            }
            return builder.append("]}").toString();
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            return fallback.toJson();
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    private String key(UUID playerUuid) {
        return RedisKeys.playerSession(playerUuid);
    }

    private String legacyKey(UUID playerUuid) {
        return RedisKeys.playerRouteSession(playerUuid);
    }

    private List<String> sessionKeys() {
        List<String> keys = new ArrayList<>();
        scanSessionKeys(keys, "ci:player:*:session");
        scanSessionKeys(keys, "ci:player:*:route-session");
        return keys;
    }

    private void scanSessionKeys(List<String> keys, String pattern) {
        String cursor = "0";
        do {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                String response = redis.command("SCAN", cursor, "MATCH", pattern, "COUNT", "100");
                String[] lines = response.split("\\n");
                cursor = lines.length == 0 || lines[0].isBlank() ? "0" : lines[0];
                for (int i = 1; i < lines.length; i++) {
                    if (!lines[i].isBlank() && !keys.contains(lines[i])) {
                        keys.add(lines[i]);
                    }
                }
            } catch (IOException | RuntimeException exception) {
                failures.incrementAndGet();
                throw new IllegalStateException("failed to scan redis route sessions", exception);
            }
        } while (!"0".equals(cursor));
    }

    private String encode(PlayerRouteSession session) {
        return session.playerUuid()
            + "|" + session.ticketId()
            + "|" + session.targetNode()
            + "|" + session.targetServerName()
            + "|" + session.nonce()
            + "|" + session.expiresAt();
    }

    private PlayerRouteSession decode(String value) {
        String[] parts = value.split("\\|", -1);
        return new PlayerRouteSession(
            UUID.fromString(parts[0]),
            UUID.fromString(parts[1]),
            parts.length > 2 ? parts[2] : "",
            parts.length > 3 ? parts[3] : "",
            parts.length > 4 ? parts[4] : "",
            Instant.parse(parts.length > 5 ? parts[5] : Instant.EPOCH.toString())
        );
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

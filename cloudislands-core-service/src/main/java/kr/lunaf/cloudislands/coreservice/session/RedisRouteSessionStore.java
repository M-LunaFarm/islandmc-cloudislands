package kr.lunaf.cloudislands.coreservice.session;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class RedisRouteSessionStore implements RouteSessionStore {
    private final URI redisUri;

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
            return session;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to store redis route session", exception);
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
            String value = redis.command("GETDEL", key(playerUuid));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            PlayerRouteSession session = decode(value);
            if (session.expiresAt().isBefore(Instant.now()) || !session.targetNode().equals(nodeId)) {
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to consume redis route session", exception);
        }
    }

    public Optional<PlayerRouteSession> findAny(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", key(playerUuid));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            PlayerRouteSession session = decode(value);
            if (session.expiresAt().isBefore(Instant.now())) {
                redis.command("DEL", key(playerUuid));
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read redis route session", exception);
        }
    }

    public boolean clear(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            return !"0".equals(redis.command("DEL", key(playerUuid)));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to clear redis route session", exception);
        }
    }

    public String toJson() {
        return "{\"sessions\":[]}";
    }

    private String key(UUID playerUuid) {
        return RedisKeys.playerRouteSession(playerUuid);
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
}

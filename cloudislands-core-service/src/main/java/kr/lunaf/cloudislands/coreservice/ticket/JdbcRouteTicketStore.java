package kr.lunaf.cloudislands.coreservice.ticket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

public final class JdbcRouteTicketStore implements RouteTicketStore {
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcRouteTicketStore(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public RouteTicket save(RouteTicket ticket) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO route_tickets(id, player_uuid, island_id, action, target_node, target_world, state, nonce, payload, expires_at, consumed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?) ON CONFLICT (id) DO UPDATE SET player_uuid = EXCLUDED.player_uuid, island_id = EXCLUDED.island_id, action = EXCLUDED.action, target_node = EXCLUDED.target_node, target_world = EXCLUDED.target_world, state = EXCLUDED.state, nonce = EXCLUDED.nonce, payload = EXCLUDED.payload, expires_at = EXCLUDED.expires_at, consumed_at = EXCLUDED.consumed_at")) {
            bind(statement, ticket, ticket.state() == RouteTicketState.CONSUMED ? clock.instant() : null);
            statement.executeUpdate();
            return ticket;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save route ticket", exception);
        }
    }

    @Override
    public int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Instant expiresAt, Map<String, String> payload) {
        int updated = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets WHERE island_id = ? AND target_node = ? AND state = 'PREPARING' AND expires_at >= now()")) {
            statement.setObject(1, islandId);
            statement.setString(2, targetNode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    RouteTicket ticket = map(rs);
                    LinkedHashMap<String, String> mergedPayload = new LinkedHashMap<>(ticket.payload());
                    mergedPayload.putAll(payload);
                    save(new RouteTicket(
                        ticket.ticketId(),
                        ticket.playerUuid(),
                        ticket.action(),
                        ticket.islandId(),
                        ticket.targetNode(),
                        targetWorld == null || targetWorld.isBlank() ? ticket.targetWorld() : targetWorld,
                        RouteTicketState.READY,
                        expiresAt == null ? ticket.expiresAt() : expiresAt,
                        ticket.nonce(),
                        Map.copyOf(mergedPayload)
                    ));
                    updated++;
                }
            }
            return updated;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark route tickets ready", exception);
        }
    }

    @Override
    public List<RouteTicket> markFailedForIsland(UUID islandId, String targetNode, String reason) {
        List<RouteTicket> failedTickets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets WHERE island_id = ? AND target_node = ? AND state = 'PREPARING'")) {
            statement.setObject(1, islandId);
            statement.setString(2, targetNode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    RouteTicket ticket = map(rs);
                    LinkedHashMap<String, String> payload = new LinkedHashMap<>(ticket.payload());
                    payload.put("failureReason", reason == null ? "" : reason);
                    RouteTicket failed = new RouteTicket(
                        ticket.ticketId(),
                        ticket.playerUuid(),
                        ticket.action(),
                        ticket.islandId(),
                        ticket.targetNode(),
                        ticket.targetWorld(),
                        RouteTicketState.FAILED,
                        ticket.expiresAt(),
                        ticket.nonce(),
                        Map.copyOf(payload)
                    );
                    save(failed);
                    failedTickets.add(failed);
                }
            }
            return failedTickets;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark route tickets failed", exception);
        }
    }

    @Override
    public List<RouteTicket> markFailedForNode(String targetNode, String reason) {
        List<RouteTicket> failedTickets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets WHERE target_node = ? AND state IN ('PREPARING', 'READY')")) {
            statement.setString(1, targetNode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    RouteTicket ticket = map(rs);
                    LinkedHashMap<String, String> payload = new LinkedHashMap<>(ticket.payload());
                    payload.put("failureReason", reason == null ? "" : reason);
                    RouteTicket failed = new RouteTicket(
                        ticket.ticketId(),
                        ticket.playerUuid(),
                        ticket.action(),
                        ticket.islandId(),
                        ticket.targetNode(),
                        ticket.targetWorld(),
                        RouteTicketState.FAILED,
                        ticket.expiresAt(),
                        ticket.nonce(),
                        Map.copyOf(payload)
                    );
                    save(failed);
                    failedTickets.add(failed);
                }
            }
            return failedTickets;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark node route tickets failed", exception);
        }
    }

    @Override
    public Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        RouteTicket ticket = find(ticketId).orElse(null);
        if (ticket == null || ticket.state() != RouteTicketState.READY) {
            return Optional.empty();
        }
        if (ticket.expiresAt().isBefore(clock.instant())) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE route_tickets SET state = 'EXPIRED' WHERE id = ? AND state = 'READY'")) {
                statement.setObject(1, ticketId);
                statement.executeUpdate();
                return Optional.empty();
            } catch (SQLException exception) {
                throw new IllegalStateException("failed to expire route ticket", exception);
            }
        }
        if (!ticket.playerUuid().equals(playerUuid) || !ticket.targetNode().equals(nodeId) || !ticket.nonce().equals(nonce)) {
            return Optional.empty();
        }
        RouteTicket consumed = new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.CONSUMED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE route_tickets SET state = 'CONSUMED', consumed_at = now() WHERE id = ? AND player_uuid = ? AND target_node = ? AND nonce = ? AND state = 'READY' AND expires_at >= now()")) {
            statement.setObject(1, ticketId);
            statement.setObject(2, playerUuid);
            statement.setString(3, nodeId);
            statement.setString(4, nonce);
            return statement.executeUpdate() == 1 ? Optional.of(consumed) : Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to consume route ticket", exception);
        }
    }

    @Override
    public Optional<RouteTicket> find(UUID ticketId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets WHERE id = ?")) {
            statement.setObject(1, ticketId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read route ticket", exception);
        }
    }

    @Override
    public Optional<RouteTicket> findLatestForPlayer(UUID playerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets WHERE player_uuid = ? ORDER BY CASE WHEN state IN ('READY', 'PREPARING') AND expires_at >= now() THEN 0 ELSE 1 END, created_at DESC LIMIT 1")) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read player route ticket", exception);
        }
    }

    @Override
    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (RouteTicketState state : RouteTicketState.values()) {
            counts.put(state.name(), 0L);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT state, count(*) AS total FROM route_tickets GROUP BY state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("state"), rs.getLong("total"));
            }
            return Map.copyOf(counts);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count route tickets", exception);
        }
    }

    @Override
    public List<RouteTicket> expireStale() {
        List<RouteTicket> expired = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM route_tickets WHERE state IN ('READY', 'PREPARING') AND expires_at < now()")) {
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    RouteTicket ticket = map(rs);
                    RouteTicket expiredTicket = new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.EXPIRED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
                    save(expiredTicket);
                    expired.add(expiredTicket);
                }
            }
            return expired;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to expire stale route tickets", exception);
        }
    }

    @Override
    public boolean clear(UUID ticketId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM route_tickets WHERE id = ?")) {
            statement.setObject(1, ticketId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear route ticket", exception);
        }
    }

    @Override
    public int clearAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM route_tickets")) {
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear route tickets", exception);
        }
    }

    @Override
    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"tickets\":[");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM route_tickets ORDER BY created_at DESC LIMIT 100");
             ResultSet rs = statement.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                RouteTicket ticket = map(rs);
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(json(ticket));
            }
            return builder.append("]}").toString();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to render route tickets", exception);
        }
    }

    private void bind(PreparedStatement statement, RouteTicket ticket, Instant consumedAt) throws SQLException {
        statement.setObject(1, ticket.ticketId());
        statement.setObject(2, ticket.playerUuid());
        statement.setObject(3, ticket.islandId());
        statement.setString(4, ticket.action().name());
        statement.setString(5, ticket.targetNode());
        statement.setString(6, ticket.targetWorld());
        statement.setString(7, ticket.state().name());
        statement.setString(8, ticket.nonce());
        statement.setString(9, toJson(ticket.payload()));
        statement.setObject(10, java.sql.Timestamp.from(ticket.expiresAt()));
        statement.setObject(11, consumedAt == null ? null : java.sql.Timestamp.from(consumedAt));
    }

    private RouteTicket map(ResultSet rs) throws SQLException {
        return new RouteTicket(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("player_uuid"),
            RouteAction.valueOf(rs.getString("action")),
            (UUID) rs.getObject("island_id"),
            rs.getString("target_node"),
            rs.getString("target_world"),
            RouteTicketState.valueOf(rs.getString("state")),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getString("nonce"),
            payload(rs.getString("payload"))
        );
    }

    private Map<String, String> payload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String pair : trimmed.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                values.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(values);
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String toJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String json(RouteTicket ticket) {
        return "{\"ticketId\":\"" + ticket.ticketId()
            + "\",\"playerUuid\":\"" + ticket.playerUuid()
            + "\",\"action\":\"" + ticket.action()
            + "\",\"islandId\":\"" + ticket.islandId()
            + "\",\"targetNode\":\"" + ticket.targetNode()
            + "\",\"targetWorld\":\"" + ticket.targetWorld()
            + "\",\"targetServerName\":\"" + ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
            + "\",\"state\":\"" + ticket.state()
            + "\",\"expiresAt\":\"" + ticket.expiresAt()
            + "\",\"payload\":" + toJson(ticket.payload())
            + "}";
    }
}

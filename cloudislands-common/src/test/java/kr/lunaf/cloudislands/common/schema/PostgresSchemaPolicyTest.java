package kr.lunaf.cloudislands.common.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostgresSchemaPolicyTest {
    @Test
    void pinsCoreTablesFromTheSchemaPlan() {
        assertEquals(
            List.of(
                "player_profiles",
                "islands",
                "island_members",
                "island_roles",
                "island_permissions",
                "island_flags",
                "island_bans",
                "island_invites",
                "island_warps",
                "island_runtime",
                "island_snapshots",
                "route_tickets",
                "island_jobs",
                "server_nodes",
                "audit_logs"
            ),
            PostgresSchemaPolicy.coreTables()
        );
        assertTrue(PostgresSchemaPolicy.coreTable("island_runtime"));
        assertTrue(PostgresSchemaPolicy.coreTable(" ROUTE_TICKETS "));
        assertFalse(PostgresSchemaPolicy.coreTable("legacy_superiorskyblock_islands"));
    }

    @Test
    void pinsRequiredColumnsForCriticalTables() {
        assertEquals(
            List.of("id", "owner_uuid", "name", "state", "template_id", "size", "level", "worth", "public_access", "locked", "created_at", "updated_at", "deleted_at"),
            PostgresSchemaPolicy.requiredColumns("islands")
        );
        assertTrue(PostgresSchemaPolicy.requiredColumn("island_runtime", "fencing_token"));
        assertTrue(PostgresSchemaPolicy.requiredColumn("route_tickets", "nonce"));
        assertTrue(PostgresSchemaPolicy.requiredColumn("route_tickets", "consumed_at"));
        assertTrue(PostgresSchemaPolicy.requiredColumn("island_jobs", "request_id"));
        assertTrue(PostgresSchemaPolicy.requiredColumn("server_nodes", "activation_queue"));
        assertFalse(PostgresSchemaPolicy.requiredColumn("islands", "server_name_owner"));
    }

    @Test
    void pinsIndexesThatProtectOwnershipRoutingAndJobIdempotency() {
        assertEquals(
            Map.of(
                "idx_islands_owner_active", "islands(owner_uuid) where deleted_at is null",
                "idx_island_members_player", "island_members(player_uuid)",
                "idx_island_invites_target", "island_invites(target_uuid,state)",
                "idx_route_tickets_player_state", "route_tickets(player_uuid,state,expires_at)",
                "idx_island_jobs_request_id", "island_jobs(request_id)"
            ),
            PostgresSchemaPolicy.requiredIndexes()
        );
        assertTrue(PostgresSchemaPolicy.requiredIndex("idx_islands_owner_active"));
        assertTrue(PostgresSchemaPolicy.requiredIndex(" IDX_ROUTE_TICKETS_PLAYER_STATE "));
        assertFalse(PostgresSchemaPolicy.requiredIndex("idx_world_name_owner"));
    }

    @Test
    void recordsAuthorityAndConcurrencyPolicies() {
        assertEquals(
            "postgresql-transactional-schema-is-authoritative-for-island-player-runtime-route-job-node-and-audit-state",
            PostgresSchemaPolicy.SOURCE_OF_TRUTH_POLICY
        );
        assertEquals(
            "one-active-island-per-owner-enforced-by-partial-unique-index-on-islands-owner-where-deleted-at-is-null",
            PostgresSchemaPolicy.OWNER_UNIQUE_POLICY
        );
        assertEquals("island_runtime-fencing_token-prevents-stale-node-writes", PostgresSchemaPolicy.RUNTIME_FENCING_POLICY);
        assertEquals("route_tickets-use-nonce-expiry-consumed-at-and-player-state-index-for-one-time-routing", PostgresSchemaPolicy.ROUTE_TICKET_POLICY);
        assertEquals("island_jobs-request_id-unique-index-provides-idempotent-control-plane-jobs", PostgresSchemaPolicy.JOB_IDEMPOTENCY_POLICY);
        assertEquals("audit_logs-retain-admin-system-action-payloads-for-security-and-recovery-review", PostgresSchemaPolicy.AUDIT_POLICY);
    }
}

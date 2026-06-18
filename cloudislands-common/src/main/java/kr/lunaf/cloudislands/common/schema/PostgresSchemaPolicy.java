package kr.lunaf.cloudislands.common.schema;

import java.util.List;
import java.util.Map;

public final class PostgresSchemaPolicy {
    public static final String SOURCE_OF_TRUTH_POLICY = "postgresql-transactional-schema-is-authoritative-for-island-player-runtime-route-job-node-and-audit-state";
    public static final String OWNER_UNIQUE_POLICY = "one-active-island-per-owner-enforced-by-partial-unique-index-on-islands-owner-where-deleted-at-is-null";
    public static final String RUNTIME_FENCING_POLICY = "island_runtime-fencing_token-prevents-stale-node-writes";
    public static final String ROUTE_TICKET_POLICY = "route_tickets-use-nonce-expiry-consumed-at-and-player-state-index-for-one-time-routing";
    public static final String JOB_IDEMPOTENCY_POLICY = "island_jobs-request_id-unique-index-provides-idempotent-control-plane-jobs";
    public static final String AUDIT_POLICY = "audit_logs-retain-admin-system-action-payloads-for-security-and-recovery-review";

    private static final List<String> CORE_TABLES = List.of(
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
    );

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.ofEntries(
        Map.entry("player_profiles", List.of("uuid", "last_name", "primary_island_id", "last_seen_at", "created_at", "updated_at")),
        Map.entry("islands", List.of("id", "owner_uuid", "name", "state", "template_id", "size", "level", "worth", "public_access", "locked", "created_at", "updated_at", "deleted_at")),
        Map.entry("island_members", List.of("island_id", "player_uuid", "role", "joined_at")),
        Map.entry("island_roles", List.of("island_id", "role", "weight", "display_name")),
        Map.entry("island_permissions", List.of("island_id", "role", "permission_key", "value")),
        Map.entry("island_flags", List.of("island_id", "flag_key", "flag_value", "updated_at")),
        Map.entry("island_bans", List.of("island_id", "banned_uuid", "actor_uuid", "reason", "created_at", "expires_at")),
        Map.entry("island_invites", List.of("id", "island_id", "inviter_uuid", "target_uuid", "state", "created_at", "expires_at")),
        Map.entry("island_warps", List.of("island_id", "name", "local_x", "local_y", "local_z", "yaw", "pitch", "public_access", "created_by", "created_at")),
        Map.entry("island_runtime", List.of("island_id", "state", "active_node", "active_world", "cell_x", "cell_z", "lease_owner", "lease_until", "fencing_token", "activated_at", "last_heartbeat", "updated_at")),
        Map.entry("island_snapshots", List.of("id", "island_id", "snapshot_no", "storage_path", "reason", "created_by", "checksum", "size_bytes", "created_at")),
        Map.entry("route_tickets", List.of("id", "player_uuid", "island_id", "action", "target_node", "target_world", "state", "nonce", "payload", "created_at", "expires_at", "consumed_at")),
        Map.entry("island_jobs", List.of("id", "job_type", "island_id", "target_node", "state", "priority", "request_id", "payload", "retry_count", "max_retries", "error_message", "created_at", "updated_at", "locked_by", "locked_until")),
        Map.entry("server_nodes", List.of("id", "pool", "velocity_server_name", "state", "soft_player_cap", "hard_player_cap", "reserved_slots", "max_active_islands", "players", "active_islands", "mspt", "heap_used_mb", "heap_max_mb", "activation_queue", "last_heartbeat", "updated_at")),
        Map.entry("audit_logs", List.of("id", "actor_uuid", "actor_type", "action", "target_type", "target_id", "payload", "created_at"))
    );

    private static final Map<String, String> REQUIRED_INDEXES = Map.of(
        "idx_islands_owner_active", "islands(owner_uuid) where deleted_at is null",
        "idx_island_members_player", "island_members(player_uuid)",
        "idx_island_invites_target", "island_invites(target_uuid,state)",
        "idx_route_tickets_player_state", "route_tickets(player_uuid,state,expires_at)",
        "idx_island_jobs_request_id", "island_jobs(request_id)"
    );

    private PostgresSchemaPolicy() {
    }

    public static List<String> coreTables() {
        return CORE_TABLES;
    }

    public static boolean coreTable(String table) {
        return table != null && CORE_TABLES.contains(table.trim().toLowerCase());
    }

    public static Map<String, List<String>> requiredColumns() {
        return REQUIRED_COLUMNS;
    }

    public static List<String> requiredColumns(String table) {
        return table == null ? List.of() : REQUIRED_COLUMNS.getOrDefault(table.trim().toLowerCase(), List.of());
    }

    public static boolean requiredColumn(String table, String column) {
        return column != null && requiredColumns(table).contains(column.trim().toLowerCase());
    }

    public static Map<String, String> requiredIndexes() {
        return REQUIRED_INDEXES;
    }

    public static boolean requiredIndex(String indexName) {
        return indexName != null && REQUIRED_INDEXES.containsKey(indexName.trim().toLowerCase());
    }

    public static String tableSummary() {
        return String.join(",", CORE_TABLES);
    }
}

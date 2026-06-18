package kr.seungmin.satisskyfactory.storage;

import java.util.List;

public final class SatisLegacyMigrationPolicy {
    public static final String SOURCE_PROJECT = "M-LunaFarm/satismc";
    public static final String LEGACY_SKYBLOCK_SOURCE = "SuperiorSkyblock2";
    public static final String LEGACY_SATIS_SOURCE = "satismc";
    public static final String SOURCE_ACCESS_POLICY = "read-only-snapshot-or-sqlite-scan-no-live-provider-hooks";
    public static final String RUNTIME_DEPENDENCY_POLICY = "legacy-provider-is-migration-input-only-never-runtime-dependency";
    public static final String RUNTIME_WITHOUT_LEGACY_PROVIDER_POLICY = "satis-runtime-starts-and-runs-when-superiorskyblock2-is-absent";
    public static final String RUNTIME_PROVIDER_HOOK_POLICY = "forbid-superiorskyblock2-runtime-hooks-after-import";
    public static final String LIVE_PROVIDER_PRESENT_POLICY = "legacy-provider-present-is-warning-only-migration-input-never-runtime-authority";
    public static final String PROVIDER_LOOKUP_POLICY = "no-bukkit-service-lookup-no-event-hooks-no-data-writes";
    public static final String PROVIDER_ABSENT_ACTION = "continue-cloudislands-api-only";
    public static final String PROVIDER_PRESENT_ACTION = "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes";
    public static final String ADDON_STATE_VERIFY_POLICY = "verify-imported-satis-state-through-cloudislands-addon-state";
    public static final String APPROVAL_POLICY = "admin-confirmation-required-before-import";
    public static final String APPROVAL_TOKEN = "CONFIRM_IMPORT";
    public static final String FINGERPRINT_APPROVAL_TOKEN = "CONFIRM_IMPORT:<dryrun-sha256>";
    public static final String APPROVAL_TOKEN_POLICY = "plain-confirm-or-dryrun-sha256-bound-confirm";
    public static final String IMPORT_COMMAND = "factory admin migration import <sqlitePath> CONFIRM_IMPORT|CONFIRM_IMPORT:<dryrun-sha256>";
    public static final String LEGACY_COMMAND_ROOT = "migrate-superiorskyblock2";
    public static final String ROLLBACK_POLICY = "rollback-manifest-only-no-automatic-live-data-delete";
    public static final String OUTPUT_ID_POLICY = "cloudislands-island-uuid";
    public static final String MANIFEST_POLICY = "create-cloudislands-migration-manifest-before-import";

    private static final List<String> FORBIDDEN_RUNTIME_PROVIDERS = List.of(
            "SuperiorSkyblock2",
            "BentoBox",
            "ASkyBlock",
            "uSkyBlock",
            "IridiumSkyblock"
    );

    private static final List<String> TARGET_FIELDS = List.of(
            "island-id",
            "owner-uuid",
            "members",
            "roles",
            "permissions",
            "island-location",
            "island-size",
            "home-location",
            "warps",
            "banned-visitors",
            "level",
            "worth",
            "upgrades",
            "flags",
            "block-value-settings",
            "satis-machines",
            "satis-resource-nodes",
            "satis-storage",
            "satis-research",
            "satis-market",
            "satis-contracts"
    );

    private static final List<String> PIPELINE_STEPS = List.of(
            "read-only-scan",
            "create-migration-manifest",
            "dry-run-validate",
            "print-conflicts",
            "admin-approve",
            "db-import",
            "extract-world-cell",
            "create-island-bundle",
            "verify-checksum",
            "cloudislands-activate-test",
            "verify-addon-state-roundtrip",
            "verify-no-legacy-provider-hook"
    );

    private static final List<String> ADMIN_COMMANDS = List.of(
            "factory admin migration status",
            "factory admin migration scan <sqlitePath>",
            "factory admin migration dryrun <sqlitePath>",
            "factory admin migration verify <sqlitePath>",
            "factory admin migration verify-addon-state <islandUuid>",
            "factory admin migration verify-no-legacy-provider",
            IMPORT_COMMAND,
            "factory admin migration rollback",
            "factory admin migrate-superiorskyblock2 scan <sqlitePath>",
            "factory admin migrate-superiorskyblock2 dryrun <sqlitePath>",
            "factory admin migrate-superiorskyblock2 import <sqlitePath> CONFIRM_IMPORT|CONFIRM_IMPORT:<dryrun-sha256>",
            "factory admin migrate-superiorskyblock2 verify <sqlitePath>",
            "factory admin migrate-superiorskyblock2 verify-addon-state <islandUuid>",
            "factory admin migrate-superiorskyblock2 verify-no-legacy-provider",
            "factory admin migrate-superiorskyblock2 rollback"
    );

    private SatisLegacyMigrationPolicy() {
    }

    public static List<String> targetFields() {
        return TARGET_FIELDS;
    }

    public static List<String> pipelineSteps() {
        return PIPELINE_STEPS;
    }

    public static List<String> adminCommands() {
        return ADMIN_COMMANDS;
    }

    public static List<String> forbiddenRuntimeProviders() {
        return FORBIDDEN_RUNTIME_PROVIDERS;
    }

    public static String forbiddenRuntimeProvidersCsv() {
        return String.join(",", FORBIDDEN_RUNTIME_PROVIDERS);
    }

    public static boolean targetFieldRequired(String field) {
        return TARGET_FIELDS.contains(field);
    }

    public static boolean pipelineStepRequired(String step) {
        return PIPELINE_STEPS.contains(step);
    }

    public static boolean runtimeRequiresLegacyProvider() {
        return false;
    }

    public static boolean legacyProviderMayBecomeRuntimeAuthority() {
        return false;
    }

    public static String runtimeProviderAction(boolean legacyProviderPresent) {
        return legacyProviderPresent ? PROVIDER_PRESENT_ACTION : PROVIDER_ABSENT_ACTION;
    }
}

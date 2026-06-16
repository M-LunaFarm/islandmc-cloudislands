package kr.lunaf.cloudislands.migration.superior;

import java.util.Set;

public final class MigrationSafetyPolicy {
    public static final String SOURCE_PLUGIN = "SuperiorSkyblock2";
    public static final boolean MIGRATION_INPUT_ONLY = true;
    public static final boolean RUNTIME_DEPENDENCY_ALLOWED = false;
    public static final Set<String> READ_ONLY_ACTIONS = Set.of("scan", "dryrun", "dry-run", "verify", "status");
    public static final Set<String> WRITE_ACTIONS = Set.of("extract", "import", "rollback");
    public static final String APPROVAL_POLICY = "import-requires-successful-dryrun-approval-token-and-unchanged-source-fingerprint";
    public static final String ROLLBACK_POLICY = "rollback-plan-records-imported-islands-and-removes-only-cloudislands-imported-state";

    private MigrationSafetyPolicy() {
    }

    public static boolean readOnly(String action) {
        return READ_ONLY_ACTIONS.contains(normalize(action));
    }

    public static boolean writeAction(String action) {
        return WRITE_ACTIONS.contains(normalize(action));
    }

    public static boolean approvalRequired(String action) {
        return normalize(action).equals("import");
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toLowerCase();
    }
}

package kr.lunaf.cloudislands.migration.superior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MigrationSafetyPolicy {
    public static final String SOURCE_PLUGIN = "SuperiorSkyblock2";
    public static final String TARGET_RUNTIME = "CloudIslands";
    public static final boolean MIGRATION_INPUT_ONLY = true;
    public static final boolean RUNTIME_DEPENDENCY_ALLOWED = false;
    public static final String RUNTIME_POLICY = "migration-input-only-no-runtime-hooks";
    public static final Set<String> READ_ONLY_ACTIONS = Set.of("scan", "dryrun", "dry-run", "verify", "status");
    public static final Set<String> WRITE_ACTIONS = Set.of("extract", "import", "rollback");
    public static final String OPERATIONS = "scan,dryrun,extract,import,verify,rollback,status";
    public static final String PIPELINE = "read-only-scan,manifest,dry-run,conflict-report,approval,db-import,world-cell-extract,bundle-checksum,cloudislands-activate-test,rollback-plan";
    public static final String CHECKSUM_POLICY = "sha256-every-extracted-world-bundle-and-verify-against-imported-snapshot";
    public static final String ACTIVATION_TEST_POLICY = "verify-can-run-cloudislands-activation-test-without-superiorskyblock2-runtime-dependency";
    public static final List<String> FORBIDDEN_RUNTIME_PROVIDERS = List.of("SuperiorSkyblock2", "BentoBox", "ASkyBlock");
    public static final String FORBIDDEN_RUNTIME_ACTION = "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes";
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

    public static boolean forbiddenRuntimeProvider(String provider) {
        String normalizedProvider = normalize(provider);
        return FORBIDDEN_RUNTIME_PROVIDERS.stream()
            .map(MigrationSafetyPolicy::normalize)
            .anyMatch(normalizedProvider::equals);
    }

    public static String forbiddenRuntimeProvidersCsv() {
        return String.join(",", FORBIDDEN_RUNTIME_PROVIDERS);
    }

    public static Map<String, String> boundaryMetadata() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourcePlugin", SOURCE_PLUGIN);
        fields.put("migrationInputOnly", Boolean.toString(MIGRATION_INPUT_ONLY));
        fields.put("runtimeDependency", Boolean.toString(RUNTIME_DEPENDENCY_ALLOWED));
        fields.put("targetRuntime", TARGET_RUNTIME);
        fields.put("runtimePolicy", RUNTIME_POLICY);
        fields.put("forbiddenRuntimeProviders", forbiddenRuntimeProvidersCsv());
        fields.put("forbiddenRuntimeAction", FORBIDDEN_RUNTIME_ACTION);
        return Map.copyOf(fields);
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toLowerCase();
    }
}

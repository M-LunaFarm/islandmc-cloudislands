package kr.seungmin.satisskyfactory.runtime;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisDatabaseRuntimeTest {
    private final SatisDatabaseRuntime runtime = new SatisDatabaseRuntime();

    @Test
    void databaseRuntimeFingerprintsBackendSettingsAndMasksPasswords() {
        DatabaseService.Settings settings = new DatabaseService.Settings(
                DatabaseService.StorageBackend.POSTGRESQL,
                "data.db",
                "jdbc:postgresql://db/cloud",
                "jdbc:postgresql://db/cloud",
                "",
                "",
                "satis",
                "secret",
                8,
                5000L,
                new DatabaseService.BackendSettings("pg", "pg-secret", 6, 3000L),
                DatabaseService.BackendSettings.empty(),
                DatabaseService.BackendSettings.empty(),
                true,
                List.of(DatabaseService.StorageBackend.POSTGRESQL, DatabaseService.StorageBackend.CORE_API, DatabaseService.StorageBackend.SQLITE)
        );

        String fingerprint = runtime.settingsFingerprint(settings);

        assertTrue(fingerprint.startsWith("POSTGRESQL|data.db|jdbc:postgresql://db/cloud"));
        assertTrue(fingerprint.endsWith("true|POSTGRESQL,CORE_API,SQLITE"));
        assertFalse(fingerprint.contains("secret"));
        assertFalse(fingerprint.contains("pg-secret"));
    }

    @Test
    void databaseRuntimeAppendsFallbackReasonsWithoutDuplicates() {
        String first = runtime.appendFallbackReason("none", "CORE_API_FAILED");
        String second = runtime.appendFallbackReason(first, "SQLITE_READY");
        String duplicate = runtime.appendFallbackReason(second, "CORE_API_FAILED");

        assertEquals("CORE_API_FAILED;SQLITE_READY", second);
        assertEquals(second, duplicate);
        assertEquals(second, runtime.appendFallbackReason(second, "none"));
    }
}

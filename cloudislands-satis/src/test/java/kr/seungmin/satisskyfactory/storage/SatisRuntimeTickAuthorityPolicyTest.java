package kr.seungmin.satisskyfactory.storage;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeTickAuthorityPolicyTest {
    @Test
    void coreApiTicksOnlyAfterAddonStateHydration() {
        assertFalse(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.CORE_API, false, true, true));
        assertFalse(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.CORE_API, true, false, true));
        assertFalse(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.CORE_API, true, true, false));
        assertTrue(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.CORE_API, true, true, true));
        assertEquals("core-api-requires-cloudislands-api-addon-state-and-hydrated-island",
                SatisRuntimeTickAuthorityPolicy.tickPolicy(DatabaseService.StorageBackend.CORE_API));
    }

    @Test
    void sharedSqlBackendsCanTickUnderCloudIslandsRuntimeFence() {
        assertTrue(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.POSTGRESQL, false, false, false));
        assertTrue(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.MYSQL, false, false, false));
        assertTrue(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.MARIADB, false, false, false));
        assertEquals("shared-sql-backend-uses-cloudislands-runtime-owner-fence",
                SatisRuntimeTickAuthorityPolicy.tickPolicy(DatabaseService.StorageBackend.POSTGRESQL));
    }

    @Test
    void localSqliteFallbackDoesNotRunDistributedRuntimeTicks() {
        assertFalse(SatisRuntimeTickAuthorityPolicy.tickReady(DatabaseService.StorageBackend.SQLITE, true, true, true));
        assertFalse(SatisRuntimeTickAuthorityPolicy.tickReady(null, true, true, true));
        assertEquals("local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-ticks",
                SatisRuntimeTickAuthorityPolicy.tickPolicy(DatabaseService.StorageBackend.SQLITE));
    }

    @Test
    void localSqliteFallbackDoesNotRunDistributedRuntimeWrites() {
        assertTrue(SatisRuntimeTickAuthorityPolicy.writeReady(DatabaseService.StorageBackend.CORE_API, true));
        assertTrue(SatisRuntimeTickAuthorityPolicy.writeReady(DatabaseService.StorageBackend.POSTGRESQL, true));
        assertFalse(SatisRuntimeTickAuthorityPolicy.writeReady(DatabaseService.StorageBackend.CORE_API, false));
        assertFalse(SatisRuntimeTickAuthorityPolicy.writeReady(DatabaseService.StorageBackend.SQLITE, true));
        assertFalse(SatisRuntimeTickAuthorityPolicy.writeReady(null, true));
        assertEquals("local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-writes",
                SatisRuntimeTickAuthorityPolicy.writePolicy(DatabaseService.StorageBackend.SQLITE));
    }
}

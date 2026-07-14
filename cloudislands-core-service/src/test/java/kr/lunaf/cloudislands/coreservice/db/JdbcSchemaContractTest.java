package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import org.junit.jupiter.api.Test;

class JdbcSchemaContractTest {
    @Test
    void publishesFailFastStartupPolicyAndCoversCriticalCreationColumns() {
        assertEquals(
            "startup-validates-critical-island-column-types-even-when-auto-schema-is-disabled",
            JdbcSchemaContract.POLICY
        );
        assertEquals(13, JdbcSchemaContract.requiredColumnCount());
    }

    @Test
    void rejectsTheTextStoredInIntegerFailureMode() {
        assertTrue(JdbcSchemaContract.compatible("text", Types.VARCHAR));
        assertFalse(JdbcSchemaContract.compatible("text", Types.INTEGER));
        assertTrue(JdbcSchemaContract.compatible("integer", Types.INTEGER));
        assertFalse(JdbcSchemaContract.compatible("integer", Types.VARCHAR));
    }

    @Test
    void acceptsPortablePostgresqlAndMysqlRepresentations() {
        assertTrue(JdbcSchemaContract.compatible("boolean", Types.BOOLEAN));
        assertTrue(JdbcSchemaContract.compatible("boolean", Types.BIT));
        assertTrue(JdbcSchemaContract.compatible("boolean", Types.TINYINT));
        assertTrue(JdbcSchemaContract.compatible("bigint", Types.BIGINT));
        assertTrue(JdbcSchemaContract.compatible("decimal", Types.NUMERIC));
        assertTrue(JdbcSchemaContract.compatible("decimal", Types.DECIMAL));
    }
}

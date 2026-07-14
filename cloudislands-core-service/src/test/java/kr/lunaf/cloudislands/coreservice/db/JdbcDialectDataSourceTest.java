package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JdbcDialectDataSourceTest {
    private static final UUID VALUE = UUID.fromString("00000000-0000-0000-0000-000000000901");

    @Test
    void recognizesEveryUuidColumnNamingConventionUsedByCoreSchemas() {
        assertUuidColumn("id");
        assertUuidColumn("island_id");
        assertUuidColumn("uuid");
        assertUuidColumn("owner_uuid");
        assertUuidColumn("created_by");
        assertUuidColumn("updated_by");
        assertUuidColumn("moderated_by");

        assertFalse(JdbcDialectDataSource.uuidColumn(new Object[] {"locked_by"}));
        assertFalse(JdbcDialectDataSource.uuidColumn(new Object[] {"material_key"}));
        assertFalse(JdbcDialectDataSource.uuidColumn(new Object[] {1}));
        assertFalse(JdbcDialectDataSource.uuidColumn(null));
    }

    @Test
    void convertsMysqlTextAndBinaryUuidValuesWithoutBreakingNulls() {
        assertEquals(VALUE, JdbcDialectDataSource.toUuid(VALUE.toString()));

        ByteBuffer bytes = ByteBuffer.allocate(16).putLong(VALUE.getMostSignificantBits()).putLong(VALUE.getLeastSignificantBits());
        assertEquals(VALUE, JdbcDialectDataSource.toUuid(bytes.array()));
        assertEquals(VALUE, JdbcDialectDataSource.toUuid(VALUE));
        assertNull(JdbcDialectDataSource.toUuid(null));
        assertNull(JdbcDialectDataSource.toUuid("not-a-uuid"));
    }

    @Test
    void recognizesEveryChar36UuidColumnInTheMysqlSchema() throws Exception {
        String schema;
        try (var input = getClass().getResourceAsStream(JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_RESOURCE)) {
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matcher = Pattern.compile("(?m)^\\s*([a-z_]+)\\s+CHAR\\(36\\)").matcher(schema);
        int columns = 0;
        while (matcher.find()) {
            columns++;
            assertUuidColumn(matcher.group(1));
        }
        assertTrue(columns >= 20, "expected the complete MySQL UUID column set");
    }

    @Test
    void mysqlConnectionsAndTimestampBindingsUseUtc() throws Exception {
        assertEquals("SET time_zone = '+00:00'", JdbcDialectDataSource.MYSQL_SESSION_TIME_ZONE_SQL);
        assertEquals("UTC", JdbcDialectDataSource.utcCalendar().getTimeZone().getID());

        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/db/JdbcDialectDataSource.java"));
        assertTrue(source.contains("configureMysqlSession(connection)"));
        assertTrue(source.contains("preparedStatement.setTimestamp(index, timestamp, utcCalendar())"));
        assertTrue(source.contains("resultSet.getTimestamp(index, utcCalendar())"));
        assertTrue(source.contains("resultSet.getTimestamp(String.valueOf(args[0]), utcCalendar())"));
    }

    private void assertUuidColumn(String name) {
        assertTrue(JdbcDialectDataSource.uuidColumn(new Object[] {name}), name);
        assertTrue(JdbcDialectDataSource.uuidColumn(new Object[] {name.toUpperCase(java.util.Locale.ROOT)}), name);
    }
}

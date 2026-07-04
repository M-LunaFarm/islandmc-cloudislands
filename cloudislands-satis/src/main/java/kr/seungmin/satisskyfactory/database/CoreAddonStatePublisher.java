package kr.seungmin.satisskyfactory.database;

import kr.lunaf.cloudislands.api.service.IslandAddonService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class CoreAddonStatePublisher {
    private Consumer<DatabaseService.CoreRowWrite> coreStateWriter;
    private Consumer<DatabaseService.CoreTableWrite> coreTableWriter;
    private Consumer<DatabaseService.CoreBulkWrite> coreBulkWriter;
    private Consumer<DatabaseService.CoreGlobalRowWrite> coreGlobalStateWriter;
    private Consumer<DatabaseService.CoreGlobalTableWrite> coreGlobalTableWriter;
    private Consumer<DatabaseService.CoreGlobalBulkWrite> coreGlobalBulkWriter;
    private boolean suspended;

    boolean writersAvailable() {
        return coreStateWriter != null
                && coreTableWriter != null
                && coreBulkWriter != null
                && coreGlobalStateWriter != null
                && coreGlobalTableWriter != null
                && coreGlobalBulkWriter != null;
    }

    boolean publishingSuspended() {
        return suspended;
    }

    boolean hasPrimaryWriter() {
        return coreStateWriter != null
                || coreTableWriter != null
                || coreGlobalStateWriter != null
                || coreGlobalTableWriter != null;
    }

    void coreStateWriter(Consumer<DatabaseService.CoreRowWrite> coreStateWriter) {
        this.coreStateWriter = coreStateWriter;
    }

    void coreTableWriter(Consumer<DatabaseService.CoreTableWrite> coreTableWriter) {
        this.coreTableWriter = coreTableWriter;
    }

    void coreBulkWriter(Consumer<DatabaseService.CoreBulkWrite> coreBulkWriter) {
        this.coreBulkWriter = coreBulkWriter;
    }

    void coreGlobalStateWriter(Consumer<DatabaseService.CoreGlobalRowWrite> coreGlobalStateWriter) {
        this.coreGlobalStateWriter = coreGlobalStateWriter;
    }

    void coreGlobalTableWriter(Consumer<DatabaseService.CoreGlobalTableWrite> coreGlobalTableWriter) {
        this.coreGlobalTableWriter = coreGlobalTableWriter;
    }

    void coreGlobalBulkWriter(Consumer<DatabaseService.CoreGlobalBulkWrite> coreGlobalBulkWriter) {
        this.coreGlobalBulkWriter = coreGlobalBulkWriter;
    }

    void withPublishingSuspended(Runnable action) {
        if (action == null) {
            return;
        }
        boolean previous = suspended;
        suspended = true;
        try {
            action.run();
        } finally {
            suspended = previous;
        }
    }

    void publishRow(UUID islandUuid, String key, String value) {
        if (suspended || coreStateWriter == null || islandUuid == null || key == null || key.isBlank() || value == null) {
            return;
        }
        coreStateWriter.accept(new DatabaseService.CoreRowWrite(islandUuid, key, value));
    }

    void publishTable(UUID islandUuid, String table, Map<String, String> values) {
        if (suspended || islandUuid == null || table == null || table.isBlank() || values == null) {
            return;
        }
        if (coreBulkWriter != null && !values.isEmpty()) {
            coreBulkWriter.accept(new DatabaseService.CoreBulkWrite(islandUuid, Map.of(), Map.of(table, Map.copyOf(values))));
            return;
        }
        if (coreTableWriter != null) {
            coreTableWriter.accept(new DatabaseService.CoreTableWrite(islandUuid, table, Map.copyOf(values)));
            return;
        }
        if (values.isEmpty()) {
            return;
        }
        String safeTable = table.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX)
                ? table.substring(IslandAddonService.TABLE_STATE_KEY_PREFIX.length())
                : table;
        values.forEach((key, value) -> publishRow(islandUuid, IslandAddonService.tableStateKey(safeTable, key), value));
    }

    void publishGlobalRow(String key, String value) {
        if (suspended || coreGlobalStateWriter == null || key == null || key.isBlank() || value == null) {
            return;
        }
        coreGlobalStateWriter.accept(new DatabaseService.CoreGlobalRowWrite(key, value));
    }

    void publishGlobalTable(String table, Map<String, String> values) {
        if (suspended || table == null || table.isBlank() || values == null) {
            return;
        }
        if (coreGlobalBulkWriter != null && !values.isEmpty()) {
            coreGlobalBulkWriter.accept(new DatabaseService.CoreGlobalBulkWrite(Map.of(), Map.of(table, Map.copyOf(values))));
            return;
        }
        if (coreGlobalTableWriter != null) {
            coreGlobalTableWriter.accept(new DatabaseService.CoreGlobalTableWrite(table, Map.copyOf(values)));
            return;
        }
        if (values.isEmpty()) {
            return;
        }
        values.forEach((key, value) -> publishGlobalRow(IslandAddonService.TABLE_STATE_KEY_PREFIX + table + "/" + key, value));
    }
}

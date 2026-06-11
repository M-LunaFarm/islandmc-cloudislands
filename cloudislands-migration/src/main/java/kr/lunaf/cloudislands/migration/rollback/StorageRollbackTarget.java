package kr.lunaf.cloudislands.migration.rollback;

import java.util.UUID;

public final class StorageRollbackTarget implements MigrationRollbackService.RollbackTarget {
    private final ImportedIslandStorageRemover remover;

    public StorageRollbackTarget(ImportedIslandStorageRemover remover) {
        this.remover = remover;
    }

    @Override
    public void removeImportedIsland(UUID islandId) {
        remover.removeIslandStorage(islandId);
    }

    public interface ImportedIslandStorageRemover {
        void removeIslandStorage(UUID islandId);
    }
}

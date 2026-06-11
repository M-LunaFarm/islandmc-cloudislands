package kr.lunaf.cloudislands.migration.rollback;

import java.util.List;
import java.util.UUID;

public final class CompositeRollbackTarget implements MigrationRollbackService.RollbackTarget {
    private final List<MigrationRollbackService.RollbackTarget> targets;

    public CompositeRollbackTarget(List<MigrationRollbackService.RollbackTarget> targets) {
        this.targets = List.copyOf(targets);
    }

    @Override
    public void removeImportedIsland(UUID islandId) {
        RuntimeException failure = null;
        for (MigrationRollbackService.RollbackTarget target : targets) {
            try {
                target.removeImportedIsland(islandId);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

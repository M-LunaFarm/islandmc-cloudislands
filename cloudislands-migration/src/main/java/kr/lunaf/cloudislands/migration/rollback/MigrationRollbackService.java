package kr.lunaf.cloudislands.migration.rollback;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationIssue;

public final class MigrationRollbackService {
    public RollbackResult rollback(MigrationRollbackPlan plan, RollbackTarget target) {
        List<MigrationIssue> issues = new ArrayList<>();
        int rolledBack = 0;
        for (UUID islandId : plan.importedIslandIds()) {
            try {
                target.removeImportedIsland(islandId);
                rolledBack++;
            } catch (RuntimeException exception) {
                issues.add(new MigrationIssue("ROLLBACK_FAILED", islandId + ": " + exception.getMessage(), true));
            }
        }
        return new RollbackResult(issues.isEmpty(), rolledBack, issues);
    }

    public interface RollbackTarget {
        void removeImportedIsland(UUID islandId);
    }

    public record RollbackResult(boolean rolledBack, int removedIslands, List<MigrationIssue> issues) {}
}

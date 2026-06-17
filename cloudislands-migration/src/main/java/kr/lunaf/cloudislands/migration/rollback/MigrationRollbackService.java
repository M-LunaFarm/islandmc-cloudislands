package kr.lunaf.cloudislands.migration.rollback;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationIssue;

public final class MigrationRollbackService {
    public RollbackResult rollback(MigrationRollbackPlan plan, RollbackTarget target) {
        List<MigrationIssue> issues = new ArrayList<>();
        if (plan == null) {
            issues.add(new MigrationIssue("ROLLBACK_PLAN_REQUIRED", "migration rollback requires a rollback plan", true));
            return new RollbackResult(false, 0, List.copyOf(issues));
        }
        if (target == null) {
            issues.add(new MigrationIssue("ROLLBACK_TARGET_REQUIRED", "migration rollback requires a target", true));
            return new RollbackResult(false, 0, List.copyOf(issues));
        }
        int rolledBack = 0;
        for (UUID islandId : plan.importedIslandIds()) {
            if (islandId == null) {
                issues.add(new MigrationIssue("ROLLBACK_INVALID_ISLAND_ID", "rollback plan contains a null island id", true));
                continue;
            }
            try {
                target.removeImportedIsland(islandId);
                rolledBack++;
            } catch (RuntimeException exception) {
                issues.add(new MigrationIssue("ROLLBACK_FAILED", islandId + ": " + exception.getMessage(), true));
            }
        }
        return new RollbackResult(issues.isEmpty(), rolledBack, List.copyOf(issues));
    }

    public interface RollbackTarget {
        void removeImportedIsland(UUID islandId);
    }

    public record RollbackResult(boolean rolledBack, int removedIslands, List<MigrationIssue> issues) {}
}

package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;

public final class DirtyRankingRecalculationTask {
    private static final Logger LOGGER = Logger.getLogger(DirtyRankingRecalculationTask.class.getName());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "cloudislands-ranking-recalc");
        thread.setDaemon(true);
        return thread;
    });
    private final RankingRepository rankings;
    private final IslandLevelRepository levels;
    private final IslandMetadataRepository metadata;
    private final RankingRecalculationService recalculation;

    public DirtyRankingRecalculationTask(RankingRepository rankings, IslandLevelRepository levels, IslandMetadataRepository metadata, RankingRecalculationService recalculation) {
        this.rankings = rankings;
        this.levels = levels;
        this.metadata = metadata;
        this.recalculation = recalculation;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::runOnce, 10L, 30L, TimeUnit.SECONDS);
    }

    public void runOnce() {
        for (UUID islandId : rankings.drainDirty(100)) {
            try {
                recalculation.recalculate(islandId, levels.blockCounts(islandId), levels.blockValues(), metadata.members(islandId).size());
            } catch (RuntimeException exception) {
                rankings.markDirty(islandId);
                LOGGER.log(Level.WARNING, "failed to recalculate dirty island ranking " + islandId, exception);
            }
        }
    }
}

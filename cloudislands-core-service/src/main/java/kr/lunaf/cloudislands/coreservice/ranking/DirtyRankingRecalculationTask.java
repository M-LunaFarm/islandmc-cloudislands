package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;

public final class DirtyRankingRecalculationTask {
    private static final Logger LOGGER = Logger.getLogger(DirtyRankingRecalculationTask.class.getName());
    public static final int BATCH_LIMIT = 100;
    public static final long INITIAL_DELAY_SECONDS = 10L;
    public static final long PERIOD_SECONDS = 30L;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "cloudislands-ranking-recalc");
        thread.setDaemon(true);
        return thread;
    });
    private final RankingRepository rankings;
    private final IslandLevelRepository levels;
    private final IslandMetadataRepository metadata;
    private final RankingRecalculationService recalculation;
    private final AtomicLong drainedTotal = new AtomicLong();
    private final AtomicLong recalculatedTotal = new AtomicLong();
    private final AtomicLong failuresTotal = new AtomicLong();
    private final AtomicLong lastBatchSize = new AtomicLong();

    public DirtyRankingRecalculationTask(RankingRepository rankings, IslandLevelRepository levels, IslandMetadataRepository metadata, RankingRecalculationService recalculation) {
        this.rankings = rankings;
        this.levels = levels;
        this.metadata = metadata;
        this.recalculation = recalculation;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::runOnce, INITIAL_DELAY_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public void runOnce() {
        java.util.List<UUID> dirty = rankings.drainDirty(BATCH_LIMIT);
        lastBatchSize.set(dirty.size());
        drainedTotal.addAndGet(dirty.size());
        for (UUID islandId : dirty) {
            try {
                recalculation.recalculate(islandId, levels.blockCounts(islandId), levels.blockValues(), metadata.members(islandId).size());
                recalculatedTotal.incrementAndGet();
            } catch (RuntimeException exception) {
                failuresTotal.incrementAndGet();
                rankings.markDirty(islandId);
                LOGGER.log(Level.WARNING, "failed to recalculate dirty island ranking " + islandId, exception);
            }
        }
    }

    public long drainedTotal() {
        return drainedTotal.get();
    }

    public long recalculatedTotal() {
        return recalculatedTotal.get();
    }

    public long failuresTotal() {
        return failuresTotal.get();
    }

    public long lastBatchSize() {
        return lastBatchSize.get();
    }
}

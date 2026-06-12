package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;

public final class JobRecoveryMonitor {
    private static final Logger LOGGER = Logger.getLogger(JobRecoveryMonitor.class.getName());
    private final IslandJobQueue jobs;
    private final Duration interval;
    private final long minIdleMillis;
    private final int maxJobs;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile long lastFailureLogMillis;

    public JobRecoveryMonitor(IslandJobQueue jobs, Duration interval, long minIdleMillis, int maxJobs) {
        this.jobs = jobs;
        this.interval = interval == null || interval.isNegative() || interval.isZero() ? Duration.ofSeconds(60) : interval;
        this.minIdleMillis = Math.max(1000L, minIdleMillis);
        this.maxJobs = Math.max(1, maxJobs);
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-job-recovery-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!recoverable() || !started.compareAndSet(false, true)) {
            return;
        }
        long delayMillis = Math.max(1000L, interval.toMillis());
        executor.scheduleWithFixedDelay(this::sweep, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void sweep() {
        try {
            String recovered = recover();
            if (hasRecoveredJobs(recovered)) {
                LOGGER.info("CloudIslands recovered stale island jobs: " + recovered);
            }
        } catch (RuntimeException exception) {
            logSweepFailure(exception);
        }
    }

    private boolean recoverable() {
        return jobs instanceof JdbcIslandJobQueue || jobs instanceof RedisIslandJobQueue;
    }

    private String recover() {
        if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
            return jdbcJobs.recoverPending("core-recovery", minIdleMillis, maxJobs);
        }
        if (jobs instanceof RedisIslandJobQueue redisJobs) {
            return redisJobs.recoverPending("core-recovery", minIdleMillis, maxJobs);
        }
        return "";
    }

    private boolean hasRecoveredJobs(String recovered) {
        if (recovered == null || recovered.isBlank()) {
            return false;
        }
        String trimmed = recovered.trim();
        return !trimmed.equals("[]") && !trimmed.equals("()") && !trimmed.equals("(nil)");
    }

    private void logSweepFailure(RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogMillis < 30_000L) {
            return;
        }
        lastFailureLogMillis = now;
        LOGGER.warning("CloudIslands stale job recovery failed: " + exception.getMessage());
    }
}

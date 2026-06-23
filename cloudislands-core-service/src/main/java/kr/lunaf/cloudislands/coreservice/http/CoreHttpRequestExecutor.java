package kr.lunaf.cloudislands.coreservice.http;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class CoreHttpRequestExecutor implements Executor, AutoCloseable {
    private static final ThreadLocal<Boolean> SATURATED_REQUEST = ThreadLocal.withInitial(() -> false);

    private final ThreadPoolExecutor executor;
    private final Semaphore admission;
    private final int queueCapacity;
    private final AtomicLong submittedTotal = new AtomicLong();
    private final AtomicLong completedTotal = new AtomicLong();
    private final AtomicLong rejectedTotal = new AtomicLong();

    public CoreHttpRequestExecutor(int workerThreads, int queueCapacity, Duration keepAlive) {
        this(workerThreads, queueCapacity, keepAlive, namedThreadFactory("cloudislands-core-http"));
    }

    CoreHttpRequestExecutor(int workerThreads, int queueCapacity, Duration keepAlive, ThreadFactory threadFactory) {
        int workers = Math.max(1, workerThreads);
        int boundedQueueCapacity = Math.max(0, queueCapacity);
        long keepAliveMillis = Math.max(1000L, keepAlive == null ? 30_000L : keepAlive.toMillis());
        BlockingQueue<Runnable> queue = boundedQueueCapacity == 0
            ? new SynchronousQueue<>()
            : new ArrayBlockingQueue<>(boundedQueueCapacity);
        this.executor = new ThreadPoolExecutor(
            workers,
            workers,
            keepAliveMillis,
            TimeUnit.MILLISECONDS,
            queue,
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
        this.admission = new Semaphore(workers + boundedQueueCapacity);
        this.queueCapacity = boundedQueueCapacity;
    }

    public static boolean saturatedRequest() {
        return SATURATED_REQUEST.get();
    }

    @Override
    public void execute(Runnable command) {
        submittedTotal.incrementAndGet();
        if (!admission.tryAcquire()) {
            rejectedTotal.incrementAndGet();
            runSaturated(command);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    command.run();
                } finally {
                    completedTotal.incrementAndGet();
                    admission.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            admission.release();
            rejectedTotal.incrementAndGet();
            runSaturated(command);
        }
    }

    public int workerThreads() {
        return executor.getMaximumPoolSize();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int activeRequests() {
        return executor.getActiveCount();
    }

    public int queuedRequests() {
        return executor.getQueue().size();
    }

    public long submittedTotal() {
        return submittedTotal.get();
    }

    public long completedTotal() {
        return completedTotal.get();
    }

    public long rejectedTotal() {
        return rejectedTotal.get();
    }

    public void shutdownGracefully(Duration grace) {
        executor.shutdown();
        long millis = Math.max(0L, grace == null ? 0L : grace.toMillis());
        try {
            if (!executor.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @Override
    public void close() {
        shutdownGracefully(Duration.ofSeconds(5));
    }

    private static void runSaturated(Runnable command) {
        boolean previous = SATURATED_REQUEST.get();
        SATURATED_REQUEST.set(true);
        try {
            command.run();
        } finally {
            SATURATED_REQUEST.set(previous);
        }
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}

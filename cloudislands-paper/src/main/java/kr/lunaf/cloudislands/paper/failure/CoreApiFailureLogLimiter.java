package kr.lunaf.cloudislands.paper.failure;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/** Combines recurring Core connection failures so one outage does not flood the server console. */
public final class CoreApiFailureLogLimiter {
    private static final long SUMMARY_INTERVAL_MILLIS = 300_000L;
    private static final Map<Plugin, CoreApiFailureLogLimiter> INSTANCES = new WeakHashMap<>();

    private final Logger logger;
    private final Set<String> affectedComponents = new LinkedHashSet<>();
    private long outageStartedAtMillis;
    private long lastSummaryAtMillis;
    private long suppressedFailures;

    private CoreApiFailureLogLimiter(Logger logger) {
        this.logger = logger;
    }

    public static CoreApiFailureLogLimiter forPlugin(Plugin plugin) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(plugin, key -> new CoreApiFailureLogLimiter(key.getLogger()));
        }
    }

    public synchronized void failed(String component, Throwable error, long retryMillis) {
        long now = System.currentTimeMillis();
        affectedComponents.add(component == null || component.isBlank() ? "unknown" : component);
        if (outageStartedAtMillis == 0L) {
            outageStartedAtMillis = now;
            lastSummaryAtMillis = now;
            logger.warning("CloudIslands Core API is unavailable; dependent features are paused and will retry automatically"
                + " (component=" + component + ", retry=" + Math.max(1L, retryMillis) + "ms, cause=" + failureMessage(error) + ")."
                + " Check core-api.base-url and make sure the Core service is running. Repeated connection errors will be summarized every 5 minutes.");
            return;
        }
        suppressedFailures++;
        if (now - lastSummaryAtMillis < SUMMARY_INTERVAL_MILLIS) {
            return;
        }
        long outageSeconds = Math.max(1L, (now - outageStartedAtMillis) / 1000L);
        logger.warning("CloudIslands Core API is still unavailable after " + outageSeconds + "s; suppressed "
            + suppressedFailures + " repeated failures (affected=" + String.join(",", affectedComponents)
            + ", latestCause=" + failureMessage(error) + ").");
        suppressedFailures = 0L;
        lastSummaryAtMillis = now;
    }

    public synchronized void recovered(String component) {
        if (outageStartedAtMillis == 0L) {
            return;
        }
        long outageSeconds = Math.max(1L, (System.currentTimeMillis() - outageStartedAtMillis) / 1000L);
        logger.info("CloudIslands Core API connection recovered after " + outageSeconds + "s via " + component + ".");
        outageStartedAtMillis = 0L;
        lastSummaryAtMillis = 0L;
        suppressedFailures = 0L;
        affectedComponents.clear();
    }

    private static String failureMessage(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause == null) {
            return "unknown";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.replace('\n', ' ').replace('\r', ' ');
    }
}

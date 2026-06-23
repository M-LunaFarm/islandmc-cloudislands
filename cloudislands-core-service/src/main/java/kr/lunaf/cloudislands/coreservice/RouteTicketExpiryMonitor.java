package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;

public final class RouteTicketExpiryMonitor {
    private static final Logger LOGGER = Logger.getLogger(RouteTicketExpiryMonitor.class.getName());
    private final RouteTicketStore tickets;
    private final GlobalEventPublisher events;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile long lastFailureLogMillis;

    public RouteTicketExpiryMonitor(RouteTicketStore tickets, GlobalEventPublisher events, Duration interval) {
        this.tickets = tickets;
        this.events = events;
        this.interval = interval == null || interval.isNegative() || interval.isZero() ? Duration.ofSeconds(30) : interval;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-route-ticket-expiry");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long delayMillis = Math.max(1000L, interval.toMillis());
        executor.scheduleWithFixedDelay(this::sweep, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void sweep() {
        try {
            for (RouteTicket ticket : tickets.expireStale()) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticket.ticketId().toString(),
                    "playerUuid", ticket.playerUuid().toString(),
                    "islandId", ticket.islandId().toString(),
                    "action", ticket.action().name(),
                    "targetNode", ticket.targetNode(),
                    "reason", "TICKET_EXPIRED"
                ));
            }
        } catch (RuntimeException exception) {
            logSweepFailure(exception);
        }
    }

    private void logSweepFailure(RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogMillis < 30_000L) {
            return;
        }
        lastFailureLogMillis = now;
        LOGGER.warning("CloudIslands route ticket expiry sweep failed: " + exception.getMessage());
    }
}

package kr.lunaf.cloudislands.paper.gui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class GuiActionDedupePolicy {
    static final long DEFAULT_WINDOW_MILLIS = 500L;

    private final long windowMillis;
    private final ConcurrentHashMap<UUID, LastAction> lastActions = new ConcurrentHashMap<>();

    GuiActionDedupePolicy() {
        this(DEFAULT_WINDOW_MILLIS);
    }

    GuiActionDedupePolicy(long windowMillis) {
        this.windowMillis = Math.max(0L, windowMillis);
    }

    boolean accept(UUID playerId, GuiAction action, GuiClick click, long nowMillis) {
        if (playerId == null || action == null) {
            return false;
        }
        if (windowMillis == 0L) {
            return true;
        }
        String fingerprint = action.stableFingerprint(click);
        AtomicBoolean accepted = new AtomicBoolean(true);
        lastActions.compute(playerId, (_id, previous) -> {
            if (previous != null && previous.fingerprint().equals(fingerprint) && nowMillis - previous.atMillis() < windowMillis) {
                accepted.set(false);
                return previous;
            }
            accepted.set(true);
            return new LastAction(fingerprint, nowMillis);
        });
        return accepted.get();
    }

    private record LastAction(String fingerprint, long atMillis) {
    }
}

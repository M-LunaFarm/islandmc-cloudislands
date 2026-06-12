package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public interface CloudGlobalEvent {
    Instant occurredAt();
}

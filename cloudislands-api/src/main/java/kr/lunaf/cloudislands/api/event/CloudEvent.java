package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public interface CloudEvent {
    Instant occurredAt();
}

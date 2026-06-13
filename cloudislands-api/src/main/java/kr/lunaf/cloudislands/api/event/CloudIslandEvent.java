package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public interface CloudIslandEvent extends CloudEvent {
    UUID islandId();
}

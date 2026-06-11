package kr.lunaf.cloudislands.coreservice.event;

import java.util.Map;

public interface GlobalEventPublisher {
    void publish(String eventType, Map<String, String> fields);
}

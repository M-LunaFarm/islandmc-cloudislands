package kr.lunaf.cloudislands.velocity.security;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class PluginMessageFirewall {
    private final AtomicLong blockedMessages = new AtomicLong();

    public void handle(PluginMessageEvent event) {
        String channel = event.getIdentifier().getId();
        String identifier = event.getIdentifier().toString();
        if (isCloudIslandsPluginMessage(channel) || isCloudIslandsPluginMessage(identifier)) {
            blockedMessages.incrementAndGet();
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    public long blockedMessages() {
        return blockedMessages.get();
    }

    public boolean isCloudIslandsPluginMessage(String channel) {
        if (channel == null) {
            return false;
        }
        String normalized = channel.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("cloudislands") || normalized.startsWith("cloudislands:");
    }
}

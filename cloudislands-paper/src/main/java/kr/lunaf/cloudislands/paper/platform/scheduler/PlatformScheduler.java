package kr.lunaf.cloudislands.paper.platform.scheduler;

import java.time.Duration;
import java.util.UUID;

public interface PlatformScheduler extends AutoCloseable {
    TaskHandle runGlobal(Runnable task);

    TaskHandle runAsync(Runnable task);

    TaskHandle runForPlayer(UUID playerId, Runnable task);

    TaskHandle runForChunk(String worldKey, int chunkX, int chunkZ, Runnable task);

    TaskHandle repeatGlobal(Duration delay, Duration interval, Runnable task);

    @Override
    void close();
}

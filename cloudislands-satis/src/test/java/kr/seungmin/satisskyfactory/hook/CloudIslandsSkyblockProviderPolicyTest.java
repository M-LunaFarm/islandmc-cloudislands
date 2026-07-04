package kr.seungmin.satisskyfactory.hook;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIslandsSkyblockProviderPolicyTest {
    @Test
    void providerDoesNotJoinCoreApiFuturesOnBukkitCallPaths() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/hook/CloudIslandsSkyblockProvider.java"));

        assertFalse(source.contains(".join()"));
        assertTrue(source.contains("OFF_THREAD_CORE_WAIT_MS"));
        assertTrue(source.contains("plugin.getServer().isPrimaryThread()"));
        assertTrue(source.contains("return Optional.empty();"));
    }
}

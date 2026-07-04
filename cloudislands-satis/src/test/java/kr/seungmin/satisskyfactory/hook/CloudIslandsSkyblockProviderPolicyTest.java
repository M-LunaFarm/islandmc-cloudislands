package kr.seungmin.satisskyfactory.hook;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIslandsSkyblockProviderPolicyTest {
    @Test
    void providerUsesCacheOnlySyncApiAndNeverWaitsForCoreApiFutures() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/hook/CloudIslandsSkyblockProvider.java"));

        assertFalse(source.contains(".join()"));
        assertFalse(source.contains("future.get("));
        assertFalse(source.contains(".get(OFF_THREAD_CORE_WAIT_MS"));
        assertFalse(source.contains("OFF_THREAD_CORE_WAIT_MS"));
        assertFalse(source.contains("TimeUnit.MILLISECONDS"));
        assertFalse(source.contains("TimeoutException"));
        assertFalse(source.contains("waitOptional"));
        assertTrue(source.contains("future.thenAccept"));
        assertTrue(source.contains("return Optional.empty();"));
        assertTrue(source.contains("return Optional.ofNullable(cached);"));
    }
}

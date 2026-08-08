package kr.seungmin.satisskyfactory.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigResourceSavePolicyTest {
    @Test
    void existingConfigFilesAreNotPassedToSaveResource() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/config/ConfigService.java"));

        int existenceCheck = source.indexOf("if (!file.isFile())");
        int saveResource = source.indexOf("plugin.saveResource(fileName, false)");
        assertTrue(existenceCheck >= 0 && existenceCheck < saveResource, source);
    }
}

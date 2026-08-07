package kr.lunaf.cloudislands.coreservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoreBasicMysqlConfigTest {
    @Test
    void basicModeSelectsTheBundledMysqlProfile() {
        String previous = System.getProperty("cloudislands.mode");
        System.setProperty("cloudislands.mode", "BASIC");
        try {
            CoreServiceConfig config = CoreServiceConfig.fromEnvironment();

            assertEquals("MYSQL", config.configuredDatabaseType());
            assertTrue(config.jdbcUrl().startsWith("jdbc:mysql://127.0.0.1:3306/cloudislands"));
            assertEquals("JDBC", config.repositoryMode());
            assertEquals("JDBC", config.jobQueueMode());
            assertTrue(config.setupDatabaseAutoSchema());
            assertEquals("LOCAL_FILESYSTEM", config.storageType());
            assertEquals("development", config.runtimeMode());
        } finally {
            if (previous == null) {
                System.clearProperty("cloudislands.mode");
            } else {
                System.setProperty("cloudislands.mode", previous);
            }
        }
    }
}

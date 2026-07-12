package kr.lunaf.cloudislands.common.packaging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminPermissionDeploymentWiringTest {
    @Test
    void composeAndHelmForwardServerSideAdminPermissions() throws IOException {
        Path root = repositoryRoot();
        String compose = Files.readString(root.resolve("deploy/compose/docker-compose.yml"));
        String singlePaper = Files.readString(root.resolve("deploy/examples/single-paper/docker-compose.yml"));
        String helmValues = Files.readString(root.resolve("deploy/helm/cloudislands/values.yaml"));
        String helmWorkloads = Files.readString(root.resolve("deploy/helm/cloudislands/templates/workloads.yaml"));
        String productionConfig = Files.readString(root.resolve("deploy/examples/production-ha/config-pack.yml"));

        assertTrue(compose.split("CI_ADMIN_PERMISSIONS: \\Q${CI_ADMIN_PERMISSIONS:-audit-read}\\E", -1).length - 1 >= 2);
        assertTrue(singlePaper.contains("CI_ADMIN_PERMISSIONS: ${CI_ADMIN_PERMISSIONS:-audit-read}"));
        assertTrue(helmValues.contains("adminPermissions: \"audit-read\""));
        assertTrue(helmWorkloads.contains("name: CI_ADMIN_PERMISSIONS"));
        assertTrue(helmWorkloads.contains(".Values.core.adminPermissions"));
        assertTrue(compose.split("CI_PUBLIC_ADMIN_API_ENABLED: \\Q${CI_PUBLIC_ADMIN_API_ENABLED:-true}\\E", -1).length - 1 >= 2);
        assertTrue(singlePaper.contains("CI_PUBLIC_ADMIN_API_ENABLED: ${CI_PUBLIC_ADMIN_API_ENABLED:-true}"));
        assertTrue(helmValues.contains("publicAdminApiEnabled: true"));
        assertTrue(productionConfig.contains("public-admin-api-enabled: true"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}

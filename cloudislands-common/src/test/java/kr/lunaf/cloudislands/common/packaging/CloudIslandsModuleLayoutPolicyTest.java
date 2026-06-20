package kr.lunaf.cloudislands.common.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CloudIslandsModuleLayoutPolicyTest {
    @Test
    void listsTheRequiredGradleModulesInPackageOrder() {
        assertEquals(
            List.of(
                "cloudislands-api",
                "cloudislands-common",
                "cloudislands-protocol",
                "cloudislands-core-client",
                "cloudislands-core-service",
                "cloudislands-velocity",
                "cloudislands-paper",
                "cloudislands-storage",
                "cloudislands-migration",
                "cloudislands-testkit",
                "cloudislands-bom"
            ),
            CloudIslandsModuleLayoutPolicy.requiredModules()
        );
    }

    @Test
    void treatsSatisAsAnOptionalExtensionModule() {
        assertEquals(List.of("cloudislands-satis"), CloudIslandsModuleLayoutPolicy.optionalExtensionModules());
        assertFalse(CloudIslandsModuleLayoutPolicy.requiredModule("cloudislands-satis"));
        assertTrue(CloudIslandsModuleLayoutPolicy.optionalExtensionModule("cloudislands-satis"));
        assertTrue(CloudIslandsModuleLayoutPolicy.knownModule("cloudislands-satis"));
    }

    @Test
    void recordsModuleResponsibilitiesFromThePackagePlan() {
        assertEquals(
            List.of("interfaces", "events", "dto-snapshots", "permission-enums", "service-contracts", "published-javadocs"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-api")
        );
        assertEquals(
            List.of("rest-grpc-server", "postgresql-repositories", "redis-event-bus", "routing-allocator", "job-scheduler", "transaction-manager", "admin-api"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-core-service")
        );
        assertEquals(
            List.of("lobby-role", "island-node-role", "protection", "world-shard-cell-manager", "teleport-manager", "gui", "events"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-paper")
        );
        assertEquals(
            List.of("superiorskyblock2-importer", "dry-run-validator", "world-extractor", "report-generator"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-migration")
        );
        assertEquals(
            List.of("satismc-feature-bridge", "config-gated-addon-runtime", "legacy-feature-migration", "addon-descriptor-sidecar"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-satis")
        );
        assertEquals(
            List.of("fixtures", "fake-repositories", "integration-test-helpers", "api-contract-verifier", "addon-certification-fixtures"),
            CloudIslandsModuleLayoutPolicy.moduleResponsibilities().get("cloudislands-testkit")
        );
    }

    @Test
    void recordsAddonDistributionAsJarAndDescriptorSidecar() {
        assertEquals(
            List.of("cloudislands-satis"),
            CloudIslandsModuleLayoutPolicy.distributionTasks().get("distAddons")
        );
        assertEquals(
            List.of("cloudislands-satis-descriptor"),
            CloudIslandsModuleLayoutPolicy.distributionTasks().get("distAddonDescriptors")
        );
        assertEquals(
            List.of("addons", "addon-descriptors"),
            CloudIslandsModuleLayoutPolicy.distributionTasks().get("distAddonBundle")
        );
        assertEquals(
            List.of("cloudislands-api-javadoc", "cloudislands-common-javadoc", "cloudislands-protocol-javadoc", "cloudislands-core-client-javadoc", "cloudislands-storage-javadoc", "cloudislands-migration-javadoc", "cloudislands-testkit-javadoc"),
            CloudIslandsModuleLayoutPolicy.distributionArtifacts().get("javadocs")
        );
        assertEquals(
            List.of("module-poms", "gradle-module-metadata", "runtime-jars", "sources-jars", "javadoc-jars"),
            CloudIslandsModuleLayoutPolicy.distributionArtifacts().get("maven-repository")
        );
        assertTrue(CloudIslandsModuleLayoutPolicy.distributionTasks().get("distDeveloperKit").contains("javadoc-jars"));
        assertTrue(CloudIslandsModuleLayoutPolicy.distributionTasks().get("distDeveloperKit").contains("maven-repository"));
    }

    @Test
    void rejectsUnknownModules() {
        assertTrue(CloudIslandsModuleLayoutPolicy.knownModule("cloudislands-core-client"));
        assertFalse(CloudIslandsModuleLayoutPolicy.knownModule("cloudislands-docs"));
        assertFalse(CloudIslandsModuleLayoutPolicy.knownModule(null));
    }
}

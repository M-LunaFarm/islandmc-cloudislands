package kr.lunaf.cloudislands.coreservice;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoreRouteDomainCoverageTest {
    private static final Path ROUTES_DIR = Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes");
    private static final Path APPLICATION = Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/CloudIslandsCoreApplication.java");

    @Test
    void everyRouteClassIsRegisteredByApplication() throws Exception {
        Set<String> routeClasses = routeClasses();
        Set<String> registeredClasses = registeredRouteClasses(applicationSource());

        assertEquals(routeClasses, registeredClasses, "Every Core route class must be registered by CloudIslandsCoreApplication");
    }

    @Test
    void routeRegistrationsUseRealDomainServicesAndRepositories() throws Exception {
        String application = applicationSource();
        Map<String, List<String>> dependencies = requiredRouteDependencies();

        assertEquals(routeClasses(), dependencies.keySet(), "Every Core route class must declare route-to-domain dependency coverage");

        for (Map.Entry<String, List<String>> expectation : dependencies.entrySet()) {
            String registration = registrationBlock(application, expectation.getKey());
            for (String dependency : expectation.getValue()) {
                assertTrue(
                    registration.contains(dependency),
                    expectation.getKey() + " must be wired to " + dependency + " in the application route registration"
                );
            }
        }
    }

    private static String applicationSource() throws IOException {
        return Files.readString(APPLICATION);
    }

    private static Set<String> routeClasses() throws IOException {
        Set<String> classes = new TreeSet<>();
        try (Stream<Path> files = Files.list(ROUTES_DIR)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Routes.java")).toList()) {
                Matcher matcher = Pattern.compile("public final class (\\w+Routes)").matcher(Files.readString(file));
                assertTrue(matcher.find(), file + " must declare a public final route class");
                classes.add(matcher.group(1));
            }
        }
        return classes;
    }

    private static Set<String> registeredRouteClasses(String application) {
        Matcher matcher = Pattern.compile("new\\s+(\\w+Routes)\\s*\\(").matcher(application);
        Set<String> classes = new TreeSet<>();
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        return classes;
    }

    private static String registrationBlock(String application, String routeClass) {
        String marker = "new " + routeClass + "(";
        int start = application.indexOf(marker);
        assertTrue(start >= 0, routeClass + " must be registered by CloudIslandsCoreApplication");
        int end = application.indexOf(").register", start);
        assertTrue(end > start, routeClass + " registration must call register");
        return application.substring(start, end);
    }

    private static Map<String, List<String>> requiredRouteDependencies() {
        return Map.ofEntries(
            entry("AddonRoutes", List.of("addonStates", "audit", "events")),
            entry("AdminGeneratorRoutes", List.of("generatorRepository", "audit", "events")),
            entry("AdminIslandLifecycleRoutes", List.of("domainServices.islandLifecycle()", "domainServices.islandDeleteService()")),
            entry("AdminNodeRoutes", List.of("nodes", "nodeFailureMonitor")),
            entry("AdminRuntimeRoutes", List.of("sessions", "tickets")),
            entry("AuditRoutes", List.of("audit")),
            entry("CoreConfigRoutes", List.of("config", "nodes")),
            entry("EventRoutes", List.of("inMemoryEvents")),
            entry("GeneratorRoutes", List.of("generatorRepository", "upgradeRepository")),
            entry("HealthRoutes", List.of("domainServices.metrics()::render", "readinessProbes(config, dataSource, deleteStorage, nodes)")),
            entry("IslandBankRoutes", List.of("bankRepository", "permissionRules")),
            entry("IslandBlockLevelRoutes", List.of("domainServices.levelRecalculation()")),
            entry("IslandCatalogRoutes", List.of("domainServices.createIsland()")),
            entry("IslandCommunicationRoutes", List.of("islandLogs", "playerProfiles", "events")),
            entry("IslandMemberRoutes", List.of("limitRepository", "permissionRules", "playerProfiles")),
            entry("IslandPlayerLifecycleRoutes", List.of("domainServices.islandLifecycle()", "domainServices.islandDeleteService()")),
            entry("IslandQueryRoutes", List.of("runtimeRepository", "domainServices.levelRecalculation()", "domainServices.islandDeleteService()")),
            entry("IslandReviewRoutes", List.of("reviewRepository", "islandRepository")),
            entry("IslandSettingsRoutes", List.of("metadataRepository", "permissionRules")),
            entry("IslandSnapshotRoutes", List.of("snapshotRepository", "runtimeRepository")),
            entry("IslandUpgradeRoutes", List.of("domainServices.upgradeService()", "domainServices.upgradePolicy()")),
            entry("IslandVisitorRoutes", List.of("limitRepository", "permissionRules")),
            entry("IslandWarehouseRoutes", List.of("warehouseRepository", "permissionRules")),
            entry("IslandWarpRoutes", List.of("metadataRepository", "limitRepository")),
            entry("JobRoutes", List.of("jobs", "domainServices.jobCompletion()")),
            entry("NodeRoutes", List.of("nodes", "runtimeRepository")),
            entry("PermissionRoleRoutes", List.of("permissionRules", "roleRepository")),
            entry("PlayerProfileRoutes", List.of("playerProfiles", "audit")),
            entry("ProgressionRoutes", List.of("domainServices.upgradePolicy()", "levelRepository", "missionRepository")),
            entry("ProtocolRoutes", List.of("nodes")),
            entry("RoutePreparationRoutes", List.of("domainServices.routing()")),
            entry("RouteTicketRoutes", List.of("routing", "tickets", "sessions")),
            entry("SuperiorSkyblock2MigrationRoutes", List.of("domainServices.migrationAdmin()")),
            entry("TemplateRoutes", List.of("templateRepository", "events"))
        );
    }
}

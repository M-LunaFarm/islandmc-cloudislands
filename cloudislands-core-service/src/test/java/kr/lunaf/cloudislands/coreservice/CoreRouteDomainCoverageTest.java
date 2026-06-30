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
    private static final Path ROUTE_MODULES = Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/CoreRouteModules.java");

    @Test
    void everyRouteClassIsRegisteredByCoreRouteModules() throws Exception {
        Set<String> routeClasses = routeClasses();
        Set<String> registeredClasses = registeredRouteClasses(routeModuleSource());

        assertEquals(routeClasses, registeredClasses, "Every Core route class must be registered by CoreRouteModules");
    }

    @Test
    void routeRegistrationsUseRealDomainServicesAndRepositories() throws Exception {
        String routes = routeModuleSource();
        Map<String, List<String>> dependencies = requiredRouteDependencies();

        assertEquals(routeClasses(), dependencies.keySet(), "Every Core route class must declare route-to-domain dependency coverage");

        for (Map.Entry<String, List<String>> expectation : dependencies.entrySet()) {
            String registration = registrationBlock(routes, expectation.getKey());
            for (String dependency : expectation.getValue()) {
                assertTrue(
                    registration.contains(dependency),
                    expectation.getKey() + " must be wired to " + dependency + " in CoreRouteModules"
                );
            }
        }
    }

    @Test
    void applicationDelegatesRouteRegistrationToCoreRouteModules() throws Exception {
        String application = Files.readString(APPLICATION);

        assertTrue(application.contains("CoreRouteModules.register"));
        assertTrue(!application.contains("new RoutePreparationRoutes("));
        assertTrue(!application.contains("new HealthRoutes("));
    }

    private static String routeModuleSource() throws IOException {
        return Files.readString(ROUTE_MODULES);
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
        assertTrue(start >= 0, routeClass + " must be registered by CoreRouteModules");
        int end = application.indexOf(").register", start);
        assertTrue(end > start, routeClass + " registration must call register");
        return application.substring(start, end);
    }

    private static Map<String, List<String>> requiredRouteDependencies() {
        return Map.ofEntries(
            entry("AddonRoutes", List.of("repositories.addonStates()", "repositories.audit()", "events")),
            entry("AdminGeneratorRoutes", List.of("repositories.generatorRepository()", "repositories.audit()", "events")),
            entry("AdminIslandLifecycleRoutes", List.of("domainServices.islandLifecycle()", "domainServices.islandDeleteService()")),
            entry("AdminNodeRoutes", List.of("repositories.nodes()", "nodeFailureMonitor")),
            entry("AdminRuntimeRoutes", List.of("repositories.sessions()", "repositories.tickets()")),
            entry("AdminSupportBundleRoutes", List.of("config", "repositories.nodes()", "repositories.jobs()", "repositories.tickets()", "repositories.sessions()", "repositories.inMemoryEvents()", "infrastructure.dataSource()", "deleteStorage")),
            entry("AuditRoutes", List.of("repositories.audit()")),
            entry("CoreConfigRoutes", List.of("config", "repositories.nodes()")),
            entry("EventRoutes", List.of("repositories.inMemoryEvents()")),
            entry("GeneratorRoutes", List.of("repositories.generatorRepository()", "repositories.upgradeRepository()", "repositories.islandRepository()")),
            entry("HealthRoutes", List.of("domainServices.metrics()::render", "readinessProbes(config, infrastructure.dataSource(), deleteStorage, repositories.nodes())")),
            entry("IslandBankRoutes", List.of("repositories.bankRepository()", "repositories.permissionRules()")),
            entry("IslandBlockLevelRoutes", List.of("domainServices.levelRecalculation()")),
            entry("IslandCatalogRoutes", List.of("domainServices.createIsland()")),
            entry("IslandCommunicationRoutes", List.of("repositories.islandLogs()", "repositories.playerProfiles()", "events")),
            entry("IslandMemberRoutes", List.of("repositories.limitRepository()", "repositories.permissionRules()", "repositories.playerProfiles()")),
            entry("IslandPlayerLifecycleRoutes", List.of("domainServices.islandLifecycle()", "domainServices.islandDeleteService()")),
            entry("IslandQueryRoutes", List.of("repositories.runtimeRepository()", "domainServices.levelRecalculation()", "domainServices.islandDeleteService()")),
            entry("IslandReviewRoutes", List.of("repositories.reviewRepository()", "repositories.islandRepository()")),
            entry("IslandSettingsRoutes", List.of("repositories.metadataRepository()", "repositories.permissionRules()")),
            entry("IslandSnapshotRoutes", List.of("repositories.snapshotRepository()", "repositories.runtimeRepository()")),
            entry("IslandUpgradeRoutes", List.of("domainServices.upgradeService()", "domainServices.upgradePolicy()")),
            entry("IslandVisitorRoutes", List.of("repositories.limitRepository()", "repositories.permissionRules()")),
            entry("IslandWarehouseRoutes", List.of("repositories.warehouseRepository()", "repositories.permissionRules()")),
            entry("IslandWarpRoutes", List.of("repositories.metadataRepository()", "repositories.limitRepository()")),
            entry("JobRoutes", List.of("repositories.jobs()", "domainServices.jobCompletion()")),
            entry("NodeRoutes", List.of("repositories.nodes()", "repositories.runtimeRepository()")),
            entry("PermissionRoleRoutes", List.of("repositories.permissionRules()", "repositories.roleRepository()")),
            entry("PlayerProfileRoutes", List.of("repositories.playerProfiles()", "repositories.audit()")),
            entry("ProgressionRoutes", List.of("domainServices.upgradePolicy()", "repositories.levelRepository()", "repositories.missionRepository()")),
            entry("ProtocolRoutes", List.of("repositories.nodes()")),
            entry("RoutePreparationRoutes", List.of("domainServices.routing()")),
            entry("RouteTicketRoutes", List.of("domainServices.routing()", "repositories.tickets()", "repositories.sessions()")),
            entry("SuperiorSkyblock2MigrationRoutes", List.of("domainServices.migrationAdmin()")),
            entry("TemplateRoutes", List.of("repositories.templateRepository()", "events"))
        );
    }
}

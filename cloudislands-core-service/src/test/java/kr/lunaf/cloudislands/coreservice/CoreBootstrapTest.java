package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import org.junit.jupiter.api.Test;

class CoreBootstrapTest {
    @Test
    void bootstrapExposesTypedCompositionStages() throws NoSuchMethodException {
        assertEquals(
            CoreInfrastructure.class,
            CoreBootstrap.class.getDeclaredMethod("infrastructure", CoreServiceConfig.class, Clock.class, Logger.class).getReturnType()
        );
        assertEquals(
            CoreRepositories.class,
            CoreBootstrap.class.getDeclaredMethod("repositories", CoreServiceConfig.class, Clock.class, CoreInfrastructure.class).getReturnType()
        );
    }

    @Test
    void compositionStagesAreImmutableRecords() {
        assertImmutableRecord(CoreInfrastructure.class);
        assertImmutableRecord(CoreRepositories.class);
        assertImmutableRecord(CoreDomainServices.class);
        assertImmutableRecord(CoreHttpRuntime.class);
        assertImmutableRecord(CoreBackgroundTasks.class);
        assertImmutableRecord(CoreLifecycle.class);
    }

    private static void assertImmutableRecord(Class<?> type) {
        assertTrue(type.isRecord(), type.getName());
        assertTrue(Modifier.isFinal(type.getModifiers()), type.getName());
    }
}

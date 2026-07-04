package kr.seungmin.satisskyfactory.runtime;

public final class SatisRuntimeBootstrap {
    public RuntimeBootstrapDecision decide(RuntimeBootstrapSnapshot snapshot) {
        boolean startRuntime = snapshot.addonRegistrationAccepted();
        return new RuntimeBootstrapDecision(
                startRuntime,
                !startRuntime,
                !startRuntime && snapshot.cloudIslandsApiMissing()
        );
    }

    public record RuntimeBootstrapSnapshot(
            boolean addonRegistrationAccepted,
            boolean cloudIslandsApiMissing
    ) {
    }

    public record RuntimeBootstrapDecision(
            boolean startRuntime,
            boolean unregisterCommands,
            boolean disablePlugin
    ) {
    }
}

package kr.lunaf.cloudislands.coreservice;

import java.util.List;

public record CoreLifecycle(CoreHttpRuntime httpRuntime, CoreBackgroundTasks backgroundTasks) {
    void start() {
        CoreLifecycleActions.start(actions());
    }

    void stop() {
        CoreLifecycleActions.stop(actions());
    }

    List<String> startOrder() {
        return CoreLifecycleActions.startOrder(actions());
    }

    List<String> stopOrder() {
        return CoreLifecycleActions.stopOrder(actions());
    }

    private List<CoreLifecycleAction> actions() {
        return List.of(
            new CoreLifecycleAction("httpRuntime", httpRuntime::start, httpRuntime::stop),
            new CoreLifecycleAction("backgroundTasks", backgroundTasks::start, backgroundTasks::stop)
        );
    }
}

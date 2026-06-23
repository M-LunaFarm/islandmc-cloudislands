package kr.lunaf.cloudislands.coreservice;

record CoreLifecycleAction(String name, Runnable startAction, Runnable stopAction) {
    void start() {
        startAction.run();
    }

    void stop() {
        stopAction.run();
    }
}

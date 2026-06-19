package kr.lunaf.cloudislands.paper.platform.scheduler;

public interface TaskHandle {
    void cancel();

    static TaskHandle noop() {
        return () -> {
        };
    }
}

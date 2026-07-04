package kr.seungmin.satisskyfactory.runtime;

import kr.seungmin.satisskyfactory.task.DirtySaveService;
import kr.seungmin.satisskyfactory.task.MachineTickService;
import kr.seungmin.satisskyfactory.task.MaintenanceTickService;

public final class SatisRuntimeLifecycle {
    public RuntimeTasks stop(RuntimeTasks tasks, StopMode mode) {
        RuntimeTasks safeTasks = tasks == null ? RuntimeTasks.empty() : tasks;
        if (safeTasks.machineTicker() != null) {
            safeTasks.machineTicker().stop();
        }
        if (safeTasks.maintenanceTicker() != null) {
            safeTasks.maintenanceTicker().stop();
        }
        DirtySaveService dirtySaves = safeTasks.dirtySaves();
        if (dirtySaves != null) {
            dirtySaves.stop();
            if (mode.discardDirtySaves()) {
                dirtySaves.discard();
            }
            if (mode.detachCorePublishers()) {
                dirtySaves.coreStatePublisher(null);
                dirtySaves.coreStateDeletePublisher(null);
            }
        }
        return mode.clearDirtySaves() ? RuntimeTasks.empty() : new RuntimeTasks(null, null, dirtySaves);
    }

    public void startDirtySaves(DirtySaveService dirtySaves, boolean writesEnabled, long periodTicks) {
        if (dirtySaves != null && writesEnabled) {
            dirtySaves.start(periodTicks);
        }
    }

    public record RuntimeTasks(
            MachineTickService machineTicker,
            MaintenanceTickService maintenanceTicker,
            DirtySaveService dirtySaves
    ) {
        public static RuntimeTasks empty() {
            return new RuntimeTasks(null, null, null);
        }
    }

    public enum StopMode {
        STOP_ONLY(false, false, false),
        STOP_AND_DISCARD(true, false, false),
        STOP_DISCARD_DETACH_AND_CLEAR(true, true, true);

        private final boolean discardDirtySaves;
        private final boolean detachCorePublishers;
        private final boolean clearDirtySaves;

        StopMode(boolean discardDirtySaves, boolean detachCorePublishers, boolean clearDirtySaves) {
            this.discardDirtySaves = discardDirtySaves;
            this.detachCorePublishers = detachCorePublishers;
            this.clearDirtySaves = clearDirtySaves;
        }

        public boolean discardDirtySaves() {
            return discardDirtySaves;
        }

        public boolean detachCorePublishers() {
            return detachCorePublishers;
        }

        public boolean clearDirtySaves() {
            return clearDirtySaves;
        }
    }
}

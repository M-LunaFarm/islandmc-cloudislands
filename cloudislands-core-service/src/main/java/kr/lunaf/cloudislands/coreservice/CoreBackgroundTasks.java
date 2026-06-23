package kr.lunaf.cloudislands.coreservice;

import java.util.List;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxDispatcher;
import kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask;

public record CoreBackgroundTasks(
    NodeFailureMonitor nodeFailureMonitor,
    RouteTicketExpiryMonitor routeTicketExpiryMonitor,
    JobRecoveryMonitor jobRecoveryMonitor,
    JobCompletionOutboxDispatcher completionOutboxDispatcher,
    DirtyRankingRecalculationTask rankingRecalculationTask
) {
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
            new CoreLifecycleAction("nodeFailureMonitor", nodeFailureMonitor::start, nodeFailureMonitor::stop),
            new CoreLifecycleAction("routeTicketExpiryMonitor", routeTicketExpiryMonitor::start, routeTicketExpiryMonitor::stop),
            new CoreLifecycleAction("jobRecoveryMonitor", jobRecoveryMonitor::start, jobRecoveryMonitor::stop),
            new CoreLifecycleAction("completionOutboxDispatcher", completionOutboxDispatcher::start, completionOutboxDispatcher::stop),
            new CoreLifecycleAction("rankingRecalculationTask", rankingRecalculationTask::start, rankingRecalculationTask::stop)
        );
    }
}

package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxDispatcher;
import kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask;

public record CoreBackgroundTasks(
    NodeFailureMonitor nodeFailureMonitor,
    RouteTicketExpiryMonitor routeTicketExpiryMonitor,
    JobRecoveryMonitor jobRecoveryMonitor,
    JobCompletionOutboxDispatcher completionOutboxDispatcher,
    DirtyRankingRecalculationTask rankingRecalculationTask
) {}

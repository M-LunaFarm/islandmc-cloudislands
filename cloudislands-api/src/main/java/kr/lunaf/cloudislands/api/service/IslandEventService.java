package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;

public interface IslandEventService {
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents();
}

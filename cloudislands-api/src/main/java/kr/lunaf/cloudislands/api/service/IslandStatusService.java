package kr.lunaf.cloudislands.api.service;

import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CloudIslandsStatusSnapshot;

public interface IslandStatusService {
    CompletableFuture<CloudIslandsStatusSnapshot> current();
}

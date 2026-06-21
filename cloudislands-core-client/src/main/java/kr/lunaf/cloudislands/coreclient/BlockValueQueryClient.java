package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BlockValueQueryClient {
    CompletableFuture<List<BlockValueView>> list();
}

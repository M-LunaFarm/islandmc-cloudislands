package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TemplateQueryClient {
    CompletableFuture<List<TemplateView>> list();
}

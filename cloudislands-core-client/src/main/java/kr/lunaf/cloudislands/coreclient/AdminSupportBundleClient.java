package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminSupportBundleClient {
    CompletableFuture<String> create();
}

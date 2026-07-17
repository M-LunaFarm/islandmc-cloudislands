package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;

@FunctionalInterface
public interface ShardWorldProvisioner {
    void ensureLoaded(String worldName) throws IOException;

    static ShardWorldProvisioner noop() {
        return _worldName -> { };
    }
}

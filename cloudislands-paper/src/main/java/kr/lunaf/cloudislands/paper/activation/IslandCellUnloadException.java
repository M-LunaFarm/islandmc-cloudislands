package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;

public final class IslandCellUnloadException extends IOException {
    private static final long serialVersionUID = 1L;

    public IslandCellUnloadException(String message) {
        super(message);
    }

    public IslandCellUnloadException(String message, Throwable cause) {
        super(message, cause);
    }
}

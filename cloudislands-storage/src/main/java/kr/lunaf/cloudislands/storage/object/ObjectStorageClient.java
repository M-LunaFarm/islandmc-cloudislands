package kr.lunaf.cloudislands.storage.object;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface ObjectStorageClient {
    boolean available() throws IOException;

    InputStream openObject(String key) throws IOException;

    ObjectStoragePutResult putObject(String key, InputStream body, ObjectStoragePutOptions options) throws IOException;

    void deleteObject(String key) throws IOException;

    List<String> listObjects(String prefix) throws IOException;

    ObjectStorageMetrics objectMetrics();
}

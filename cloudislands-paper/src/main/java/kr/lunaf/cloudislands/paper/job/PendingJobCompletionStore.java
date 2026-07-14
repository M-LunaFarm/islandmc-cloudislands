package kr.lunaf.cloudislands.paper.job;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Durable local-success journal used to replay only the Core completion report. */
final class PendingJobCompletionStore {
    private static final int MAGIC = 0x43494A43;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;
    private static final int MAX_FIELDS = 512;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;

    private final Path file;
    private final Map<UUID, Map<String, String>> entries = new LinkedHashMap<>();

    PendingJobCompletionStore(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    synchronized Optional<Map<String, String>> find(UUID jobId) {
        return Optional.ofNullable(entries.get(jobId));
    }

    synchronized void put(UUID jobId, Map<String, String> payload) throws IOException {
        if (jobId == null) {
            throw new IOException("pending completion job id is missing");
        }
        Map<String, String> previous = entries.put(jobId, sanitized(payload));
        try {
            persist();
        } catch (IOException exception) {
            restore(jobId, previous);
            throw exception;
        }
    }

    synchronized void remove(UUID jobId) throws IOException {
        Map<String, String> previous = entries.remove(jobId);
        if (previous != null) {
            try {
                persist();
            } catch (IOException exception) {
                entries.put(jobId, previous);
                throw exception;
            }
        }
    }

    synchronized int size() {
        return entries.size();
    }

    private Map<String, String> sanitized(Map<String, String> payload) throws IOException {
        if (payload == null || payload.size() > MAX_FIELDS) {
            throw new IOException("pending completion payload field limit exceeded");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IOException("pending completion payload contains null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }

    private void restore(UUID jobId, Map<String, String> previous) {
        if (previous == null) {
            entries.remove(jobId);
        } else {
            entries.put(jobId, previous);
        }
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("invalid pending completion journal: " + file);
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                throw new IOException("unsupported pending completion journal: " + file);
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IOException("invalid pending completion record count: " + count);
            }
            for (int index = 0; index < count; index++) {
                UUID jobId = UUID.fromString(input.readUTF());
                int fields = input.readInt();
                if (fields < 0 || fields > MAX_FIELDS) {
                    throw new IOException("invalid pending completion field count: " + fields);
                }
                Map<String, String> payload = new LinkedHashMap<>();
                for (int field = 0; field < fields; field++) {
                    payload.put(input.readUTF(), input.readUTF());
                }
                if (entries.putIfAbsent(jobId, Map.copyOf(payload)) != null) {
                    throw new IOException("duplicate pending completion job: " + jobId);
                }
            }
            if (input.read() != -1) {
                throw new IOException("trailing data in pending completion journal: " + file);
            }
        } catch (EOFException | IllegalArgumentException exception) {
            throw new IOException("corrupt pending completion journal: " + file, exception);
        }
    }

    private void persist() throws IOException {
        if (entries.size() > MAX_RECORDS) {
            throw new IOException("pending completion record limit exceeded");
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileOutputStream raw = new FileOutputStream(temporary.toFile());
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(raw))) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(entries.size());
                for (Map.Entry<UUID, Map<String, String>> entry : entries.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList()) {
                    output.writeUTF(entry.getKey().toString());
                    output.writeInt(entry.getValue().size());
                    for (Map.Entry<String, String> field : entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                        output.writeUTF(field.getKey());
                        output.writeUTF(field.getValue());
                    }
                }
                output.flush();
                raw.getFD().sync();
            }
            if (Files.size(temporary) > MAX_FILE_BYTES) {
                throw new IOException("pending completion journal size limit exceeded");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

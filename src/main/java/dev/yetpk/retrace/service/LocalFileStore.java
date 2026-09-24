package dev.yetpk.retrace.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(@Value("${retrace.storage.root}") String storageRoot) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage root " + root, e);
        }
    }

    /**
     * Stores the given content under a freshly generated storage key and returns that key.
     * The stream is copied to a temp file inside the store root first, so the final move
     * is atomic on a single filesystem.
     */
    @Override
    public UUID store(UUID projectId, InputStream content) {
        UUID key = UUID.randomUUID();
        Path target = pathFor(projectId, key);
        Path tempFile;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile(root, "upload-", ".tmp");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            long written = Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (written == 0) {
                throw new IllegalArgumentException("Refusing to store empty content");
            }
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(tempFile);
            throw e;
        }
        return key;
    }

    @Override
    public InputStream retrieve(UUID projectId, UUID storageKey) {
        try {
            return Files.newInputStream(pathFor(projectId, storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path pathFor(UUID projectId, UUID storageKey) {
        return root.resolve(projectId.toString()).resolve("artifacts").resolve(storageKey.toString());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup of a temp file
        }
    }
}

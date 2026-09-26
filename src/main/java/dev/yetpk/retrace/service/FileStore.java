package dev.yetpk.retrace.service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Stores artifact file content, addressed by project and a generated storage key.
 * Implementations are free to back this with local disk, S3, or any other provider.
 */
public interface FileStore {

    UUID storeContent(UUID projectId, InputStream content);

    InputStream retrieveContent(UUID projectId, UUID storageKey);
}

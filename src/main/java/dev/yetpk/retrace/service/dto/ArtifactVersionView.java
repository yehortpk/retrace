package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One stored version of an artifact. {@code ordinal} is the version's identity within its artifact;
 * a version is never addressed by its own id.
 */
public record ArtifactVersionView(UUID artifactId, String artifactName, int ordinal, String label,
                                  String filename, String contentType, long sizeBytes,
                                  OffsetDateTime createdAt) {
}

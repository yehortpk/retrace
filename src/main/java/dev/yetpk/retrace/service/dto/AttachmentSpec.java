package dev.yetpk.retrace.service.dto;

import java.util.UUID;
import org.springframework.core.io.InputStreamSource;

/**
 * One file to attach to an entry being recorded. The artifact it belongs to is named either by
 * {@code artifactId} (an existing artifact, which must live in the same project) or by
 * {@code artifactName}, which always creates a new artifact — a name is a current label, not an
 * identity to look an artifact up by.
 *
 * <p>Content is passed as an {@link InputStreamSource} rather than bytes so nothing buffers the
 * whole upload: a {@code MultipartFile} satisfies it as is, and the service opens and closes the
 * stream when it stores the file.
 */
public record AttachmentSpec(UUID artifactId, String artifactName, String label, String filename,
                             String contentType, long sizeBytes, InputStreamSource content) {

    public AttachmentSpec {
        if (artifactId == null && (artifactName == null || artifactName.isBlank())) {
            throw new IllegalArgumentException("An attachment needs either an artifactId or an artifactName");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("An attachment needs a positive size");
        }
    }
}

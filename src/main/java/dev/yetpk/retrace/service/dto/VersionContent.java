package dev.yetpk.retrace.service.dto;

import java.io.InputStream;

/**
 * An open stream over a stored version, with what a download response needs to describe it.
 * The caller owns the stream and must close it.
 */
public record VersionContent(InputStream content, String filename, String contentType, long sizeBytes) {
}

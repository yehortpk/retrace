package dev.yetpk.retrace.service.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything one recorded entry carries. The same command shape backs the UI form and the agent's
 * {@code curl}: nothing here says which of the two produced the entry, because nothing should.
 *
 * @param occurredAt when it happened; {@code null} means now
 */
public record RecordEntryCommand(OffsetDateTime occurredAt, String description, String note, String sessionId,
                                 List<AttachmentSpec> attachments) {

    public RecordEntryCommand {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("An entry needs a description");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

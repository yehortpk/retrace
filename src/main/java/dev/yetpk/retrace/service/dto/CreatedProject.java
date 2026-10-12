package dev.yetpk.retrace.service.dto;

import dev.yetpk.retrace.security.IssuedApiKey;

/**
 * A new project together with the key issued with it — the only moment that key's plaintext exists.
 * A project never exists without a way to connect an agent to it.
 */
public record CreatedProject(ProjectSummary project, IssuedApiKey key) {
}

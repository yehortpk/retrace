package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.ApiKey;

/**
 * A freshly issued key together with its plaintext value — the only moment the plaintext exists.
 * Only {@code bcrypt(secret)} is persisted, so a key that is not shown to its owner here can never
 * be recovered.
 *
 * @param value the full credential to present as {@code X-API-Key}, formatted {@code <id>.<secret>}
 */
public record IssuedApiKey(ApiKey key, String value) {
}

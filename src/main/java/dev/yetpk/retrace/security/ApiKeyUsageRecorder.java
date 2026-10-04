package dev.yetpk.retrace.security;

import dev.yetpk.retrace.repo.ApiKeyRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stamps {@code api_key.last_used_at} away from the request that authenticated.
 *
 * <p>This lives in its own bean rather than as a method on {@link ApiKeyService} for a reason that is
 * easy to get wrong: {@code @Async} and {@code @Transactional} are applied by a proxy, so a call
 * {@code ApiKeyService} made to itself would run inline in the caller's read-only transaction and
 * silently do neither. Going through another bean is what makes the boundary real.
 *
 * <p>A write per authenticated request has no business in the latency budget of the agent's
 * {@code curl}, and failing to record a timestamp must never fail a request whose credential was
 * valid — so the failure is logged and swallowed here.
 */
@Component
public class ApiKeyUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyUsageRecorder.class);

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyUsageRecorder(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Async
    @Transactional
    public void recordUsage(String keyId) {
        try {
            apiKeyRepository.findById(keyId).ifPresent(key -> key.setLastUsedAt(OffsetDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Could not record last use of API key {}", keyId, e);
        }
    }
}

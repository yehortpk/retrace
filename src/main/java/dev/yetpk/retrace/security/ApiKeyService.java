package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.service.error.NotFoundException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns API key issuance, authentication, and revocation, and is the only class that ever sees a
 * plaintext secret. A stored key is an id, a name, and {@code bcrypt(secret)} — the secret itself is
 * returned once at issuance and then unrecoverable.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String ID_PREFIX = "pm_";
    private static final int ID_SUFFIX_LENGTH = 12;
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_BYTES = 32;
    private static final char SEPARATOR = '.';

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyUsageRecorder usageRecorder;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder,
                         ApiKeyUsageRecorder usageRecorder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
        this.usageRecorder = usageRecorder;
    }

    /**
     * Issues a key for {@code owner} and returns its plaintext value exactly once. The value is
     * {@code <id>.<secret>}: the id is a public handle safe to list and log, the secret is 32 random
     * bytes that only ever exist in the returned string and in the caller's hands.
     */
    @Transactional
    public IssuedApiKey issueKey(AppUser owner, String name) {
        String id = generateId();
        String secret = generateSecret();
        ApiKey key = apiKeyRepository.save(new ApiKey(id, owner, name, passwordEncoder.encode(secret)));
        log.info("Issued API key {} ('{}') for user {}", id, name, owner.getId());
        return new IssuedApiKey(key, id + SEPARATOR + secret);
    }

    /**
     * Authenticates a presented {@code <id>.<secret>} credential, returning the owning user.
     *
     * <p>Every way of being wrong — malformed, unknown id, revoked, wrong secret — produces the same
     * empty result, so an id cannot be probed for existence by the shape of the failure.
     * {@code last_used_at} is recorded off the request path: a write per authenticated request has
     * no business sitting in the latency budget of the agent's {@code curl}.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> authenticate(String presented) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }
        // Split on the first separator only: the secret is URL-safe base64 and contains no '.', but
        // taking the first is the unambiguous rule regardless.
        int separator = presented.indexOf(SEPARATOR);
        if (separator <= 0 || separator == presented.length() - 1) {
            return Optional.empty();
        }
        String id = presented.substring(0, separator);
        String secret = presented.substring(separator + 1);

        Optional<ApiKey> found = apiKeyRepository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApiKey key = found.get();
        if (key.isRevoked() || !passwordEncoder.matches(secret, key.getSecretHash())) {
            return Optional.empty();
        }
        usageRecorder.recordUsage(id);
        // Touch the owner inside the transaction so the caller gets an initialized user, not a proxy
        // that would fail to load once this read-only transaction has closed.
        AppUser owner = key.getOwner();
        owner.getUsername();
        return Optional.of(owner);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> findKeys(AppUser owner) {
        return apiKeyRepository.findActiveByOwnerId(owner.getId());
    }

    /**
     * Revokes one of {@code owner}'s keys. A key belonging to somebody else is reported exactly like
     * one that does not exist — the same answer the rest of the application gives for a project or an
     * artifact owned by another user.
     */
    @Transactional
    public void revokeKey(AppUser owner, String keyId) {
        ApiKey key = apiKeyRepository.findByIdAndOwnerId(keyId, owner.getId())
                .orElseThrow(() -> new NotFoundException("No API key with id '%s'".formatted(keyId)));
        if (!key.isRevoked()) {
            key.revoke(OffsetDateTime.now());
            log.info("Revoked API key {} for user {}", keyId, owner.getId());
        }
    }

    private String generateId() {
        StringBuilder id = new StringBuilder(ID_PREFIX);
        for (int i = 0; i < ID_SUFFIX_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    private String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}

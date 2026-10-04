package dev.yetpk.retrace.security;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.service.error.CredentialTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a user and, in the same transaction, issues the key that lets an agent start recording.
 *
 * <p>Registration issuing a key is deliberate: the product's thesis is that the agent does the
 * remembering, and a new user who has to make a second trip to a settings page before their agent
 * can write anything has a setup step between them and the whole point.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    /** The name given to the key every new user is registered with. */
    public static final String DEFAULT_KEY_NAME = "Default key";

    private final AppUserRepository appUserRepository;
    private final ApiKeyService apiKeyService;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository appUserRepository, ApiKeyService apiKeyService,
                               PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.apiKeyService = apiKeyService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a user and returns them with the plaintext of their first key, which exists only in
     * this return value and is never recoverable afterwards.
     *
     * @throws CredentialTakenException if the username or the email is already registered
     */
    @Transactional
    public Registration register(String username, String email, String password) {
        if (appUserRepository.isUsernameTaken(username)) {
            throw new CredentialTakenException("Username '%s' is already taken".formatted(username));
        }
        if (appUserRepository.isEmailTaken(email)) {
            throw new CredentialTakenException("Email '%s' is already registered".formatted(email));
        }
        AppUser user = appUserRepository.save(new AppUser(username, email, passwordEncoder.encode(password)));
        IssuedApiKey issued = apiKeyService.issueKey(user, DEFAULT_KEY_NAME);
        log.info("Registered user {} ('{}')", user.getId(), username);
        return new Registration(user, issued);
    }

    /** A newly registered user and the one key issued with them. */
    public record Registration(AppUser user, IssuedApiKey issuedKey) {
    }
}

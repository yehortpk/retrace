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
 * Creates a user. No API key is issued here: a key belongs to a project, and a user with no project
 * has nothing for a key to point at. Creating a project issues its first key instead (see
 * {@code ProjectService#createProject}).
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a user, storing only a bcrypt hash of their password.
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
        log.info("Registered user {} ('{}')", user.getId(), username);
        return new Registration(user);
    }

    /** A newly registered user. */
    public record Registration(AppUser user) {
    }
}

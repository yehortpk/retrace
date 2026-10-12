package dev.yetpk.retrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.service.error.CredentialTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationServiceTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        apiKeyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    /** A key belongs to a project, and a user who has just registered has none for it to point at. */
    @Test
    void Register_NewUser_ShouldIssueNoKey() {
        registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThat(apiKeyRepository.count()).isZero();
    }

    @Test
    void Register_NewUser_ShouldStoreOnlyABcryptHashOfThePassword() {
        RegistrationService.Registration registration =
                registrationService.register("ada", "ada@example.test", "supersecret1");

        String storedHash = appUserRepository.findById(registration.user().getId()).orElseThrow()
                .getPasswordHash();
        assertThat(storedHash).isNotEqualTo("supersecret1");
        assertThat(passwordEncoder.matches("supersecret1", storedHash)).isTrue();
    }

    @Test
    void Register_TakenUsername_ShouldFailWithoutCreatingAUser() {
        registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThatThrownBy(() -> registrationService.register("ada", "other@example.test", "supersecret1"))
                .isInstanceOf(CredentialTakenException.class);

        assertThat(appUserRepository.count()).isEqualTo(1);
    }

    @Test
    void Register_TakenEmail_ShouldFailWithoutCreatingAUser() {
        registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThatThrownBy(() -> registrationService.register("grace", "ada@example.test", "supersecret1"))
                .isInstanceOf(CredentialTakenException.class);

        assertThat(appUserRepository.count()).isEqualTo(1);
    }
}

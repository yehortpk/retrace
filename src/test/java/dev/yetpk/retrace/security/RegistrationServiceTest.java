package dev.yetpk.retrace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.yetpk.retrace.domain.ApiKey;
import dev.yetpk.retrace.repo.ApiKeyRepository;
import dev.yetpk.retrace.repo.AppUserRepository;
import dev.yetpk.retrace.service.error.CredentialTakenException;
import java.util.List;
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

    @Test
    void Register_NewUser_ShouldIssueExactlyOneKey() {
        RegistrationService.Registration registration =
                registrationService.register("ada", "ada@example.test", "supersecret1");

        List<ApiKey> keys = apiKeyRepository.findActiveByOwnerId(registration.user().getId());
        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().getName()).isEqualTo(RegistrationService.DEFAULT_KEY_NAME);
    }

    @Test
    void Register_NewUser_ShouldStoreOnlyABcryptHashOfTheSecret() {
        RegistrationService.Registration registration =
                registrationService.register("ada", "ada@example.test", "supersecret1");

        String presented = registration.issuedKey().value();
        String secret = presented.substring(presented.indexOf('.') + 1);
        ApiKey stored = apiKeyRepository.findById(registration.issuedKey().key().getId()).orElseThrow();

        assertThat(stored.getSecretHash()).isNotEqualTo(secret);
        assertThat(stored.getSecretHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(secret, stored.getSecretHash())).isTrue();
    }

    @Test
    void Register_NewUser_ShouldReturnTheKeyAsIdAndSecret() {
        RegistrationService.Registration registration =
                registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThat(registration.issuedKey().value())
                .startsWith(registration.issuedKey().key().getId() + ".");
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
    void Register_TakenUsername_ShouldFailBeforeIssuingAKey() {
        registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThatThrownBy(() -> registrationService.register("ada", "other@example.test", "supersecret1"))
                .isInstanceOf(CredentialTakenException.class);

        assertThat(apiKeyRepository.count()).isEqualTo(1);
    }

    @Test
    void Register_TakenEmail_ShouldFailBeforeIssuingAKey() {
        registrationService.register("ada", "ada@example.test", "supersecret1");

        assertThatThrownBy(() -> registrationService.register("grace", "ada@example.test", "supersecret1"))
                .isInstanceOf(CredentialTakenException.class);

        assertThat(apiKeyRepository.count()).isEqualTo(1);
        assertThat(appUserRepository.count()).isEqualTo(1);
    }
}

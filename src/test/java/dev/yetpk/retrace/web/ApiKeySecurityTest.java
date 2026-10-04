package dev.yetpk.retrace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.yetpk.retrace.domain.AppUser;
import dev.yetpk.retrace.security.IssuedApiKey;
import dev.yetpk.retrace.security.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pins how the two credentials behave at the HTTP edge — the part of the design that is a set of
 * deliberate choices rather than framework defaults.
 */
class ApiKeySecurityTest extends WebTestSupport {

    private MockHttpSession signIn(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", "supersecret1"))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void FindProjects_ValidKey_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk());
    }

    @Test
    void FindProjects_NoCredential_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindProjects_UnknownKey_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, "pm_doesnotexist.somesecret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindProjects_MalformedKey_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, "no-separator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindProjects_RevokedKey_ShouldReturnUnauthorized() throws Exception {
        IssuedApiKey issued = issueKey(owner, "temporary");
        apiKeyService.revokeKey(owner, issued.key().getId());

        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, issued.value()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindProjects_KeyAuthentication_ShouldCreateNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void CreateProject_KeyAuthenticationWithoutCsrfToken_ShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(API_KEY_HEADER, ownerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void CreateProject_SessionWithoutCsrfToken_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(asSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void FindKeys_KeyAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/keys").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void FindKeys_SessionAuthentication_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/keys").with(asSession(owner)))
                .andExpect(status().isOk());
    }

    /**
     * A request that presents a credential and gets it wrong is a mistake its caller needs to see,
     * not an invitation to act as whoever the cookie belongs to.
     */
    @Test
    void FindProjects_InvalidKeyWithValidSession_ShouldReturnUnauthorizedRatherThanFallBack() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .with(asSession(owner))
                        .header(API_KEY_HEADER, "pm_doesnotexist.somesecret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Login_CorrectPassword_ShouldEstablishASession() throws Exception {
        registerUser("ada");

        MockHttpSession session = signIn("ada");

        mockMvc.perform(get("/api/keys").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void Login_WrongPassword_ShouldReturnUnauthorized() throws Exception {
        registerUser("ada");

        mockMvc.perform(post("/api/auth/login")
                        .param("username", "ada")
                        .param("password", "not-the-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void FindCurrentUser_KeyAndSession_ShouldIdentifyTheSameOwner() throws Exception {
        String viaKey = mockMvc.perform(get("/api/auth/me").header(API_KEY_HEADER, ownerKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String viaSession = mockMvc.perform(get("/api/auth/me").with(asSession(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(viaKey).isEqualTo(viaSession);
    }

    @Test
    void Register_TakenUsername_ShouldReturnConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"email\":\"new@example.test\","
                                + "\"password\":\"supersecret1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void Register_ShortPassword_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"email\":\"ada@example.test\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void Register_NewUser_ShouldReturnAWorkingKeyOnce() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"email\":\"ada@example.test\","
                                + "\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String issuedKey = objectMapper.readTree(body).get("apiKey").asString();
        mockMvc.perform(get("/api/projects").header(API_KEY_HEADER, issuedKey))
                .andExpect(status().isOk());
    }

    @Test
    void FindProjects_KeyOfAnotherOwner_ShouldSeeOnlyTheirOwnProjects() throws Exception {
        RegistrationService.Registration other = registerUser("other");
        AppUser otherUser = other.user();
        createProject(owner, "Mine");
        createProject(otherUser, "Theirs");

        String body = mockMvc.perform(get("/api/projects")
                        .header(API_KEY_HEADER, other.issuedKey().value()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Theirs").doesNotContain("Mine");
    }
}

package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class RegisterFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void registeringCreatesAnUnverifiedUserAndSendsAConfirmationEmail() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@x.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, AuthFlows.randomUsername(), "tortoise-violin-42"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailIgnoreCase(email)).hasValueSatisfying(user -> {
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.getUsername()).startsWith("u-");
        });
        assertThat(emailSender.lastMessageTo(email)).isNotNull();
    }

    @Test
    void rejectsARegistrationWithAnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload("not-an-email", AuthFlows.randomUsername(), "tortoise-violin-42"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPasswordShorterThanTwelveCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload("short@x.com", AuthFlows.randomUsername(), "elevenchars"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADuplicateEmailWithConflict() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@x.com";
        RegisterPayload payload = new RegisterPayload(email, AuthFlows.randomUsername(), "tortoise-violin-42");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsAUsernameTakenWithDifferentCasingWithConflict() throws Exception {
        String username = AuthFlows.randomUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload("first-" + UUID.randomUUID() + "@x.com", username, "tortoise-violin-42"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "second-" + UUID.randomUUID() + "@x.com", username.toUpperCase(), "tortoise-violin-42"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstu", "with space", " padded", "at@sign", "accentué", "emoji😀"})
    void rejectsAnInvalidUsername(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload("invalid-" + UUID.randomUUID() + "@x.com", username, "tortoise-violin-42"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefghijklmnopqrst", "Max_the-3rd"})
    void acceptsUsernamesAtTheEdgesOfTheFormat(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload("edge-" + UUID.randomUUID() + "@x.com", username, "tortoise-violin-42"))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    // bcrypt's limit is 72 *bytes*: 73 ASCII chars, 40 "é" (80 bytes) or 20 emoji (80
    // bytes) used to reach the encoder and crash it into a 500.
    @ValueSource(strings = {
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "éééééééééééééééééééééééééééééééééééééééé",
        "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"
    })
    void rejectsAPasswordLongerThanBcryptsByteLimitWithABadRequest(String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "long-" + UUID.randomUUID() + "@x.com", AuthFlows.randomUsername(), password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void acceptsAPasswordOfExactlyBcryptsByteLimit() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "max-" + UUID.randomUUID() + "@x.com", AuthFlows.randomUsername(), "a".repeat(72)))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsACommonPasswordWithADedicatedErrorCode() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "common-" + UUID.randomUUID() + "@x.com", AuthFlows.randomUsername(), "Qwerty123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_COMMON"));
    }

    @Test
    void rejectsAPasswordContainingTheUsername() throws Exception {
        String username = AuthFlows.randomUsername();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "personal-" + UUID.randomUUID() + "@x.com", username, username + "-secret"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_CONTAINS_PERSONAL_INFO"));
    }

    /** Checked before uniqueness: a refused password must not reveal that the email is taken. */
    @Test
    void aCommonPasswordIsReportedBeforeATakenEmail() throws Exception {
        String email = "taken-common-" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload(email, AuthFlows.randomUsername(), "tortoise-violin-42"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPayload(email, AuthFlows.randomUsername(), "qwerty123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_COMMON"));
    }

    private record RegisterPayload(String email, String username, String password) {}
}

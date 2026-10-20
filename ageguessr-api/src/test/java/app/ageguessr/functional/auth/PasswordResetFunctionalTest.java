package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class PasswordResetFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void resettingReplacesThePasswordAndLogsOutEveryExistingSession() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-tortoise-violin");
        LoggedInSession existingSession = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        forgotPassword(email).andExpect(status().isNoContent());
        EmailMessage resetEmail = emailSender.lastMessageTo(email);
        assertThat(resetEmail.htmlBody()).contains("/reset-password?token=");

        resetPassword(VerificationLinks.extractToken(resetEmail), "new-tortoise-violin").andExpect(status().isNoContent());

        login(email, "old-tortoise-violin").andExpect(status().isUnauthorized());
        login(email, "new-tortoise-violin").andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(existingSession.asRequestCookie()))
                .andExpect(status().isUnauthorized());
        assertThat(emailSender.lastMessageTo(email).subject()).isEqualTo("Your Ageguessr password was changed");
    }

    @Test
    void aResetLinkWorksOnlyOnce() throws Exception {
        String email = "reset-once-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-tortoise-violin");
        forgotPassword(email);
        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        resetPassword(token, "new-tortoise-violin").andExpect(status().isNoContent());
        resetPassword(token, "another-tortoise-violin").andExpect(status().isNotFound());
    }

    @Test
    void usingOneResetLinkInvalidatesTheOthers() throws Exception {
        String email = "reset-many-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-tortoise-violin");
        forgotPassword(email);
        String firstToken = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        forgotPassword(email);
        String secondToken = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        resetPassword(secondToken, "new-tortoise-violin").andExpect(status().isNoContent());
        resetPassword(firstToken, "another-tortoise-violin").andExpect(status().isNotFound());
    }

    @Test
    void resettingAnUnverifiedAccountAlsoVerifiesIt() throws Exception {
        String email = "reset-unverified-" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(AuthFlows.registerBody(objectMapper, email, "old-tortoise-violin")));
        forgotPassword(email);

        resetPassword(VerificationLinks.extractToken(emailSender.lastMessageTo(email)), "new-tortoise-violin")
                .andExpect(status().isNoContent());

        login(email, "new-tortoise-violin").andExpect(status().isOk());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        String email = "nobody-" + UUID.randomUUID() + "@x.com";

        forgotPassword(email).andExpect(status().isNoContent());

        assertThat(emailSender.lastMessageTo(email)).isNull();
    }

    @Test
    void aGarbageTokenIsNotFound() throws Exception {
        resetPassword("not-a-real-token", "new-tortoise-violin").andExpect(status().isNotFound());
    }

    @Test
    void theNewPasswordMustBeAtLeastTwelveCharacters() throws Exception {
        resetPassword("whatever", "elevenchars").andExpect(status().isBadRequest());
    }

    @Test
    void aNewPasswordOverBcryptsByteLimitIsABadRequestNotACrash() throws Exception {
        resetPassword("whatever", "é".repeat(40)).andExpect(status().isBadRequest());
    }

    /** The refusal rolls the redemption back: the same link works for a second try. */
    @Test
    void aCommonPasswordIsRefusedAndTheLinkStaysUsable() throws Exception {
        String email = "reset-common-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-tortoise-violin");
        forgotPassword(email).andExpect(status().isNoContent());
        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        resetPassword(token, "qwerty123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_COMMON"));
        login(email, "old-tortoise-violin").andExpect(status().isOk());

        resetPassword(token, "new-tortoise-violin").andExpect(status().isNoContent());
        login(email, "new-tortoise-violin").andExpect(status().isOk());
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EmailPayload(email))));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPayload(token, newPassword))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CredentialsPayload(email, password))));
    }

    private record EmailPayload(String email) {}

    private record ResetPayload(String token, String newPassword) {}

    private record CredentialsPayload(String email, String password) {}
}

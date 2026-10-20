package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class ChangePasswordFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void keepsTheCurrentSessionAndLogsOutTheOthers() throws Exception {
        String email = registered();
        LoggedInSession thisDevice = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");
        LoggedInSession otherDevice = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        changePassword(thisDevice, "old-tortoise-violin", "new-tortoise-violin").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(thisDevice.asRequestCookie())).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(otherDevice.asRequestCookie()))
                .andExpect(status().isUnauthorized());
        AuthFlows.login(mockMvc, objectMapper, email, "new-tortoise-violin");
    }

    @Test
    void theNotificationLinkLetsTheOwnerTakeTheAccountBack() throws Exception {
        String email = registered();
        LoggedInSession intruder = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        // The intruder changes the password twice: the owner's first link must survive.
        changePassword(intruder, "old-tortoise-violin", "intruder-tortoise-1").andExpect(status().isNoContent());
        String ownersLink = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        changePassword(intruder, "intruder-tortoise-1", "intruder-tortoise-2").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(ownersLink, "owner-again-tortoise"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(intruder.asRequestCookie()))
                .andExpect(status().isUnauthorized());
        AuthFlows.login(mockMvc, objectMapper, email, "owner-again-tortoise");
    }

    @Test
    void rejectsAWrongCurrentPassword() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        changePassword(session, "wrong-password", "new-tortoise-violin")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void wrongCurrentPasswordsEventuallyLockTheAccount() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        for (int i = 0; i < 5; i++) {
            changePassword(session, "wrong-password", "new-tortoise-violin").andExpect(status().isForbidden());
        }

        // Locked: even the right current password and a normal login are refused now.
        changePassword(session, "old-tortoise-violin", "new-tortoise-violin").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialsPayload(email, "old-tortoise-violin"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePayload("a-password", "new-tortoise-violin"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCommonNewPasswordIsRefusedAndTheOldOneStillWorks() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");

        changePassword(session, "old-tortoise-violin", "qwerty123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_COMMON"));

        AuthFlows.login(mockMvc, objectMapper, email, "old-tortoise-violin");
    }

    /** The current password is checked first: without it, nothing is said about the new one. */
    @Test
    void aWrongCurrentPasswordWinsOverACommonNewPassword() throws Exception {
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, registered(), "old-tortoise-violin");

        changePassword(session, "wrong-password", "qwerty123456")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
    }

    private String registered() throws Exception {
        String email = "change-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-tortoise-violin");
        return email;
    }

    private ResultActions changePassword(LoggedInSession session, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .cookie(session.asRequestCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePayload(current, next))));
    }

    private record ChangePayload(String currentPassword, String newPassword) {}

    private record ResetPayload(String token, String newPassword) {}

    private record CredentialsPayload(String email, String password) {}
}

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
        LoggedInSession thisDevice = AuthFlows.login(mockMvc, objectMapper, email, "old-password");
        LoggedInSession otherDevice = AuthFlows.login(mockMvc, objectMapper, email, "old-password");

        changePassword(thisDevice, "old-password", "new-password").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(thisDevice.asRequestCookie())).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(otherDevice.asRequestCookie()))
                .andExpect(status().isUnauthorized());
        AuthFlows.login(mockMvc, objectMapper, email, "new-password");
    }

    @Test
    void theNotificationLinkLetsTheOwnerTakeTheAccountBack() throws Exception {
        String email = registered();
        LoggedInSession intruder = AuthFlows.login(mockMvc, objectMapper, email, "old-password");

        // The intruder changes the password twice: the owner's first link must survive.
        changePassword(intruder, "old-password", "intruder-1").andExpect(status().isNoContent());
        String ownersLink = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        changePassword(intruder, "intruder-1", "intruder-2").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(ownersLink, "owner-again"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(intruder.asRequestCookie()))
                .andExpect(status().isUnauthorized());
        AuthFlows.login(mockMvc, objectMapper, email, "owner-again");
    }

    @Test
    void rejectsAWrongCurrentPassword() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "old-password");

        changePassword(session, "wrong-password", "new-password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void wrongCurrentPasswordsEventuallyLockTheAccount() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "old-password");

        for (int i = 0; i < 5; i++) {
            changePassword(session, "wrong-password", "new-password").andExpect(status().isForbidden());
        }

        // Locked: even the right current password and a normal login are refused now.
        changePassword(session, "old-password", "new-password").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialsPayload(email, "old-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePayload("a-password", "new-password"))))
                .andExpect(status().isUnauthorized());
    }

    private String registered() throws Exception {
        String email = "change-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "old-password");
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

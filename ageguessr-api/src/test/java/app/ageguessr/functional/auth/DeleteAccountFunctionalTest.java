package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.UserRepository;
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
class DeleteAccountFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private UserRepository userRepository;

    @Test
    void theAccountIsOnlyDeletedOnceTheEmailedLinkIsConfirmed() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "tortoise-violin-42");

        requestDeletion(session, "tortoise-violin-42").andExpect(status().isAccepted());
        assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();

        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        // No bearer token: the link may be opened on another, logged-out device.
        confirmDeletion(token)
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialsPayload(email, "tortoise-violin-42"))))
                .andExpect(status().isUnauthorized());
        // The access token is a self-contained JWT still within its hour: it must
        // yield a clean 404, never a 500.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auth/refresh").cookie(session.asRequestCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordSendsNoConfirmationEmail() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "tortoise-violin-42");
        String lastEmailBefore = emailSender.lastMessageTo(email).subject();

        requestDeletion(session, "wrong-password").andExpect(status().isForbidden());

        assertThat(emailSender.lastMessageTo(email).subject()).isEqualTo(lastEmailBefore);
    }

    @Test
    void aPasswordResetLinkCannotConfirmADeletion() throws Exception {
        String email = registered();
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EmailPayload(email))));
        String resetToken = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        confirmDeletion(resetToken).andExpect(status().isNotFound());
        assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void aDeletionLinkCannotResetThePassword() throws Exception {
        String email = registered();
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "tortoise-violin-42");
        requestDeletion(session, "tortoise-violin-42");
        String deletionToken = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(deletionToken, "hijacked-tortoise-violin"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestingADeletionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/me/deletion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordPayload("tortoise-violin-42"))))
                .andExpect(status().isUnauthorized());
    }

    private String registered() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "tortoise-violin-42");
        return email;
    }

    private ResultActions requestDeletion(LoggedInSession session, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/me/deletion")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordPayload(password))));
    }

    private ResultActions confirmDeletion(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/confirm-account-deletion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenPayload(token))));
    }

    private record PasswordPayload(String password) {}

    private record TokenPayload(String token) {}

    private record EmailPayload(String email) {}

    private record ResetPayload(String token, String newPassword) {}

    private record CredentialsPayload(String email, String password) {}
}

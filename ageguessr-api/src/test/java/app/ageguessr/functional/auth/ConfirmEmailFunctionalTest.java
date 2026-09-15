package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class ConfirmEmailFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void confirmingWithAnUnknownTokenReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("not-a-real-token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmingTwiceWithTheSameTokenFailsTheSecondTime() throws Exception {
        String email = "confirm-" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email)));
        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));

        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isNotFound());
    }

    private String registerBody(String email) {
        return objectMapper.writeValueAsString(new CredentialsPayload(email, "password123"));
    }

    private String tokenBody(String token) {
        return objectMapper.writeValueAsString(new TokenPayload(token));
    }

    private record CredentialsPayload(String email, String password) {}

    private record TokenPayload(String token) {}
}

package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, "password123"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailIgnoreCase(email)).hasValueSatisfying(user -> {
            assertThat(user.isEmailVerified()).isFalse();
        });
        assertThat(emailSender.lastMessageTo(email)).isNotNull();
    }

    @Test
    void rejectsARegistrationWithAnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload("not-an-email", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPasswordShorterThanEightCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload("short@x.com", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADuplicateEmailWithConflict() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@x.com";
        RegisterPayload payload = new RegisterPayload(email, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    private record RegisterPayload(String email, String password) {}
}

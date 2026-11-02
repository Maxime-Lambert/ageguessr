package app.ageguessr.functional.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Distinct (uncached) Spring context - the lowered threshold below only applies here,
 * never to the other functional tests exercising real register/login/refresh flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
@TestPropertySource(
        properties = {
            "app.rate-limit.auth.max-attempts=3",
            "app.rate-limit.auth.window-minutes=1",
            "app.rate-limit.game.max-attempts=2",
            "app.rate-limit.game.window-minutes=1"
        })
class RateLimitFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void blocksRequestsPastTheConfiguredThreshold() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentAsString())
                        .contains("RATE_LIMITED"));
    }

    /**
     * In prod every request reaches Spring from the edge Caddy container: without
     * X-Forwarded-For (set by the Caddy chain to the real client IP) all users would
     * share a single bucket. Pins that the limiter keys on that header, not on the
     * socket peer.
     */
    @Test
    void partitionsByForwardedClientIp() throws Exception {
        String exhaustedClient = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            resendVerificationFrom(exhaustedClient).andExpect(status().isAccepted());
        }
        resendVerificationFrom(exhaustedClient).andExpect(status().isTooManyRequests());

        resendVerificationFrom("203.0.113.11").andExpect(status().isAccepted());
    }

    /**
     * The guesses have their own counter: whatever they answer (here a 409, no game being
     * drawn), past the threshold they are refused before reaching the game. The reads are
     * not limited, nor do the guesses use up the login attempts.
     */
    @Test
    void limitsTheGuessesOfTheGameApart() throws Exception {
        String client = "203.0.113.20";
        for (int i = 0; i < 2; i++) {
            guessFrom(client).andExpect(status().isConflict());
        }
        guessFrom(client).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/game/today").header("X-Forwarded-For", client)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/resend-verification")
                        .header("X-Forwarded-For", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                .andExpect(status().isAccepted());
    }

    private ResultActions guessFrom(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/game/2000-01-01/rounds/1/first-guess")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guess\":30,\"language\":\"EN\"}"));
    }

    private ResultActions resendVerificationFrom(String clientIp)
            throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))));
    }

    private record EmailPayload(String email) {}
}

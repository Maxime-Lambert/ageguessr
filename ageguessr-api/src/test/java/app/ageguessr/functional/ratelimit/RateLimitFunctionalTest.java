package app.ageguessr.functional.ratelimit;

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
import tools.jackson.databind.ObjectMapper;

/**
 * Distinct (uncached) Spring context - the lowered threshold below only applies here,
 * never to the other functional tests exercising real register/login/refresh flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
@TestPropertySource(properties = {"app.rate-limit.auth.max-attempts=3", "app.rate-limit.auth.window-minutes=1"})
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

    private record EmailPayload(String email) {}
}

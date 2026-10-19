package app.ageguessr.functional.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class HealthFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void answersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    /** Guards the SecurityConfig allow-list: only /api/health was opened, not /api/** broadly. */
    @Test
    void otherApiPathsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/health-but-not-really")).andExpect(status().isUnauthorized());
    }
}

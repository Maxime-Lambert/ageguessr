package app.ageguessr.functional.hints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.auth.UserRole;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.functional.auth.AuthFlows;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** The hint curation endpoints through the real security chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class HintCurationFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private HintRepository hints;

    /** One admin per test, logged in on first use. */
    private String adminToken;

    @AfterEach
    void cleanUp() {
        hints.deleteAll();
    }

    @Test
    void everyHintEndpointIsForAdminsOnly() throws Exception {
        Hint hint = store("a", HintReviewStatus.PENDING);
        String userToken = token(UserRole.USER);
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get("/api/admin/hints/pending"),
            get("/api/admin/hints/coverage"),
            review(hint.getId(), Map.of("decision", "APPROVED"))
        }) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void listsOnlyThePendingHints() throws Exception {
        Hint pending = store("a", HintReviewStatus.PENDING);
        store("b", HintReviewStatus.APPROVED);

        mockMvc.perform(asAdmin(get("/api/admin/hints/pending")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPending").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].hintId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.items[0].category").value("MUSIC"))
                .andExpect(jsonPath("$.items[0].year").value(1976))
                .andExpect(jsonPath("$.items[0].audience").value("FR"))
                .andExpect(jsonPath("$.items[0].textFr").value("Un texte."))
                .andExpect(jsonPath("$.items[0].sourceUrl").value("https://fr.wikipedia.org/wiki/Page"));
    }

    @Test
    void refusesALimitOutOfBounds() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/hints/pending").param("limit", "0")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/admin/hints/pending").param("limit", "51")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvingWithACorrectedTextReachesTheCoverage() throws Exception {
        Hint hint = store("a", HintReviewStatus.PENDING);

        mockMvc.perform(asAdmin(review(hint.getId(), Map.of("decision", "APPROVED", "textFr", "Corrigé."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.textFr").value("Corrigé."));

        Hint reloaded = hints.findById(hint.getId()).orElseThrow();
        assertThat(reloaded.getTextFr()).isEqualTo("Corrigé.");
        assertThat(reloaded.getReviewedBy()).isNotNull();
        assertThat(reloaded.getReviewedAt()).isNotNull();
        mockMvc.perform(asAdmin(get("/api/admin/hints/coverage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimum").value(5))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.rows[?(@.year == 1976)].cells[0].category").value("MUSIC"))
                .andExpect(jsonPath("$.rows[?(@.year == 1976)].cells[0].fr").value(1))
                .andExpect(jsonPath("$.rows[?(@.year == 1976)].cells[0].en").value(0));
    }

    @Test
    void theCoverageFollowsEveryDecision() throws Exception {
        Hint first = store("a", HintReviewStatus.PENDING);
        Hint second = store("b", HintReviewStatus.PENDING);
        assertCoverage(0, 2);

        mockMvc.perform(asAdmin(review(first.getId(), Map.of("decision", "APPROVED"))))
                .andExpect(status().isOk());
        assertCoverage(1, 1);

        mockMvc.perform(asAdmin(review(second.getId(), Map.of("decision", "REJECTED"))))
                .andExpect(status().isOk());
        assertCoverage(1, 0);

        mockMvc.perform(asAdmin(review(first.getId(), Map.of("decision", "PENDING"))))
                .andExpect(status().isOk());
        assertCoverage(0, 1);
    }

    private void assertCoverage(int frenchMusic1976, int pending) throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/hints/coverage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(pending))
                .andExpect(jsonPath("$.rows[?(@.year == 1976)].cells[0].fr").value(frenchMusic1976));
    }

    @Test
    void refusesAnInvalidReview() throws Exception {
        Hint hint = store("a", HintReviewStatus.PENDING);

        mockMvc.perform(asAdmin(review(hint.getId(), Map.of("decision", "APPROVED", "textFr", "Sorti en 1976."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HINT_TEXT_CONTAINS_YEAR"));
        mockMvc.perform(asAdmin(review(hint.getId(), Map.of("decision", "APPROVED", "textFr", "a".repeat(513)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(review(hint.getId(), Map.of("textFr", "Un texte."))))
                .andExpect(status().isBadRequest());

        assertThat(hints.findById(hint.getId()).orElseThrow().getReviewStatus()).isEqualTo(HintReviewStatus.PENDING);
    }

    @Test
    void anUnknownHintIsNotFound() throws Exception {
        mockMvc.perform(asAdmin(review(UUID.randomUUID(), Map.of("decision", "APPROVED"))))
                .andExpect(status().isNotFound());
    }

    private Hint store(String key, HintReviewStatus status) {
        return hints.save(Hint.builder()
                .category(HintCategory.MUSIC)
                .year(1976)
                .audience(HintAudience.FR)
                .textFr("Un texte.")
                .sourceUrl("https://fr.wikipedia.org/wiki/Page")
                .sourceKind("chart")
                .dedupeKey(key)
                .reviewStatus(status)
                .createdAt(Instant.now())
                .build());
    }

    private MockHttpServletRequestBuilder review(UUID hintId, Map<String, String> body) {
        return put("/api/admin/hints/" + hintId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        if (adminToken == null) {
            adminToken = token(UserRole.ADMIN);
        }
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private String token(UserRole role) throws Exception {
        String email = "hints-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        users.save(user);
        return AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
    }
}

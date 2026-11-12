package app.ageguessr.functional.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.auth.UserRole;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GuestCookie;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.ReviewStatus;
import app.ageguessr.functional.auth.AuthFlows;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Player reports end to end: from a finished round of the daily game or of a series, to
 * the admin queues and the decisions that close them. The reports must hold nothing of
 * the player.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class ReportFunctionalTest {

    private static final int AGE = 40;
    private static final String PASSWORD = "tortoise-violin-42";
    private static final String ADMIN_EMAIL = "admin@ageguessr.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private UserRepository users;

    @Autowired
    private PersonRepository people;

    @Autowired
    private PersonPhotoRepository photos;

    @Autowired
    private HintRepository hints;

    @Autowired
    private DailyRoundRepository dailyRounds;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final List<PersonPhoto> seeded = new ArrayList<>();
    private String adminToken;

    /** Six photos, the first one in today's game (alone): five are left for a series. */
    @BeforeEach
    void seed() {
        today = LocalDate.now(clock);
        Continent[] boxes = Continent.values();
        for (int i = 0; i < boxes.length; i++) {
            seeded.add(savePhoto(savePerson(boxes[i], i)));
        }
        dailyRounds.save(DailyRound.builder()
                .gameDate(today)
                .position((short) 1)
                .photoId(seeded.getFirst().getId())
                .drawnAt(Instant.now())
                .build());
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(today.getYear() - AGE);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM free_series");
        jdbc.update("DELETE FROM users WHERE email LIKE 'report-%'");
        jdbc.update("DELETE FROM game_plays");
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM hints WHERE source_kind = 'functional-test'");
        people.deleteAll();
    }

    @Test
    void aGuestReportsThePhotoOfTheirFinishedRoundOnce() throws Exception {
        Cookie guest = guestCookie();
        finishExactly(guest);

        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"WRONG_PERSON\"}", guest)
                .andExpect(status().isNoContent());
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", guest)
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT reason FROM photo_reports WHERE photo_id = ?", String.class,
                        seeded.getFirst().getId()))
                .containsExactly("WRONG_PERSON");
        // Nothing of the player in the report: its columns are the target, the reason, the dates.
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'photo_reports'",
                        String.class))
                .containsExactlyInAnyOrder("id", "photo_id", "reason", "status", "created_at", "closed_at");
        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].result.photoReported").value(true))
                .andExpect(jsonPath("$.rounds[0].result.reportedHints.length()").value(0));
    }

    @Test
    void aPhotoOfTodaysGameAlertsTheAdminAtItsFirstReport() throws Exception {
        Cookie guest = guestCookie();
        finishExactly(guest);

        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"LOOKS_MINOR\"}", guest)
                .andExpect(status().isNoContent());

        EmailMessage alert = emailSender.lastMessageTo(ADMIN_EMAIL);
        assertThat(alert).isNotNull();
        assertThat(alert.subject()).contains("Photo reported", "Person 0 " + run);
        assertThat(alert.htmlBody()).contains("LOOKS_MINOR", "today's game");
    }

    @Test
    void refusesWhatCannotBeReported() throws Exception {
        Cookie guest = guestCookie();
        perform(post(dailyRound("first-guess")), "{\"guess\":30,\"language\":\"EN\"}", guest)
                .andExpect(status().isOk());

        // Not finished yet.
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", guest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROUND_NOT_FINISHED"));
        // Someone else's round.
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", guestCookie())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROUND_NOT_FINISHED"));
        // A guest without a cookie has played nothing.
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", null)
                .andExpect(status().isConflict());
        // A reason of the other list, or none of them, or no target.
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"WRONG_YEAR\"}", guest)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REASON"));
        report(dailyRound("report"), "{\"reason\":\"OTHER\"}", guest).andExpect(status().isBadRequest());
        // No such round today.
        report("/api/game/" + today + "/rounds/9/report", "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", guest)
                .andExpect(status().isNotFound());

        // Finished exactly: no hint was shown.
        Cookie exact = guestCookie();
        finishExactly(exact);
        report(dailyRound("report"), "{\"target\":\"HINT\",\"reason\":\"FALSE\"}", exact)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOTHING_TO_REPORT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_reports", Long.class)).isZero();
    }

    @Test
    void theHintOfARoundGoesToTheHintQueueAndACorrectionClosesIt() throws Exception {
        Cookie guest = guestCookie();
        perform(post(dailyRound("first-guess")), "{\"guess\":30,\"language\":\"EN\"}", guest)
                .andExpect(status().isOk());
        perform(post(dailyRound("hint")), "{\"categories\":[\"MUSIC\"]}", guest).andExpect(status().isOk());
        perform(post(dailyRound("second-guess")), "{\"guess\":40}", guest).andExpect(status().isOk());

        report(dailyRound("report"), "{\"target\":\"HINT\",\"reason\":\"WRONG_YEAR\",\"hintCategory\":\"FILM\"}", guest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOTHING_TO_REPORT"));
        report(dailyRound("report"), "{\"target\":\"HINT\",\"reason\":\"WRONG_YEAR\",\"hintCategory\":\"MUSIC\"}", guest)
                .andExpect(status().isNoContent());
        // Once per hint: without naming it, the only hint of the round, already reported.
        report(dailyRound("report"), "{\"target\":\"HINT\",\"reason\":\"FALSE\"}", guest)
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].result.reportedHints[0]").value("MUSIC"));

        assertThat(emailSender.lastMessageTo(ADMIN_EMAIL).subject()).contains("Hint reported", "MUSIC");
        MvcResult queue = mockMvc.perform(asAdmin(get("/api/admin/hints/reported")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reasons.WRONG_YEAR").value(1))
                .andExpect(jsonPath("$.items[0].total").value(1))
                .andReturn();
        String hintId = objectMapper.readTree(queue.getResponse().getContentAsString())
                .get("items").get(0).get("hintId").asString();

        mockMvc.perform(asAdmin(put("/api/admin/hints/" + hintId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"textEn\":\"A better text.\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/hints/reported"))).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM hint_reports", String.class)).isEqualTo("RESOLVED");
    }

    @Test
    void thePhotoQueueIsKeptRejectedOrEmptiedByAnExclusion() throws Exception {
        PersonPhoto photo = seeded.getFirst();
        Cookie first = guestCookie();
        finishExactly(first);
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"TOO_SMALL\"}", first)
                .andExpect(status().isNoContent());

        mockMvc.perform(asAdmin(get("/api/admin/photos/reported")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].photoId").value(photo.getId().toString()))
                .andExpect(jsonPath("$.items[0].person.nameEn").value("Person 0 " + run))
                .andExpect(jsonPath("$.items[0].reasons.TOO_SMALL").value(1))
                .andExpect(jsonPath("$.items[0].inTodaysGame").value(true));

        // Keep: out of the queue.
        mockMvc.perform(asAdmin(post("/api/admin/photos/" + photo.getId() + "/reports/dismiss")))
                .andExpect(status().isNoContent());
        mockMvc.perform(asAdmin(get("/api/admin/photos/reported"))).andExpect(jsonPath("$.items.length()").value(0));

        // Reported again, then rejected: out of the queue too.
        Cookie second = guestCookie();
        finishExactly(second);
        report(dailyRound("report"), "{\"target\":\"PHOTO\",\"reason\":\"WRONG_PERSON\"}", second)
                .andExpect(status().isNoContent());
        mockMvc.perform(asAdmin(put("/api/admin/photos/" + photo.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(get("/api/admin/photos/reported"))).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForList("SELECT status FROM photo_reports ORDER BY created_at", String.class))
                .containsExactly("DISMISSED", "RESOLVED");

        // Excluding the person deletes the photo, and its reports with it.
        mockMvc.perform(asAdmin(post("/api/admin/people/" + photo.getPersonId() + "/exclusion")))
                .andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_reports", Long.class)).isZero();
    }

    @Test
    void aRoundOfASeriesIsReportedLikeADailyOne() throws Exception {
        MvcResult started = perform(post("/api/game/series"), "{\"size\":5}", null)
                .andExpect(status().isOk())
                .andReturn();
        Cookie guest = cookieFrom(started);
        String seriesId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asString();
        String round = "/api/game/series/" + seriesId + "/rounds/1/";
        perform(post(round + "first-guess"), "{\"guess\":40,\"language\":\"EN\"}", guest).andExpect(status().isOk());

        report(round + "report", "{\"target\":\"PHOTO\",\"reason\":\"NOT_IDENTIFIABLE\"}", guest)
                .andExpect(status().isNoContent());
        // Someone else asking about this series learns nothing of it.
        report(round + "report", "{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}", guestCookie())
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/game/series/" + seriesId).cookie(guest))
                .andExpect(jsonPath("$.rounds[0].result.photoReported").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_reports WHERE reason = 'NOT_IDENTIFIABLE'",
                        Long.class))
                .isEqualTo(1);
    }

    @Test
    void theQueuesAreForAdminsOnly() throws Exception {
        String userToken = token(UserRole.USER);
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/admin/photos/reported"),
                get("/api/admin/hints/reported"),
                post("/api/admin/photos/" + UUID.randomUUID() + "/reports/dismiss"),
                post("/api/admin/hints/" + UUID.randomUUID() + "/reports/dismiss"));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/photos/reported"),
                get("/api/admin/hints/reported"),
                post("/api/admin/photos/" + UUID.randomUUID() + "/reports/dismiss"),
                post("/api/admin/hints/" + UUID.randomUUID() + "/reports/dismiss"))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    private void finishExactly(Cookie guest) throws Exception {
        perform(post(dailyRound("first-guess")), "{\"guess\":40,\"language\":\"EN\"}", guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"));
    }

    private String dailyRound(String step) {
        return "/api/game/" + today + "/rounds/1/" + step;
    }

    private ResultActions report(String url, String body, Cookie guest) throws Exception {
        return perform(post(url), body, guest);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body, Cookie guest) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (guest != null) {
            request.cookie(guest);
        }
        return mockMvc.perform(request);
    }

    private static Cookie guestCookie() {
        return new Cookie(GuestCookie.NAME, UUID.randomUUID().toString());
    }

    private static Cookie cookieFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie(GuestCookie.NAME, setCookie.substring(GuestCookie.NAME.length() + 1, setCookie.indexOf(';')));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        if (adminToken == null) {
            adminToken = token(UserRole.ADMIN);
        }
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private String token(UserRole role) throws Exception {
        String email = "report-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        users.save(user);
        return AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
    }

    private Person savePerson(Continent continent, int index) {
        return people.save(Person.builder()
                .wikidataId("Q" + (950_000 + index))
                .nameEn("Person " + index + " " + run)
                .birthDate(LocalDate.of(1950, 1, 1))
                .sitelinks(42)
                .gender(Gender.FEMALE)
                .continent(continent)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private PersonPhoto savePhoto(Person person) {
        return photos.save(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(person.getWikidataId() + ".jpg")
                .takenFrom(LocalDate.of(1990, 6, 1))
                .takenTo(LocalDate.of(1990, 6, 30))
                .ageAtPhoto(AGE)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + person.getWikidataId() + ".jpg")
                .publicId(UUID.randomUUID())
                .reviewStatus(ReviewStatus.APPROVED)
                .createdAt(Instant.now())
                .build());
    }

    private void saveHint(int year) {
        hints.save(Hint.builder()
                .category(HintCategory.MUSIC)
                .year(year)
                .audience(HintAudience.ALL)
                .textEn("A hit of that year.")
                .textFr("Un tube de cette année.")
                .sourceUrl("https://en.wikipedia.org/wiki/Hit")
                .sourceKind("functional-test")
                .dedupeKey("functional-" + UUID.randomUUID())
                .reviewStatus(HintReviewStatus.APPROVED)
                .createdAt(Instant.now())
                .build());
    }
}

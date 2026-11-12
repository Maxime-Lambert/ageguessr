package app.ageguessr.functional.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The free series end to end, against a real Postgres: the same anti-cheat surface as the
 * daily game (nothing of the answer before the end of a round), and a series stays with
 * its players.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class SeriesFunctionalTest {

    private static final int AGE = 40;
    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

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

    private final List<PersonPhoto> seeded = new ArrayList<>();
    private PersonPhoto daily;

    /** Six photos, one of them in today's daily game: five are left for a series. */
    @BeforeEach
    void seed() {
        LocalDate today = LocalDate.now(clock);
        Continent[] boxes = Continent.values();
        for (int i = 0; i < boxes.length; i++) {
            seeded.add(savePhoto(savePerson(boxes[i], i)));
        }
        daily = seeded.getFirst();
        dailyRounds.save(DailyRound.builder()
                .gameDate(today)
                .position((short) 1)
                .photoId(daily.getId())
                .drawnAt(Instant.now())
                .build());
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(today.getYear() - AGE);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM free_series");
        jdbc.update("DELETE FROM users WHERE email LIKE 'series-%'");
        jdbc.update("DELETE FROM game_plays");
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM hints WHERE source_kind = 'functional-test'");
        people.deleteAll();
    }

    @Test
    void aGuestPlaysASeriesToTheEndWithoutAnythingLeakingBefore() throws Exception {
        MvcResult started = start(5, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.rounds.length()").value(5))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"))
                .andExpect(jsonPath("$.score.max").value(5000))
                .andExpect(jsonPath("$.creator").value(true))
                .andExpect(jsonPath("$.shareCode").isEmpty())
                .andReturn();
        String body = started.getResponse().getContentAsString();
        assertNoAnswerIn(body);
        assertThat(body).doesNotContain(daily.getPublicId().toString());
        Cookie guest = cookieFrom(started);
        String seriesId = idOf(started);

        String afterFirst = perform(post(round(seriesId, 1, "first-guess")), "{\"guess\":30,\"language\":\"FR\"}", guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("AWAITING_HINT"))
                .andExpect(jsonPath("$.direction").value("OLDER"))
                .andExpect(jsonPath("$.gap").value("CLEARLY"))
                .andExpect(jsonPath("$.range.min").value(36))
                .andExpect(jsonPath("$.range.max").value(45))
                .andExpect(jsonPath("$.categories[0]").value("MUSIC"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(afterFirst);
        perform(post(round(seriesId, 1, "hint")), "{\"categories\":[\"MUSIC\"]}", guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.hints[0].text").value("Un tube de cette année."));
        perform(post(round(seriesId, 1, "second-guess")), "{\"guess\":40}", guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"))
                .andExpect(jsonPath("$.result.age").value(AGE));

        mockMvc.perform(get("/api/game/series/" + seriesId).cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[0].step").value("FINISHED"))
                .andExpect(jsonPath("$.rounds[1].step").value("NOT_STARTED"))
                .andExpect(jsonPath("$.score.complete").value(false));
    }

    @Test
    void aSeriesStaysWithItsPlayers() throws Exception {
        MvcResult started = start(5, null).andReturn();
        String seriesId = idOf(started);
        Cookie stranger = new Cookie(GuestCookie.NAME, UUID.randomUUID().toString());

        mockMvc.perform(get("/api/game/series/" + seriesId).cookie(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/game/series/" + seriesId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/game/series/" + UUID.randomUUID()).cookie(cookieFrom(started)))
                .andExpect(status().isNotFound());
        perform(post(round(seriesId, 1, "first-guess")), "{\"guess\":30,\"language\":\"EN\"}", stranger)
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesASecondFirstGuess() throws Exception {
        MvcResult started = start(5, null).andReturn();
        Cookie guest = cookieFrom(started);
        String seriesId = idOf(started);
        perform(post(round(seriesId, 1, "first-guess")), "{\"guess\":30,\"language\":\"EN\"}", guest)
                .andExpect(status().isOk());

        perform(post(round(seriesId, 1, "first-guess")), "{\"guess\":31,\"language\":\"EN\"}", guest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void picksUpTheUnfinishedSeriesUntilItIsDone() throws Exception {
        Cookie guest = new Cookie(GuestCookie.NAME, UUID.randomUUID().toString());
        mockMvc.perform(get("/api/game/series/current").cookie(guest)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/game/series/current")).andExpect(status().isNoContent());

        String seriesId = idOf(start(5, guest).andReturn());
        mockMvc.perform(get("/api/game/series/current").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seriesId));

        // Exact first guesses end each round at once.
        for (int position = 1; position <= 5; position++) {
            perform(post(round(seriesId, position, "first-guess")), "{\"guess\":40,\"language\":\"EN\"}", guest)
                    .andExpect(jsonPath("$.step").value("FINISHED"));
        }
        mockMvc.perform(get("/api/game/series/current").cookie(guest)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/game/series/" + seriesId).cookie(guest))
                .andExpect(jsonPath("$.score.points").value(5000))
                .andExpect(jsonPath("$.score.complete").value(true));
    }

    @Test
    void validatesTheLength() throws Exception {
        start(7, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SIZE"));
        perform(post("/api/game/series"), "{}", null).andExpect(status().isBadRequest());
    }

    @Test
    void saysSoWhenThePoolIsEmpty() throws Exception {
        jdbc.update("DELETE FROM daily_rounds");
        people.deleteAll();

        start(5, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("NO_PHOTOS"));
    }

    @Test
    void aChallengeComparesTheScoresOfThoseWhoFinishedIt() throws Exception {
        MvcResult started = start(5, null).andReturn();
        Cookie creator = cookieFrom(started);
        String seriesId = idOf(started);
        playAll(seriesId, creator, 40);

        share(seriesId, "{}", creator)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NICKNAME_REQUIRED"));
        String code = codeOf(share(seriesId, "{\"nickname\":\"Sherlock\"}", creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(matchesPattern("^[0-9A-HJKMNP-TV-Z]{8}$")))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.leaderboard[0].name").value("Sherlock"))
                .andExpect(jsonPath("$.leaderboard[0].points").value(5000))
                .andExpect(jsonPath("$.leaderboard[0].you").value(true))
                .andReturn());
        share(seriesId, "{\"nickname\":\"Other\"}", creator).andExpect(jsonPath("$.code").value(code));

        // A friend opens the link: who sent it, nothing else, and no cookie for reading.
        mockMvc.perform(get("/api/game/challenges/" + code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value("NOT_JOINED"))
                .andExpect(jsonPath("$.creatorName").value("Sherlock"))
                .andExpect(jsonPath("$.creatorGuest").value(true))
                .andExpect(jsonPath("$.rounds").value(5))
                .andExpect(jsonPath("$.seriesId").isEmpty())
                .andExpect(jsonPath("$.leaderboard").isEmpty());
        join(code, "{}", null).andExpect(jsonPath("$.errorCode").value("NICKNAME_REQUIRED"));
        join(code, "{\"nickname\":\"a b\"}", null).andExpect(status().isBadRequest());
        MvcResult joined = join(code, "{\"nickname\":\"Watson\"}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.seriesId").value(seriesId))
                .andExpect(jsonPath("$.leaderboard").isEmpty())
                .andReturn();
        Cookie friend = cookieFrom(joined);
        mockMvc.perform(get("/api/game/series/" + seriesId).cookie(friend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creator").value(false))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"));

        playAll(seriesId, friend, 30);
        mockMvc.perform(get("/api/game/challenges/" + code).cookie(friend))
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.leaderboard.length()").value(2))
                .andExpect(jsonPath("$.leaderboard[0].name").value("Sherlock"))
                .andExpect(jsonPath("$.leaderboard[1].name").value("Watson"))
                .andExpect(jsonPath("$.leaderboard[1].rank").value(2))
                .andExpect(jsonPath("$.leaderboard[1].you").value(true));

        // Joining again starts nothing over.
        join(code, "{\"nickname\":\"Moriarty\"}", friend)
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.leaderboard[1].name").value("Watson"));
    }

    @Test
    void anAccountJoinsUnderItsUsername() throws Exception {
        MvcResult started = start(5, null).andReturn();
        Cookie creator = cookieFrom(started);
        String seriesId = idOf(started);
        playAll(seriesId, creator, 40);
        String code = codeOf(share(seriesId, "{\"nickname\":\"Sherlock\"}", creator).andReturn());
        String email = "series-" + UUID.randomUUID() + "@x.com";
        String username = AuthFlows.randomUsername();
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, username, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();

        // A guest may not pass for this account, whatever the case.
        join(code, "{\"nickname\":\"%s\"}".formatted(username.toUpperCase()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NICKNAME_TAKEN"));

        MockHttpServletRequestBuilder join = post("/api/game/challenges/" + code + "/join")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        perform(join, "{\"nickname\":\"Ignored\"}", null)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        for (int position = 1; position <= 5; position++) {
            perform(post(round(seriesId, position, "first-guess"))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                    "{\"guess\":40,\"language\":\"EN\"}",
                    null)
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/game/challenges/" + code).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.leaderboard[1].name").value(username))
                .andExpect(jsonPath("$.leaderboard[1].guest").value(false))
                .andExpect(jsonPath("$.leaderboard[1].rank").value(1));
    }

    @Test
    void onlyTheCreatorSharesASeriesAndOnceItIsPlayed() throws Exception {
        MvcResult started = start(5, null).andReturn();
        Cookie creator = cookieFrom(started);
        String seriesId = idOf(started);

        share(seriesId, "{\"nickname\":\"Sherlock\"}", creator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SERIES_NOT_FINISHED"));
        share(seriesId, "{\"nickname\":\"Sherlock\"}", new Cookie(GuestCookie.NAME, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        playAll(seriesId, creator, 40);
        String code = codeOf(share(seriesId, "{\"nickname\":\"Sherlock\"}", creator).andReturn());
        Cookie friend = cookieFrom(join(code, "{\"nickname\":\"Watson\"}", null).andReturn());
        share(seriesId, "{\"nickname\":\"Watson\"}", friend)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_CREATOR"));
    }

    @Test
    void anUnknownCodeIsNotFound() throws Exception {
        mockMvc.perform(get("/api/game/challenges/K7F2QX9M")).andExpect(status().isNotFound());
        join("K7F2QX9M", "{\"nickname\":\"Watson\"}", null).andExpect(status().isNotFound());
    }

    /** Neither the age, nor the identity, nor the credit, nor the internal photo id. */
    private void assertNoAnswerIn(String json) {
        assertThat(json)
                .doesNotContain("\"age\"", "\"year\"", "\"result\":{")
                .doesNotContain("Person ", "Jane Photographer", "commons.wikimedia.org");
        seeded.forEach(photo -> assertThat(json).doesNotContain(photo.getId().toString()));
    }

    /** Each round to the end: exact ({@code AGE}) in one guess, otherwise a hint then {@code AGE}. */
    private void playAll(String seriesId, Cookie player, int firstGuess) throws Exception {
        for (int position = 1; position <= 5; position++) {
            perform(post(round(seriesId, position, "first-guess")),
                            "{\"guess\":%d,\"language\":\"EN\"}".formatted(firstGuess),
                            player)
                    .andExpect(status().isOk());
            if (firstGuess != AGE) {
                perform(post(round(seriesId, position, "hint")), "{\"categories\":[\"MUSIC\"]}", player)
                        .andExpect(status().isOk());
                perform(post(round(seriesId, position, "second-guess")), "{\"guess\":40}", player)
                        .andExpect(jsonPath("$.step").value("FINISHED"));
            }
        }
    }

    private ResultActions share(String seriesId, String body, Cookie player) throws Exception {
        return perform(post("/api/game/series/" + seriesId + "/share"), body, player);
    }

    private ResultActions join(String code, String body, Cookie player) throws Exception {
        return perform(post("/api/game/challenges/" + code + "/join"), body, player);
    }

    private String codeOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asString();
    }

    private ResultActions start(int size, Cookie guest) throws Exception {
        return perform(post("/api/game/series"), "{\"size\":%d}".formatted(size), guest);
    }

    private String idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asString();
    }

    private static Cookie cookieFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(GuestCookie.NAME + "=");
        return new Cookie(GuestCookie.NAME, setCookie.substring(GuestCookie.NAME.length() + 1, setCookie.indexOf(';')));
    }

    private static String round(String seriesId, int position, String step) {
        return "/api/game/series/" + seriesId + "/rounds/" + position + "/" + step;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body, Cookie guest) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (guest != null) {
            request.cookie(guest);
        }
        return mockMvc.perform(request);
    }

    private Person savePerson(Continent continent, int index) {
        return people.save(Person.builder()
                .wikidataId("Q" + (910_000 + index))
                .nameEn("Person " + index)
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
                .commonsFile(person.getNameEn() + ".jpg")
                .takenFrom(LocalDate.of(1990, 6, 1))
                .takenTo(LocalDate.of(1990, 6, 30))
                .ageAtPhoto(AGE)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .authorText("Jane Photographer")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + person.getNameEn() + ".jpg")
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

package app.ageguessr.functional.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GuestCookie;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.i18n.Language;
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
import com.jayway.jsonpath.JsonPath;
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
 * The game endpoints end to end, against a real Postgres. They are the anti-cheat
 * surface: until a round is finished, the raw JSON must hold nothing of the answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class GameFunctionalTest {

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
    private DailyRoundRepository rounds;

    @Autowired
    private GamePlayRepository gamePlays;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private final List<PersonPhoto> seeded = new ArrayList<>();

    @BeforeEach
    void seedToday() {
        today = LocalDate.now(clock);
        Continent[] boxes = {Continent.EUROPE, Continent.ASIA, Continent.AFRICA};
        for (int i = 0; i < boxes.length; i++) {
            PersonPhoto photo = savePhoto(savePerson(boxes[i], i));
            seeded.add(photo);
            rounds.save(DailyRound.builder()
                    .gameDate(today)
                    .position((short) (i + 1))
                    .photoId(photo.getId())
                    .drawnAt(Instant.now())
                    .build());
        }
        // Only MUSIC reaches the minimum for the year of the photos.
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(HintCategory.MUSIC, today.getYear() - AGE);
        }
        saveHint(HintCategory.FILM, today.getYear() - AGE);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_plays");
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM hints WHERE source_kind = 'functional-test'");
        people.deleteAll();
    }

    @Test
    void aGuestPlaysARoundToTheEndWithoutAnythingLeakingBefore() throws Exception {
        String before = mockMvc.perform(get("/api/game/today"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.rounds.length()").value(3))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rounds[0].imageUrl").value("/api/images/" + seeded.getFirst().getPublicId()))
                .andExpect(jsonPath("$.score.max").value(3000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(before);

        MvcResult first = firstGuess(1, 30, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("AWAITING_HINT"))
                .andExpect(jsonPath("$.firstGuess").value(30))
                .andExpect(jsonPath("$.direction").value("OLDER"))
                // 10 years off: the band of 6 to 15 years, on the older side.
                .andExpect(jsonPath("$.gap").value("CLEARLY"))
                .andExpect(jsonPath("$.range.min").value(36))
                .andExpect(jsonPath("$.range.max").value(45))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0]").value("MUSIC"))
                // A single category reaches the minimum: the player picks it alone.
                .andExpect(jsonPath("$.hintsToPick").value(1))
                .andReturn();
        assertNoAnswerIn(first.getResponse().getContentAsString());
        String setCookie = first.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(GuestCookie.NAME + "=")
                .contains("HttpOnly", "SameSite=Lax", "Path=/api/game", "Max-Age=2592000");
        Cookie guest = new Cookie(GuestCookie.NAME, setCookie.substring(GuestCookie.NAME.length() + 1, setCookie.indexOf(';')));

        String hint = perform(post(round(1, "hint")), "{\"categories\":[\"MUSIC\"]}", guest, null)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.hints.length()").value(1))
                .andExpect(jsonPath("$.hints[0].category").value("MUSIC"))
                .andExpect(jsonPath("$.hints[0].text").value(containsString("A hit")))
                .andExpect(jsonPath("$.range.min").value(36))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(hint);

        String during = mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.rounds[0].gap").value("CLEARLY"))
                .andExpect(jsonPath("$.rounds[0].hints[0].text").value(containsString("A hit")))
                .andExpect(jsonPath("$.rounds[1].step").value("NOT_STARTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(during);

        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"))
                .andExpect(jsonPath("$.result.age").value(AGE))
                .andExpect(jsonPath("$.result.firstPoints").value(184))
                .andExpect(jsonPath("$.result.secondPoints").value(409))
                .andExpect(jsonPath("$.result.points").value(593))
                .andExpect(jsonPath("$.result.person.name").value("Person 0"))
                .andExpect(jsonPath("$.result.credit.author").value("Jane Photographer"))
                .andExpect(jsonPath("$.result.hints[0].year").value(today.getYear() - AGE))
                .andExpect(jsonPath("$.result.reportedHints.length()").value(0));

        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].result.points").value(593))
                .andExpect(jsonPath("$.score.points").value(593))
                .andExpect(jsonPath("$.score.complete").value(false));
    }

    @Test
    void anExactFirstGuessEndsTheRoundAtAThousand() throws Exception {
        firstGuess(2, AGE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"))
                .andExpect(jsonPath("$.direction").value(nullValue()))
                .andExpect(jsonPath("$.gap").value(nullValue()))
                .andExpect(jsonPath("$.range").value(nullValue()))
                .andExpect(jsonPath("$.result.points").value(1000))
                .andExpect(jsonPath("$.result.secondGuess").value(nullValue()));
    }

    @Test
    void anAccountKeepsItsGameApartFromTheGuests() throws Exception {
        String email = "player-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();

        perform(post(round(1, "first-guess")), "{\"guess\":30,\"language\":\"FR\"}", null, token)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mockMvc.perform(get("/api/game/today").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.rounds[0].step").value("AWAITING_HINT"));
        mockMvc.perform(get("/api/game/today").cookie(new Cookie(GuestCookie.NAME, UUID.randomUUID().toString())))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"));
        // The account wins over a guest cookie sent along.
        perform(post(round(1, "first-guess")), "{\"guess\":31,\"language\":\"FR\"}", guestCookie(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void anInvalidTokenIsRefusedRatherThanPlayedAsAGuest() throws Exception {
        mockMvc.perform(get("/api/game/today").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesASecondFirstGuess() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        firstGuess(1, 35, guest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void refusesTheSecondGuessBeforeTheHint() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void aPlayerPicksThreeCategoriesAndSeesOneHintOfEachWithoutTheirYear() throws Exception {
        int year = today.getYear() - AGE;
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(HintCategory.FILM, year, "Film " + i);
            saveHint(HintCategory.SPORT, year, "Sport " + i);
            saveHint(HintCategory.WORLD, year, "World " + i);
        }
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest)
                .andExpect(jsonPath("$.categories.length()").value(4))
                .andExpect(jsonPath("$.hintsToPick").value(3));

        for (String wrong : List.of("[\"MUSIC\",\"FILM\"]", "[\"MUSIC\",\"FILM\",\"SPORT\",\"WORLD\"]", "[\"MUSIC\",\"MUSIC\",\"FILM\"]")) {
            perform(post(round(1, "hint")), "{\"categories\":" + wrong + "}", guest, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("HINT_COUNT"));
        }
        perform(post(round(1, "hint")), "{\"categories\":[]}", guest, null).andExpect(status().isBadRequest());
        perform(post(round(1, "hint")), "{}", guest, null).andExpect(status().isBadRequest());
        perform(post(round(1, "hint")), "{\"categories\":[\"MUSIC\",\"FILM\",\"CELEBRITIES\"]}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_UNAVAILABLE"));

        String shown = perform(post(round(1, "hint")), "{\"categories\":[\"WORLD\",\"MUSIC\",\"SPORT\"]}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.hints[*].category").value(contains("MUSIC", "SPORT", "WORLD")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(shown);

        // Another player who also picked SPORT sees the same sports hint.
        Cookie other = guestCookie();
        firstGuess(1, 30, other).andExpect(status().isOk());
        String otherShown = perform(post(round(1, "hint")), "{\"categories\":[\"SPORT\",\"FILM\",\"WORLD\"]}", other, null)
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(otherShown, "$.hints[?(@.category == 'SPORT')].text"))
                .isEqualTo(JsonPath.<List<String>>read(shown, "$.hints[?(@.category == 'SPORT')].text"));

        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.hints.length()").value(3))
                .andExpect(jsonPath("$.result.hints[*].year").value(contains(year, year, year)))
                .andExpect(jsonPath("$.result.hints[2].sourceUrl").value("https://en.wikipedia.org/wiki/Hit"));
    }

    @Test
    void aLiteratureHintIsOfferedShownAndKeptInTheRound() throws Exception {
        int year = today.getYear() - AGE;
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(HintCategory.LITERATURE, year, "Book " + i);
            saveHint(HintCategory.FILM, year, "Film " + i);
        }
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(jsonPath("$.categories", hasItem("LITERATURE")));

        // V23: the round stores the category, as the CHECK constraints of the plays now allow.
        perform(post(round(1, "hint")), "{\"categories\":[\"LITERATURE\",\"MUSIC\",\"FILM\"]}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hints[*].category", hasItem("LITERATURE")));
        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.hints[*].category", hasItem("LITERATURE")));
    }

    @Test
    void aPageOfBeforeSeveralHintsStillGetsOneHint() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        perform(post(round(1, "hint")), "{\"category\":\"MUSIC\"}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hints.length()").value(1))
                .andExpect(jsonPath("$.hints[0].category").value("MUSIC"));
    }

    @Test
    void refusesACategoryWithTooFewHints() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        perform(post(round(1, "hint")), "{\"categories\":[\"FILM\"]}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_UNAVAILABLE"));
    }

    @Test
    void refusesAHintToAGuestWithoutCookieAndSetsNone() throws Exception {
        perform(post(round(1, "hint")), "{\"categories\":[\"MUSIC\"]}", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refusesTheGameOfAnotherDay() throws Exception {
        perform(post("/api/game/" + today.minusDays(1) + "/rounds/1/first-guess"), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_CLOSED"));
    }

    @Test
    void refusesARoundWhosePhotoWasRejectedAndLeavesItOutOfTheScore() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(2, 30, guest).andExpect(status().isOk());
        PersonPhoto photo = photos.findById(seeded.get(1).getId()).orElseThrow();
        photo.setReviewStatus(ReviewStatus.REJECTED);
        photos.save(photo);

        perform(post(round(2, "hint")), "{\"categories\":[\"MUSIC\"]}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROUND_NEUTRALIZED"));
        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[1].status").value("NEUTRALIZED"))
                .andExpect(jsonPath("$.rounds[1].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.score.max").value(2000));
    }

    @Test
    void validatesTheRequest() throws Exception {
        firstGuess(1, 17, null).andExpect(status().isBadRequest());
        firstGuess(1, 101, null).andExpect(status().isBadRequest());
        perform(post(round(1, "first-guess")), "{\"guess\":30,\"language\":\"DE\"}", null, null)
                .andExpect(status().isBadRequest());
        perform(post(round(1, "first-guess")), "{\"guess\":30}", null, null).andExpect(status().isBadRequest());
        perform(post("/api/game/02-11-2026/rounds/1/first-guess"), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        perform(post(round(9, "first-guess")), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void loggingInClaimsTheGuestGamesAndRemovesTheGuestCookie() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());
        firstGuess(2, 50, guest).andExpect(status().isOk());
        String email = "claim-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
        // Round 2 played on the account too: the account's round wins.
        perform(post(round(2, "first-guess")), "{\"guess\":60,\"language\":\"EN\"}", null, token)
                .andExpect(status().isOk());

        String setCookie = perform(post("/api/game/guest-plays/claim"), "", guest, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(1))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).startsWith(GuestCookie.NAME + "=;").contains("Max-Age=0", "Path=/api/game", "HttpOnly");
        mockMvc.perform(get("/api/game/today").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.rounds[0].firstGuess").value(30))
                .andExpect(jsonPath("$.rounds[1].firstGuess").value(60));
        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"));
        perform(post("/api/game/guest-plays/claim"), "", guest, token).andExpect(jsonPath("$.claimed").value(0));
    }

    @Test
    void claimingWithoutAGuestCookieMovesNothingAndSetsNoCookie() throws Exception {
        String email = "claim-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();

        perform(post("/api/game/guest-plays/claim"), "", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(0))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void claimingNeedsAnAccount() throws Exception {
        perform(post("/api/game/guest-plays/claim"), "", guestCookie(), null).andExpect(status().isUnauthorized());
    }

    @Test
    void theHistoryShowsFinishedRoundsOnlyAndNeverAnImage() throws Exception {
        LocalDate yesterday = today.minusDays(1);
        for (int i = 0; i < seeded.size(); i++) {
            rounds.save(DailyRound.builder()
                    .gameDate(yesterday)
                    .position((short) (i + 1))
                    .photoId(seeded.get(i).getId())
                    .drawnAt(Instant.now())
                    .build());
        }
        String email = "history-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
        String me = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(me).get("id").asString());
        // Yesterday, three exact first guesses: a complete day at 3 000.
        for (short position = 1; position <= 3; position++) {
            gamePlays.save(GamePlay.start(yesterday, position, PlayerKey.account(userId), Language.FR, AGE, AGE, Instant.now()));
        }
        // Today, round 1 still being played, round 2 finished.
        perform(post(round(1, "first-guess")), "{\"guess\":30,\"language\":\"EN\"}", null, token)
                .andExpect(status().isOk());
        perform(post(round(2, "first-guess")), "{\"guess\":40,\"language\":\"EN\"}", null, token)
                .andExpect(status().isOk());

        String json = mockMvc.perform(get("/api/game/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.gamesPlayed").value(1))
                .andExpect(jsonPath("$.stats.bestPoints").value(3000))
                .andExpect(jsonPath("$.stats.currentStreak").value(1))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value(today.toString()))
                .andExpect(jsonPath("$.days[0].complete").value(false))
                .andExpect(jsonPath("$.days[0].points").value(1000))
                .andExpect(jsonPath("$.days[0].rounds[0].finished").value(false))
                .andExpect(jsonPath("$.days[0].rounds[0].name").value(nullValue()))
                .andExpect(jsonPath("$.days[0].rounds[0].ageAtPhoto").value(nullValue()))
                .andExpect(jsonPath("$.days[0].rounds[0].points").value(nullValue()))
                .andExpect(jsonPath("$.days[0].rounds[1].name").value("Person 1"))
                .andExpect(jsonPath("$.days[0].rounds[1].ageAtPhoto").value(AGE))
                .andExpect(jsonPath("$.days[1].complete").value(true))
                .andExpect(jsonPath("$.days[1].max").value(3000))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json).doesNotContain("/api/images/", "commons.wikimedia.org");
        seeded.forEach(photo -> assertThat(json)
                .doesNotContain(photo.getId().toString())
                .doesNotContain(photo.getPublicId().toString()));

        mockMvc.perform(get("/api/game/history?page=1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.days.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/game/history?page=-1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theHistoryNeedsAnAccount() throws Exception {
        mockMvc.perform(get("/api/game/history").cookie(guestCookie())).andExpect(status().isUnauthorized());
    }

    /**
     * Neither the age, nor the identity, nor the credit (the Commons page names the
     * person), nor the internal photo id, nor the hint year (today's year minus the age).
     */
    private void assertNoAnswerIn(String json) {
        assertThat(json)
                .doesNotContain("\"age\"", "\"year\"", "\"result\":{")
                .doesNotContain("Person 0", "Jane Photographer", "commons.wikimedia.org");
        seeded.forEach(photo -> assertThat(json).doesNotContain(photo.getId().toString()));
    }

    private Cookie guestCookie() {
        return new Cookie(GuestCookie.NAME, UUID.randomUUID().toString());
    }

    private String round(int position, String step) {
        return "/api/game/" + today + "/rounds/" + position + "/" + step;
    }

    private ResultActions firstGuess(int position, int guess, Cookie guest) throws Exception {
        return perform(post(round(position, "first-guess")), "{\"guess\":%d,\"language\":\"EN\"}".formatted(guess), guest, null);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body, Cookie guest, String token)
            throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (guest != null) {
            request.cookie(guest);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Person savePerson(Continent continent, int index) {
        return people.save(Person.builder()
                .wikidataId("Q" + (900_000 + index))
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

    private void saveHint(HintCategory category, int year) {
        saveHint(category, year, "A hit of that year.");
    }

    private void saveHint(HintCategory category, int year, String textEn) {
        hints.save(Hint.builder()
                .category(category)
                .year(year)
                .audience(HintAudience.ALL)
                .textEn(textEn)
                .textFr("Un tube de cette année.")
                .sourceUrl("https://en.wikipedia.org/wiki/Hit")
                .sourceKind("functional-test")
                .dedupeKey("functional-" + UUID.randomUUID())
                .reviewStatus(HintReviewStatus.APPROVED)
                .createdAt(Instant.now())
                .build());
    }
}

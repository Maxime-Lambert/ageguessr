package app.ageguessr.features.game.DrawDailyGame;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DailyDrawTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Test
    void picksTheConfiguredNumberOfPhotosInDistinctBoxes() {
        List<DrawCandidate> pool = poolOfEveryBox(3);

        DailyDraw.Outcome outcome = draw(3, 1).draw(pool, DAY);

        assertThat(outcome.photoIds()).hasSize(3).doesNotHaveDuplicates();
        assertThat(boxesOf(outcome, pool)).doesNotHaveDuplicates();
        assertThat(outcome.replayedEarly()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void followsTheNumberOfRoundsOfTheProperties(int rounds) {
        assertThat(draw(rounds, 7).draw(poolOfEveryBox(2), DAY).photoIds()).hasSize(rounds);
    }

    @Test
    void neverPutsTwoPeopleOfTheSameBoxInADayOverManyDraws() {
        List<DrawCandidate> pool = poolOfEveryBox(4);
        DailyDraw draw = draw(3, 42);

        for (int i = 0; i < 1_000; i++) {
            assertThat(boxesOf(draw.draw(pool, DAY), pool)).doesNotHaveDuplicates();
        }
    }

    @Test
    void treatsAnUnknownGenderOrContinentAsABoxOfItsOwn() {
        List<DrawCandidate> pool = List.of(
                candidate(null, Continent.EUROPE, null),
                candidate(null, Continent.EUROPE, null),
                candidate(Gender.MALE, null, null),
                candidate(Gender.MALE, null, null));

        DailyDraw.Outcome outcome = draw(3, 3).draw(pool, DAY);

        // Two boxes only: (unknown, Europe) and (male, unknown).
        assertThat(outcome.photoIds()).hasSize(2);
        assertThat(boxesOf(outcome, pool)).doesNotHaveDuplicates();
    }

    @Test
    void leavesOutAPhotoPlayedLessThanThreeYearsAgo() {
        DrawCandidate recent = candidate(Gender.FEMALE, Continent.ASIA, DAY.minusYears(3).plusDays(1));
        DrawCandidate fresh = candidate(Gender.MALE, Continent.ASIA, null);

        DailyDraw.Outcome outcome = draw(1, 5).draw(List.of(recent, fresh), DAY);

        assertThat(outcome.photoIds()).containsExactly(fresh.photoId());
    }

    @Test
    void letsAPhotoPlayedExactlyThreeYearsAgoComeBack() {
        DrawCandidate threeYearsAgo = candidate(Gender.FEMALE, Continent.ASIA, DAY.minusYears(3));

        DailyDraw.Outcome outcome = draw(1, 5).draw(List.of(threeYearsAgo), DAY);

        assertThat(outcome.photoIds()).containsExactly(threeYearsAgo.photoId());
        assertThat(outcome.replayedEarly()).isZero();
    }

    @Test
    void replaysThePhotosPlayedLongestAgoWhenTooFewAreLeft() {
        DrawCandidate fresh = candidate(Gender.FEMALE, Continent.AFRICA, null);
        DrawCandidate lastMonth = candidate(Gender.MALE, Continent.AFRICA, DAY.minusMonths(1));
        DrawCandidate lastYear = candidate(Gender.FEMALE, Continent.EUROPE, DAY.minusYears(1));
        DrawCandidate twoYearsAgo = candidate(Gender.MALE, Continent.EUROPE, DAY.minusYears(2));

        DailyDraw.Outcome outcome = draw(3, 9).draw(List.of(fresh, lastMonth, lastYear, twoYearsAgo), DAY);

        assertThat(outcome.photoIds())
                .containsExactly(fresh.photoId(), twoYearsAgo.photoId(), lastYear.photoId());
        assertThat(outcome.replayedEarly()).isEqualTo(2);
    }

    @Test
    void keepsTheBoxRuleWhenReplayingEarly() {
        DrawCandidate fresh = candidate(Gender.FEMALE, Continent.AFRICA, null);
        DrawCandidate sameBoxOldest = candidate(Gender.FEMALE, Continent.AFRICA, DAY.minusYears(2));
        DrawCandidate otherBox = candidate(Gender.MALE, Continent.AFRICA, DAY.minusMonths(1));

        DailyDraw.Outcome outcome = draw(3, 9).draw(List.of(fresh, sameBoxOldest, otherBox), DAY);

        assertThat(outcome.photoIds()).containsExactly(fresh.photoId(), otherBox.photoId());
        assertThat(outcome.replayedEarly()).isEqualTo(1);
    }

    @Test
    void drawsFewerRoundsRatherThanTwoOfTheSameBox() {
        List<DrawCandidate> pool = List.of(
                candidate(Gender.FEMALE, Continent.OCEANIA, null),
                candidate(Gender.FEMALE, Continent.OCEANIA, null),
                candidate(Gender.FEMALE, Continent.OCEANIA, DAY.minusYears(5)));

        assertThat(draw(3, 2).draw(pool, DAY).photoIds()).hasSize(1);
    }

    @Test
    void drawsNothingFromAnEmptyPool() {
        DailyDraw.Outcome outcome = draw(3, 2).draw(List.of(), DAY);

        assertThat(outcome.photoIds()).isEmpty();
        assertThat(outcome.replayedEarly()).isZero();
    }

    @Test
    void givesEveryPhotoTheSameChance() {
        List<DrawCandidate> pool = poolOfEveryBox(3);
        DailyDraw draw = draw(3, 2026);
        Map<UUID, Integer> picks = new HashMap<>();
        int draws = 10_000;

        for (int i = 0; i < draws; i++) {
            draw.draw(pool, DAY).photoIds().forEach(id -> picks.merge(id, 1, Integer::sum));
        }

        double mean = draws * 3.0 / pool.size();
        assertThat(picks).hasSize(pool.size());
        assertThat(picks.values()).allSatisfy(count -> assertThat(count).isBetween((int) (mean * 0.8), (int) (mean * 1.2)));
    }

    private static DailyDraw draw(int rounds, long seed) {
        return new DailyDraw(new GameProperties(rounds, 3, false), new Random(seed));
    }

    private static DrawCandidate candidate(Gender gender, Continent continent, LocalDate lastPlayedOn) {
        return new DrawCandidate(UUID.randomUUID(), gender, continent, lastPlayedOn);
    }

    /** {@code perBox} never-played photos in each of the 18 boxes. */
    private static List<DrawCandidate> poolOfEveryBox(int perBox) {
        List<DrawCandidate> pool = new ArrayList<>();
        for (Gender gender : Gender.values()) {
            for (Continent continent : Continent.values()) {
                for (int i = 0; i < perBox; i++) {
                    pool.add(candidate(gender, continent, null));
                }
            }
        }
        return pool;
    }

    private static List<String> boxesOf(DailyDraw.Outcome outcome, List<DrawCandidate> pool) {
        Map<UUID, DrawCandidate> byId = pool.stream().collect(Collectors.toMap(DrawCandidate::photoId, c -> c));
        return outcome.photoIds().stream()
                .map(byId::get)
                .map(c -> c.gender() + "/" + c.continent())
                .toList();
    }
}

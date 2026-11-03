package app.ageguessr.features.game.StartSeries;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

class SeriesDrawTest {

    private static final Continent[] CONTINENTS = {Continent.EUROPE, Continent.ASIA, Continent.AFRICA, Continent.OCEANIA};

    /** A fixed seed: a failing case replays. RepeatedTest varies it to cover more draws. */
    private SeriesDraw draw(long seed) {
        return new SeriesDraw(new SplittableRandom(seed));
    }

    @RepeatedTest(20)
    void neverPicksAPhotoOfTodaysDailyGame(RepetitionInfo repetition) {
        List<SeriesCandidate> pool = pool(4, 3);
        Set<UUID> today = Set.of(pool.get(0).photoId(), pool.get(5).photoId());

        List<UUID> picked = draw(repetition.getCurrentRepetition()).draw(pool, today, Set.of(), 10);

        assertThat(picked).hasSize(10).doesNotContainAnyElementsOf(today);
    }

    @RepeatedTest(20)
    void picksThePhotosThePlayerHasNeverSeenFirst(RepetitionInfo repetition) {
        List<SeriesCandidate> pool = pool(4, 3);
        Set<UUID> unseen = Set.of(pool.get(0).photoId(), pool.get(4).photoId(), pool.get(8).photoId());
        Set<UUID> seen = pool.stream()
                .map(SeriesCandidate::photoId)
                .filter(id -> !unseen.contains(id))
                .collect(Collectors.toSet());

        List<UUID> picked = draw(repetition.getCurrentRepetition()).draw(pool, Set.of(), seen, 5);

        assertThat(picked).hasSize(5);
        assertThat(picked.subList(0, 3)).containsExactlyInAnyOrderElementsOf(unseen);
    }

    @RepeatedTest(20)
    void neverPicksTwoPhotosOfTheSamePerson(RepetitionInfo repetition) {
        UUID person = UUID.randomUUID();
        List<SeriesCandidate> pool = new ArrayList<>(pool(2, 1));
        pool.add(new SeriesCandidate(UUID.randomUUID(), person, Gender.MALE, Continent.EUROPE));
        pool.add(new SeriesCandidate(UUID.randomUUID(), person, Gender.MALE, Continent.EUROPE));

        List<UUID> picked = draw(repetition.getCurrentRepetition()).draw(pool, Set.of(), Set.of(), 5);

        Map<UUID, UUID> personOf =
                pool.stream().collect(Collectors.toMap(SeriesCandidate::photoId, SeriesCandidate::personId));
        assertThat(picked).hasSize(3);
        assertThat(picked.stream().map(personOf::get).distinct()).hasSize(3);
    }

    @RepeatedTest(20)
    void takesTheBoxesInTurn(RepetitionInfo repetition) {
        List<SeriesCandidate> pool = pool(4, 5);
        Map<UUID, Continent> boxOf =
                pool.stream().collect(Collectors.toMap(SeriesCandidate::photoId, SeriesCandidate::continent));

        List<UUID> picked = draw(repetition.getCurrentRepetition()).draw(pool, Set.of(), Set.of(), 10);

        // 10 photos over 4 boxes: two boxes twice, two three times, never four of one.
        Map<Continent, Long> perBox =
                picked.stream().collect(Collectors.groupingBy(boxOf::get, Collectors.counting()));
        assertThat(perBox).hasSize(4);
        assertThat(perBox.values()).allMatch(count -> count == 2 || count == 3);
        assertThat(picked.subList(0, 4).stream().map(boxOf::get).distinct()).hasSize(4);
    }

    @Test
    void drawsFewerPhotosWhenThePoolRunsShort() {
        List<SeriesCandidate> pool = pool(2, 2);

        assertThat(draw(1).draw(pool, Set.of(pool.getFirst().photoId()), Set.of(), 20)).hasSize(3);
        assertThat(draw(1).draw(List.of(), Set.of(), Set.of(), 5)).isEmpty();
    }

    @Test
    void isTheSameForTheSameSeed() {
        List<SeriesCandidate> pool = pool(4, 5);

        assertThat(draw(42).draw(pool, Set.of(), Set.of(), 10)).isEqualTo(draw(42).draw(pool, Set.of(), Set.of(), 10));
    }

    /** {@code perBox} photos in each of {@code boxes} boxes, each of a different person. */
    private static List<SeriesCandidate> pool(int boxes, int perBox) {
        List<SeriesCandidate> pool = new ArrayList<>();
        for (int box = 0; box < boxes; box++) {
            for (int i = 0; i < perBox; i++) {
                pool.add(new SeriesCandidate(UUID.randomUUID(), UUID.randomUUID(), Gender.FEMALE, CONTINENTS[box]));
            }
        }
        return pool;
    }
}

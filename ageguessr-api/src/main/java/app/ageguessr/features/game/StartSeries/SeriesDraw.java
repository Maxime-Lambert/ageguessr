package app.ageguessr.features.game.StartSeries;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Picks the photos of a free series (user decisions, 2026-11-03):
 * <ul>
 *   <li>never a photo of today's daily game, which the series would spoil;
 *   <li>the photos the player has never seen first, then the others;
 *   <li>never two photos of the same person: the second would tell the age of the first;
 *   <li>the boxes (continent × gender) in turn, the least used first. The daily rule
 *       (never two of a box) cannot hold: 20 photos are more than the boxes.
 * </ul>
 * Uniform within these rules, with no weighting by age. Fewer photos than asked when the
 * pool runs short. Pure, so that every rule is tested with a fixed seed.
 */
@Component
@RequiredArgsConstructor
public class SeriesDraw {

    private final RandomGenerator random;

    private record Box(Gender gender, Continent continent) {
        static Box of(SeriesCandidate candidate) {
            return new Box(candidate.gender(), candidate.continent());
        }
    }

    public List<UUID> draw(Collection<SeriesCandidate> candidates, Set<UUID> today, Set<UUID> seen, int size) {
        List<SeriesCandidate> unseen = new ArrayList<>();
        List<SeriesCandidate> seenAgain = new ArrayList<>();
        for (SeriesCandidate candidate : candidates) {
            if (!today.contains(candidate.photoId())) {
                (seen.contains(candidate.photoId()) ? seenAgain : unseen).add(candidate);
            }
        }
        Map<Box, Integer> uses = new HashMap<>();
        Set<UUID> people = new HashSet<>();
        List<UUID> picked = new ArrayList<>();
        for (List<SeriesCandidate> pool : List.of(unseen, seenAgain)) {
            while (picked.size() < size) {
                pool.removeIf(candidate -> people.contains(candidate.personId()));
                if (pool.isEmpty()) {
                    break;
                }
                int fewest = pool.stream()
                        .mapToInt(candidate -> uses.getOrDefault(Box.of(candidate), 0))
                        .min()
                        .orElseThrow();
                List<SeriesCandidate> open = pool.stream()
                        .filter(candidate -> uses.getOrDefault(Box.of(candidate), 0) == fewest)
                        .toList();
                SeriesCandidate chosen = open.get(random.nextInt(open.size()));
                pool.remove(chosen);
                people.add(chosen.personId());
                uses.merge(Box.of(chosen), 1, Integer::sum);
                picked.add(chosen.photoId());
            }
        }
        return List.copyOf(picked);
    }
}

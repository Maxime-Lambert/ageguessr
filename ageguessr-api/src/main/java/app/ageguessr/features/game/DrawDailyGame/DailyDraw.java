package app.ageguessr.features.game.DrawDailyGame;

import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Picks the photos of a day (user decisions, 2026-11-02):
 * <ul>
 *   <li>uniformly at random among the photos, with no weighting by age: a rule on ages
 *       would tell players something ("the first one is 22, so the others are not");
 *   <li>never two people of the same box (continent × gender) on the same day, which also
 *       rules out two photos of the same person;
 *   <li>never a photo played less than {@code replayYears} ago, the bound included: a
 *       photo played exactly 3 years ago may come back.
 * </ul>
 * When too few photos are left (small pool in dev, exhausted pool), the box rule holds
 * and the replay rule gives way, to the photos played longest ago first. When even the
 * boxes run out, the day gets fewer rounds rather than two of the same box. Pure, so that
 * every rule is tested with a fixed seed.
 */
@Component
@RequiredArgsConstructor
public class DailyDraw {

    private final GameProperties properties;
    private final RandomGenerator random;

    /** The photos picked in round order, and how many of them broke the replay rule. */
    public record Outcome(List<UUID> photoIds, int replayedEarly) {}

    private record Box(Gender gender, Continent continent) {
        static Box of(DrawCandidate candidate) {
            return new Box(candidate.gender(), candidate.continent());
        }
    }

    public Outcome draw(List<DrawCandidate> candidates, LocalDate day) {
        LocalDate lastReplayableDay = day.minusYears(properties.replayYears());
        List<DrawCandidate> replayable = new ArrayList<>();
        List<DrawCandidate> playedRecently = new ArrayList<>();
        for (DrawCandidate candidate : candidates) {
            boolean recent = candidate.lastPlayedOn() != null && candidate.lastPlayedOn().isAfter(lastReplayableDay);
            (recent ? playedRecently : replayable).add(candidate);
        }

        Set<Box> usedBoxes = new HashSet<>();
        List<UUID> picked = new ArrayList<>();
        while (picked.size() < properties.rounds()) {
            List<DrawCandidate> open = replayable.stream()
                    .filter(candidate -> !usedBoxes.contains(Box.of(candidate)))
                    .toList();
            if (open.isEmpty()) {
                break;
            }
            DrawCandidate chosen = open.get(random.nextInt(open.size()));
            usedBoxes.add(Box.of(chosen));
            picked.add(chosen.photoId());
        }

        // Shuffled before the stable sort, so that photos played on the same day come back
        // in a random order rather than in the database's.
        Collections.shuffle(playedRecently, random);
        playedRecently.sort(Comparator.comparing(DrawCandidate::lastPlayedOn));
        int replayedEarly = 0;
        for (DrawCandidate candidate : playedRecently) {
            if (picked.size() == properties.rounds()) {
                break;
            }
            if (usedBoxes.add(Box.of(candidate))) {
                picked.add(candidate.photoId());
                replayedEarly++;
            }
        }
        return new Outcome(List.copyOf(picked), replayedEarly);
    }
}

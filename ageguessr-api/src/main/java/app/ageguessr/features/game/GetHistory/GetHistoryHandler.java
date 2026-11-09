package app.ageguessr.features.game.GetHistory;

import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.features.game.DatedRoundState;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.RoundReveal;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.GetHistory.HistoryCalculator.Play;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An account's history: the statistics over every day played, and the rounds of one page
 * of days. Every played round is read at once (3 a day at most, about 1 100 a year), then
 * HistoryCalculator does the arithmetic. The photos and people of the page's days are
 * loaded in two queries, whatever the number of days.
 */
@Service
@RequiredArgsConstructor
public class GetHistoryHandler {

    static final int PAGE_SIZE = 30;

    private final GamePlayRepository plays;
    private final DailyRoundRepository rounds;
    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HistoryResponse handle(UUID userId, int page) {
        List<GamePlay> played = plays.findByUserId(userId);
        Set<LocalDate> dates = played.stream().map(GamePlay::getGameDate).collect(Collectors.toSet());
        Map<LocalDate, Map<Short, Boolean>> playable = new HashMap<>();
        Map<LocalDate, Map<Short, UUID>> photoOfRound = new HashMap<>();
        if (!dates.isEmpty()) {
            for (DatedRoundState state : rounds.findStatesOn(dates)) {
                boolean stillPlayable = state.status() == DailyRoundStatus.PLAYABLE;
                playable.computeIfAbsent(state.gameDate(), date -> new HashMap<>())
                        .put(state.position(), stillPlayable);
                if (stillPlayable) {
                    photoOfRound.computeIfAbsent(state.gameDate(), date -> new HashMap<>())
                            .put(state.position(), state.photoId());
                }
            }
        }
        HistoryCalculator.Result result = HistoryCalculator.compute(
                played.stream().map(GetHistoryHandler::toPlay).toList(), playable, LocalDate.now(clock));

        List<HistoryCalculator.Day> days = result.days();
        // In long: a huge page number would overflow an int and go negative.
        int from = (int) Math.min((long) page * PAGE_SIZE, days.size());
        int to = Math.min(from + PAGE_SIZE, days.size());
        List<HistoryCalculator.Day> shown = days.subList(from, to);
        Answers answers = answersOf(shown, photoOfRound);
        List<HistoryResponse.Day> pageDays = shown.stream()
                .map(day -> toDay(day, played, photoOfRound.getOrDefault(day.date(), Map.of()), answers))
                .toList();
        HistoryCalculator.Stats stats = result.stats();
        return new HistoryResponse(
                new HistoryResponse.Stats(
                        stats.gamesPlayed(),
                        stats.averagePoints(),
                        stats.bestPoints(),
                        stats.currentStreak(),
                        stats.bestStreak()),
                pageDays,
                page,
                to < days.size());
    }

    private static Play toPlay(GamePlay play) {
        boolean finished = play.step() == RoundStep.FINISHED;
        return new Play(play.getGameDate(), play.getPosition(), finished, finished ? play.points() : 0);
    }

    /** The photos and people of the page's playable rounds: two queries for the whole page. */
    private record Answers(Map<UUID, PersonPhoto> photos, Map<UUID, Person> people) {}

    private Answers answersOf(List<HistoryCalculator.Day> shown, Map<LocalDate, Map<Short, UUID>> photoOfRound) {
        Set<UUID> photoIds = shown.stream()
                .flatMap(day -> photoOfRound.getOrDefault(day.date(), Map.of()).values().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PersonPhoto> pagePhotos = photos.findAllById(photoIds).stream()
                .collect(Collectors.toMap(PersonPhoto::getId, Function.identity()));
        Set<UUID> personIds = pagePhotos.values().stream().map(PersonPhoto::getPersonId).collect(Collectors.toSet());
        Map<UUID, Person> pagePeople = people.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return new Answers(pagePhotos, pagePeople);
    }

    private static HistoryResponse.Day toDay(
            HistoryCalculator.Day day, List<GamePlay> played, Map<Short, UUID> photoOfDay, Answers answers) {
        List<HistoryResponse.Round> rounds = played.stream()
                .filter(play -> play.getGameDate().equals(day.date()))
                .sorted(Comparator.comparing(GamePlay::getPosition))
                .map(play -> {
                    PersonPhoto photo = answers.photos().get(photoOfDay.get(play.getPosition()));
                    Person person = photo == null ? null : answers.people().get(photo.getPersonId());
                    return toRound(play, photo, person);
                })
                .toList();
        return new HistoryResponse.Day(day.date(), day.points(), day.max(), day.complete(), rounds);
    }

    /**
     * The answer only for a finished round: today's round still being played must say
     * nothing more here than on the game screen. Nor for a neutralized one: its photo was
     * rejected or its person excluded (or deleted between the two reads), so neither is
     * shown again.
     */
    private static HistoryResponse.Round toRound(GamePlay play, PersonPhoto photo, Person person) {
        boolean finished = play.step() == RoundStep.FINISHED;
        boolean neutralized = photo == null || person == null;
        boolean reveal = finished && !neutralized;
        return new HistoryResponse.Round(
                play.getPosition(),
                finished,
                neutralized,
                reveal ? RoundReveal.name(person, play.getLanguage()) : null,
                reveal ? photo.getAgeAtPhoto() : null,
                play.getFirstGuess(),
                play.getSecondGuess(),
                finished ? play.points() : null);
    }
}

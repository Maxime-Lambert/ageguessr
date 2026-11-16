package app.ageguessr.features.game.GetCalendar;

import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.features.game.DatedRoundState;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.PlayerHistories;
import app.ageguessr.features.game.PlayerHistory;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Three queries whatever the month: its rounds, the player's rounds in it, the first draw. */
@Service
@RequiredArgsConstructor
public class GetCalendarHandler {

    private final DailyRoundRepository rounds;
    private final GamePlayRepository plays;
    private final Clock clock;

    /**
     * @param month empty for the current month
     * @param player empty for a visitor who never played: no day started
     */
    @Transactional(readOnly = true)
    public CalendarResponse handle(Optional<YearMonth> month, Optional<PlayerKey> player) {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        YearMonth firstMonth = rounds.findFirstGameDate().map(YearMonth::from).orElse(current);
        YearMonth shown = month.orElse(current);
        if (shown.isAfter(current) || shown.isBefore(firstMonth)) {
            throw new ValidationException("MONTH_OUT_OF_RANGE", "The calendar goes from the first game to this month");
        }
        LocalDate from = shown.atDay(1);
        LocalDate to = shown.atEndOfMonth();
        Map<LocalDate, Map<Short, Boolean>> playable = new HashMap<>();
        for (DatedRoundState state : rounds.findStatesBetween(from, to)) {
            playable.computeIfAbsent(state.gameDate(), date -> new HashMap<>())
                    .put(state.position(), state.status() == DailyRoundStatus.PLAYABLE);
        }
        List<PlayerHistory.Play> played = player.map(key -> plays.findOf(key, from, to)).orElse(List.of()).stream()
                .map(PlayerHistories::toPlay)
                .toList();
        List<CalendarResponse.Day> days = CalendarDays.of(shown, today, playable, played).stream()
                .map(day -> new CalendarResponse.Day(day.date(), day.state()))
                .toList();
        return new CalendarResponse(shown, firstMonth, days);
    }
}

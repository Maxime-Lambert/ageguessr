package app.ageguessr.features.game.GetDayGame;

import static app.ageguessr.features.game.GameFixtures.CLOCK;
import static app.ageguessr.features.game.GameFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.DayGameResponse;
import app.ageguessr.features.game.DayGames;
import app.ageguessr.features.game.GameScore;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetDayGameHandlerTest {

    private final DayGames days = mock(DayGames.class);
    private final GetDayGameHandler handler = new GetDayGameHandler(days, CLOCK);
    private final Optional<PlayerKey> guest = Optional.of(PlayerKey.guest(UUID.randomUUID()));

    @Test
    void readsAPastDayAndToday() {
        LocalDate past = TODAY.minusDays(12);
        DayGameResponse game = new DayGameResponse(past, List.of(mock(RoundView.class)), new GameScore(0, 1000, false));
        when(days.of(past, guest)).thenReturn(game);
        when(days.of(TODAY, guest))
                .thenReturn(new DayGameResponse(TODAY, List.of(mock(RoundView.class)), new GameScore(0, 1000, false)));

        assertThat(handler.handle(past, guest)).isSameAs(game);
        assertThat(handler.handle(TODAY, guest).date()).isEqualTo(TODAY);
    }

    @Test
    void refusesADayToComeWithoutReadingIt() {
        assertThatThrownBy(() -> handler.handle(TODAY.plusDays(1), guest))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("GAME_CLOSED");
        verifyNoInteractions(days);
    }

    @Test
    void findsNothingOnADayWithoutADraw() {
        LocalDate past = TODAY.minusDays(3);
        when(days.of(past, Optional.empty())).thenReturn(new DayGameResponse(past, List.of(), new GameScore(0, 0, false)));

        assertThatThrownBy(() -> handler.handle(past, Optional.empty())).isInstanceOf(NotFoundException.class);
    }
}

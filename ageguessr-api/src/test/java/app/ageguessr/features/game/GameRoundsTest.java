package app.ageguessr.features.game;

import static app.ageguessr.features.game.GameFixtures.CLOCK;
import static app.ageguessr.features.game.GameFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.DailyRoundRepository.RoundState;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameRoundsTest {

    private final DailyRoundRepository dailyRounds = mock(DailyRoundRepository.class);
    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);
    private final PersonRepository people = mock(PersonRepository.class);
    private final GameRounds rounds = new GameRounds(dailyRounds, photos, people, CLOCK);

    @Test
    void loadsAPlayableRoundWithItsPhotoAndPerson() {
        PersonPhoto photo = stubPhoto();
        when(dailyRounds.findStatesOf(TODAY))
                .thenReturn(List.of(new RoundState((short) 1, photo.getId(), DailyRoundStatus.PLAYABLE)));

        GameRound round = rounds.playable(TODAY, (short) 1);

        assertThat(round.photo()).isSameAs(photo);
        assertThat(round.person().getWikidataId()).isEqualTo("Q42");
    }

    @Test
    void closesEveryOtherDayBeforeLookingAtIt() {
        assertThatThrownBy(() -> rounds.playable(TODAY.minusDays(1), (short) 1))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("GAME_CLOSED");
        assertThatThrownBy(() -> rounds.playable(TODAY.plusDays(1), (short) 1))
                .extracting("errorCode")
                .isEqualTo("GAME_CLOSED");
        verifyNoInteractions(dailyRounds);
    }

    @Test
    void refusesANeutralizedRound() {
        when(dailyRounds.findStatesOf(TODAY))
                .thenReturn(List.of(new RoundState((short) 1, null, DailyRoundStatus.NEUTRALIZED)));

        assertThatThrownBy(() -> rounds.playable(TODAY, (short) 1))
                .extracting("errorCode")
                .isEqualTo("ROUND_NEUTRALIZED");
    }

    @Test
    void findsNoRoundAtAnUnknownPosition() {
        when(dailyRounds.findStatesOf(TODAY)).thenReturn(List.of());

        assertThatThrownBy(() -> rounds.playable(TODAY, (short) 4)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void neutralizesARoundWhosePhotoVanishedBetweenTheReads() {
        UUID photoId = UUID.randomUUID();
        when(dailyRounds.findStatesOf(TODAY))
                .thenReturn(List.of(new RoundState((short) 1, photoId, DailyRoundStatus.PLAYABLE)));
        when(photos.findById(photoId)).thenReturn(Optional.empty());

        assertThat(rounds.of(TODAY)).extracting(GameRound::status).containsExactly(DailyRoundStatus.NEUTRALIZED);
    }

    private PersonPhoto stubPhoto() {
        PersonPhoto photo = PersonPhoto.builder().id(UUID.randomUUID()).personId(UUID.randomUUID()).build();
        when(photos.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(people.findById(photo.getPersonId()))
                .thenReturn(Optional.of(Person.builder().wikidataId("Q42").build()));
        return photo;
    }
}

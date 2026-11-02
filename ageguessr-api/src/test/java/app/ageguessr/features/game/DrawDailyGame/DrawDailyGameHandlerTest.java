package app.ageguessr.features.game.DrawDailyGame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

class DrawDailyGameHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T00:00:05Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private final DailyRoundRepository rounds = mock(DailyRoundRepository.class);
    private final DrawCandidateRepository candidates = mock(DrawCandidateRepository.class);
    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);
    private final GameProperties properties = new GameProperties(3, 3, false);
    private final DrawDailyGameHandler handler = new DrawDailyGameHandler(
            rounds,
            candidates,
            photos,
            new DailyDraw(properties, new Random(1)),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            mock(PlatformTransactionManager.class));

    @Test
    void returnsADayAlreadyDrawnWithoutDrawingAgain() {
        List<DailyRound> existing = List.of(round(1, UUID.randomUUID()));
        when(rounds.findByGameDateOrderByPosition(DAY)).thenReturn(existing);

        assertThat(handler.drawIfMissing(DAY)).isEqualTo(existing);

        verifyNoInteractions(candidates, photos);
        verify(rounds, never()).saveAllAndFlush(anyList());
    }

    @Test
    void drawsAMissingDayInOrderAndRenewsThePublicIdOfEachPhoto() {
        List<DrawCandidate> pool = List.of(
                new DrawCandidate(UUID.randomUUID(), Gender.FEMALE, Continent.ASIA, null),
                new DrawCandidate(UUID.randomUUID(), Gender.MALE, Continent.ASIA, null),
                new DrawCandidate(UUID.randomUUID(), Gender.MALE, Continent.EUROPE, null));
        when(rounds.findByGameDateOrderByPosition(DAY)).thenReturn(List.of());
        when(candidates.findCandidates()).thenReturn(pool);
        when(rounds.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        List<PersonPhoto> drawnPhotos = pool.stream().map(c -> photo(c.photoId())).toList();
        List<UUID> oldPublicIds = drawnPhotos.stream().map(PersonPhoto::getPublicId).toList();
        when(photos.findAllById(anyList())).thenReturn(drawnPhotos);

        List<DailyRound> drawn = handler.drawIfMissing(DAY);

        assertThat(drawn).extracting(DailyRound::getPosition).containsExactly((short) 1, (short) 2, (short) 3);
        assertThat(drawn).allSatisfy(round -> {
            assertThat(round.getGameDate()).isEqualTo(DAY);
            assertThat(round.getDrawnAt()).isEqualTo(NOW);
        });
        assertThat(drawn).extracting(DailyRound::getPhotoId)
                .containsExactlyInAnyOrderElementsOf(pool.stream().map(DrawCandidate::photoId).toList());
        assertThat(drawnPhotos).extracting(PersonPhoto::getPublicId)
                .doesNotContainAnyElementsOf(oldPublicIds)
                .doesNotContainNull();
        verify(photos).saveAllAndFlush(drawnPhotos);
    }

    @Test
    void readsTheOtherDrawWhenADrawConflicts() {
        List<DailyRound> winner = List.of(round(1, UUID.randomUUID()));
        when(rounds.findByGameDateOrderByPosition(DAY)).thenReturn(List.of(), winner);
        when(candidates.findCandidates())
                .thenReturn(List.of(new DrawCandidate(UUID.randomUUID(), Gender.FEMALE, Continent.ASIA, null)));
        when(rounds.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("daily_rounds_pkey"));

        assertThat(handler.drawIfMissing(DAY)).isEqualTo(winner);

        verify(photos, never()).saveAllAndFlush(any());
    }

    @Test
    void savesNoRoundFromAnEmptyPool() {
        when(rounds.findByGameDateOrderByPosition(DAY)).thenReturn(List.of());
        when(candidates.findCandidates()).thenReturn(List.of());
        when(rounds.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(photos.findAllById(anyList())).thenReturn(List.of());

        assertThat(handler.drawIfMissing(DAY)).isEmpty();
    }

    private static DailyRound round(int position, UUID photoId) {
        return DailyRound.builder().gameDate(DAY).position((short) position).photoId(photoId).drawnAt(NOW).build();
    }

    private static PersonPhoto photo(UUID id) {
        return PersonPhoto.builder().id(id).publicId(UUID.randomUUID()).build();
    }
}

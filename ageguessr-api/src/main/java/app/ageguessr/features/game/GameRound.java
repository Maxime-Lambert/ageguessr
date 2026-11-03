package app.ageguessr.features.game;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A round with what it is played on, of the daily game or of a free series. Internal:
 * the photo carries the answer and its internal id, so a GameRound never leaves the API
 * as is, only through {@link RoundView}.
 *
 * @param gameDate the day of the game, or the day a series was created: the hints aim at
 *     its year minus the age
 * @param photo null when the round is neutralized, as {@code person}
 * @param seriesId null for the daily game
 */
public record GameRound(
        LocalDate gameDate,
        short position,
        DailyRoundStatus status,
        PersonPhoto photo,
        Person person,
        UUID seriesId) {

    /** A round of the daily game. */
    public GameRound(LocalDate gameDate, short position, DailyRoundStatus status, PersonPhoto photo, Person person) {
        this(gameDate, position, status, photo, person, null);
    }

    public boolean playable() {
        return status == DailyRoundStatus.PLAYABLE;
    }

    public int age() {
        return photo.getAgeAtPhoto();
    }

    /**
     * What makes the hint of a category the same for every player of this round. For the
     * daily game it is exactly the key used before the series existed, so that no hint
     * changes on the day of the release.
     */
    public String hintKey() {
        return (seriesId == null ? gameDate.toString() : "series:" + seriesId) + "|" + position;
    }
}

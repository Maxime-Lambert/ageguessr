package app.ageguessr.features.game;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import java.time.LocalDate;

/**
 * A round of a day with what it is played on. Internal: the photo carries the answer and
 * its internal id, so a GameRound never leaves the API as is, only through
 * {@link RoundView}.
 *
 * @param photo null when the round is neutralized, as {@code person}
 */
public record GameRound(LocalDate gameDate, short position, DailyRoundStatus status, PersonPhoto photo, Person person) {

    public boolean playable() {
        return status == DailyRoundStatus.PLAYABLE;
    }

    public int age() {
        return photo.getAgeAtPhoto();
    }
}

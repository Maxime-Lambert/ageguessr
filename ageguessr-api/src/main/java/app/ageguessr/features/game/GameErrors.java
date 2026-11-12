package app.ageguessr.features.game;

import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.exceptions.ValidationException;

/**
 * The business refusals of the game. Those of a guess are 409: the request is valid, the
 * moment is not.
 */
public final class GameErrors {

    private GameErrors() {}

    /** Only today's game is played: a round started before midnight is closed after it. */
    public static ConflictException gameClosed() {
        return new ConflictException("GAME_CLOSED", "Only today's game can be played");
    }

    /** The photo was rejected or its person excluded since the draw: out for everyone. */
    public static ConflictException roundNeutralized() {
        return new ConflictException("ROUND_NEUTRALIZED", "This round was removed from the game");
    }

    /** Already played, not yet allowed, or played at the same time from another tab. */
    public static ConflictException wrongStep() {
        return new ConflictException("WRONG_STEP", "This step of the round is not open");
    }

    public static ConflictException categoryUnavailable() {
        return new ConflictException("CATEGORY_UNAVAILABLE", "No hint of this category for this round");
    }

    /** Not the number of categories the round asks for, or the same one twice. */
    public static ValidationException wrongHintCount() {
        return new ValidationException("HINT_COUNT", "Pick the number of distinct hint categories the round asks for");
    }

    /** Unknown, purged, or not one of the player's series: the same answer for all three. */
    public static NotFoundException seriesNotFound() {
        return new NotFoundException("No such series");
    }

    /** The pool has no photo left to draw (empty in a fresh environment). */
    public static ConflictException noPhotos() {
        return new ConflictException("NO_PHOTOS", "No photo can be drawn for a series");
    }

    /** Unknown or expired (purged): the same answer for both. */
    public static NotFoundException challengeNotFound() {
        return new NotFoundException("No such challenge");
    }

    /** Only the creator shares a series, and once it is played. */
    public static ConflictException notCreator() {
        return new ConflictException("NOT_CREATOR", "Only the creator of a series shares it");
    }

    public static ConflictException seriesNotFinished() {
        return new ConflictException("SERIES_NOT_FINISHED", "A series is shared once played");
    }

    /** A guest's nickname is an account's username: they would pass for it. */
    public static ValidationException nicknameTaken() {
        return new ValidationException("NICKNAME_TAKEN", "This name belongs to an account");
    }

    public static ValidationException nicknameRequired() {
        return new ValidationException("NICKNAME_REQUIRED", "A guest names themselves in a challenge");
    }

    /** A round is reported from its result: once finished by the player who reports it. */
    public static ConflictException roundNotFinished() {
        return new ConflictException("ROUND_NOT_FINISHED", "A round is reported once finished");
    }

    /** The round showed no hint, or the hint was rejected since: nothing left to report. */
    public static ConflictException nothingToReport() {
        return new ConflictException("NOTHING_TO_REPORT", "This round has nothing of this kind to report");
    }

    /** A hint reported without saying which, in a round that showed several. */
    public static ValidationException hintCategoryRequired() {
        return new ValidationException("HINT_CATEGORY_REQUIRED", "Say which hint of the round is reported");
    }

    /** A reason of the other target, or none of the list. */
    public static ValidationException invalidReason() {
        return new ValidationException("INVALID_REASON", "This reason does not apply to this report");
    }
}

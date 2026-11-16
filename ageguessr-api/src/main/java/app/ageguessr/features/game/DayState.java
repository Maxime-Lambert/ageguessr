package app.ageguessr.features.game;

/** A day of the calendar, for one player (GetCalendar). */
public enum DayState {
    /** To come, never drawn, or with no round left to play. */
    UNAVAILABLE,
    /** Drawn, up to today, and no round started yet. */
    AVAILABLE,
    /** At least one round started, a playable one left. */
    STARTED,
    /** Every playable round finished (a complete day, PlayerHistory). */
    FINISHED
}

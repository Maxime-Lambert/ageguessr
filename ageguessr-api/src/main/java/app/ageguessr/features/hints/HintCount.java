package app.ageguessr.features.hints;

/** How many approved hints share a year, a category and an audience. */
public record HintCount(int year, HintCategory category, HintAudience audience, long total) {}

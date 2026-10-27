package app.ageguessr.features.people.IngestPeople;

import java.util.List;

/**
 * Structured Data on Commons for one file: its "inception" dates (P571) and how many
 * subjects it "depicts" (P180). More than one subject usually means a group photo.
 */
record MediaInfo(List<PhotoDate> inceptions, int depictedCount) {

    static final MediaInfo NONE = new MediaInfo(List.of(), 0);
}

package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Runs the parsing, filter, evaluator and selector over everything recorded for Mallika
 * Sherawat (Q262665, born 1976-10-24) and checks that the Java rules give what the spike
 * measured by hand: 5 valid photos out of 26 (plans/done/people-sourcing.md, Phase 1).
 */
class SpikeRegressionTest {

    private static final LocalDate BIRTH = LocalDate.of(1976, 10, 24);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void findsTheFivePhotosTheSpikeFound() {
        assertThat(acceptedCandidates())
                .extracting(PhotoSelector.Candidate::fileTitle)
                .containsExactlyInAnyOrder(
                        "File:ROMY2011 a21 Mallika Sherawat.jpg",
                        "File:The Myth 007 (7271376614).jpg",
                        "File:The Myth 008 (7271376754).jpg",
                        "File:The Myth 009 (7271376864).jpg",
                        "File:Mallika Sherawat Cannes 141 (28039077121).jpg");
    }

    @Test
    void keepsTwoOfThemTenYearsApart() {
        List<PhotoSelector.Candidate> kept = PhotoSelector.select(List.of(), acceptedCandidates(), 5, 10, Map.of());

        assertThat(kept)
                .extracting(PhotoSelector.Candidate::fileTitle, PhotoSelector.Candidate::age)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("File:Mallika Sherawat Cannes 141 (28039077121).jpg", 39),
                        org.assertj.core.groups.Tuple.tuple("File:The Myth 007 (7271376614).jpg", 29));
    }

    private static List<PhotoSelector.Candidate> acceptedCandidates() {
        Map<Long, MediaInfo> mediaInfo = new java.util.HashMap<>();
        for (JsonNode entity : Fixtures.json("commons-mediainfo-Q262665.json").path("entities")) {
            mediaInfo.put(Long.parseLong(entity.path("id").asString().substring(1)), CommonsClient.parseMediaInfo(entity));
        }
        List<PhotoSelector.Candidate> accepted = new ArrayList<>();
        for (JsonNode page : Fixtures.json("commons-imageinfo-Q262665.json").path("query").path("pages")) {
            CommonsFile file = CommonsClient.parseFile(page).orElseThrow();
            MediaInfo info = mediaInfo.getOrDefault(file.pageId(), MediaInfo.NONE);
            // No height floor: the fixture was recorded with 800 px wide thumbnails.
            if (PhotoCandidateFilter.check(file, info, 0).isPresent()) {
                continue;
            }
            PhotoEvaluation evaluation = PhotoEvaluator.evaluate(
                    new PhotoEvaluator.Evidence(
                            file.title(), info.inceptions(), file.informationDate(), file.exifDateTimeOriginal()),
                    BIRTH,
                    null,
                    TODAY);
            if (evaluation instanceof PhotoEvaluation.Accepted ok) {
                accepted.add(new PhotoSelector.Candidate(file.title(), false, ok));
            }
        }
        return accepted;
    }
}

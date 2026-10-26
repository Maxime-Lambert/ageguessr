package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.PhotoDateSource;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether a photo gives the person's age exactly. Validated on real data in the
 * spike of plans/active-plan.md (Phase 1): the declared date alone is not enough, since
 * an old print scanned years later is often "dated" with the scan or upload day. The
 * camera's EXIF must agree, and no year in the file name may contradict it.
 */
final class PhotoEvaluator {

    /** Year of the first practical photographic process (daguerreotype). */
    static final int FIRST_PHOTOGRAPH_YEAR = 1839;

    static final int MAX_AGE = 120;

    private static final Pattern YEAR_IN_NAME = Pattern.compile("(?<!\\d)(1[89]\\d\\d|20\\d\\d)(?!\\d)");

    private PhotoEvaluator() {}

    /** What Commons tells about one file's date. {@code structuredDate} comes from SDC "inception" (P571). */
    record Evidence(
            String fileTitle,
            Optional<PhotoDate> structuredDate,
            String informationDate,
            String exifDateTimeOriginal) {}

    static PhotoEvaluation evaluate(Evidence evidence, LocalDate birth, LocalDate death, LocalDate today) {
        Optional<PhotoDate> structured = evidence.structuredDate();
        Optional<PhotoDate> information = PhotoDateParser.parseInformationDate(evidence.informationDate());

        PhotoDate date;
        PhotoDateSource source;
        if (structured.isPresent() && information.isPresent()) {
            Optional<PhotoDate> overlap = structured.get().intersection(information.get());
            if (overlap.isEmpty()) {
                return new PhotoEvaluation.Rejected(PhotoRejection.CONFLICTING_DATES);
            }
            date = overlap.get();
            source = PhotoDateSource.BOTH;
        } else if (structured.isPresent()) {
            date = structured.get();
            source = PhotoDateSource.STRUCTURED_DATA;
        } else if (information.isPresent()) {
            date = information.get();
            source = PhotoDateSource.INFORMATION_TEMPLATE;
        } else {
            return new PhotoEvaluation.Rejected(PhotoRejection.NOT_DATED);
        }

        // Before birth, in the future, or after death: a grave, a statue, a plaque...
        if (date.from().isBefore(birth) || date.to().isAfter(today) || (death != null && date.to().isAfter(death))) {
            return new PhotoEvaluation.Rejected(PhotoRejection.OUTSIDE_LIFETIME);
        }
        if (date.from().getYear() < FIRST_PHOTOGRAPH_YEAR) {
            return new PhotoEvaluation.Rejected(PhotoRejection.BEFORE_PHOTOGRAPHY);
        }
        int age = ageOn(birth, date.from());
        if (age != ageOn(birth, date.to())) {
            return new PhotoEvaluation.Rejected(PhotoRejection.AMBIGUOUS_AGE);
        }
        if (age > MAX_AGE) {
            return new PhotoEvaluation.Rejected(PhotoRejection.AGE_OUT_OF_RANGE);
        }
        Set<Integer> yearsInName = yearsIn(evidence.fileTitle(), today.getYear());
        if (!yearsInName.isEmpty() && !yearsInName.contains(date.from().getYear())) {
            return new PhotoEvaluation.Rejected(PhotoRejection.FILE_NAME_YEAR_CONFLICT);
        }
        Optional<LocalDate> exif = PhotoDateParser.parseExifDate(evidence.exifDateTimeOriginal());
        if (exif.isEmpty()) {
            return new PhotoEvaluation.Rejected(PhotoRejection.NO_EXIF_DATE);
        }
        if (!date.contains(exif.get())) {
            return new PhotoEvaluation.Rejected(PhotoRejection.EXIF_CONFLICT);
        }
        return new PhotoEvaluation.Accepted(date, source, age);
    }

    /**
     * Completed years, as java.time counts them: someone born on February 29 turns one
     * on March 1 of a non-leap year.
     */
    static int ageOn(LocalDate birth, LocalDate day) {
        return Period.between(birth, day).getYears();
    }

    /** Plausible photo years only, so "IMG_0042" or a 1700s portrait reference do not count. */
    private static Set<Integer> yearsIn(String fileTitle, int currentYear) {
        Matcher m = YEAR_IN_NAME.matcher(fileTitle);
        return m.results()
                .map(r -> Integer.parseInt(r.group(1)))
                .filter(y -> y >= FIRST_PHOTOGRAPH_YEAR && y <= currentYear)
                .collect(Collectors.toSet());
    }
}

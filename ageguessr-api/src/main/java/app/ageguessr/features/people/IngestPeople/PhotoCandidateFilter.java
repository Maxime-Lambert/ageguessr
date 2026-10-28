package app.ageguessr.features.people.IngestPeople;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks that do not depend on dates: the file must be a JPEG photo, under a license we
 * can honor, of one person, and tall enough to show.
 */
final class PhotoCandidateFilter {

    static final String JPEG = "image/jpeg";

    // Whole tokens only: "PD-Finland" contains the letters "ND" but is public domain.
    private static final Pattern NON_COMMERCIAL_OR_NO_DERIVATIVES = Pattern.compile("(?<![A-Z])N[CD](?![A-Z])");
    // CC0, CC BY or CC BY-SA, then optionally a version and a port ("CC BY-SA 3.0 de");
    // public domain, spelled out or as a Commons "PD-..." tag.
    private static final Pattern ALLOWED = Pattern.compile("^(CC0|CC BY|CC BY-SA)( .*)?$|^PUBLIC DOMAIN$|^PD(-.*)?$");

    private PhotoCandidateFilter() {}

    static Optional<PhotoRejection> check(CommonsFile file, MediaInfo mediaInfo, int minHeight) {
        // JPEG only: photographs are JPEG; PNG, SVG, GIF and TIFF on Commons are mostly
        // drawings, logos, scans of documents or animations.
        if (!JPEG.equals(file.mime()) || file.thumbnailUrl() == null || file.descriptionUrl() == null) {
            return Optional.of(PhotoRejection.UNSUPPORTED_FORMAT);
        }
        if (!isAllowedLicense(file.licenseShortName())) {
            return Optional.of(PhotoRejection.LICENSE_NOT_ALLOWED);
        }
        if (mediaInfo.depictedCount() > 1) {
            return Optional.of(PhotoRejection.GROUP_PHOTO);
        }
        // The thumbnail is asked by height and Commons never enlarges: a short one means
        // a small original. Checked before download and selection, so a small photo
        // never takes the place of a usable one of the same person.
        if (file.thumbnailHeight() < minHeight) {
            return Optional.of(PhotoRejection.TOO_SMALL);
        }
        return Optional.empty();
    }

    /**
     * Allow-list, so anything unexpected is refused. Public domain and CC0 need nothing;
     * CC BY and CC BY-SA need the author and license credited, which we store and show
     * once the round is revealed. Refused: GFDL (its full text must ship with the image),
     * "Copyrighted free use" and other free-form terms, and any NC/ND variant (Commons
     * does not host them, but a mislabelled file must not slip through).
     */
    static boolean isAllowedLicense(String shortName) {
        if (shortName == null) {
            return false;
        }
        String name = shortName.strip().toUpperCase(Locale.ROOT);
        if (NON_COMMERCIAL_OR_NO_DERIVATIVES.matcher(name).find()) {
            return false;
        }
        return ALLOWED.matcher(name).matches();
    }
}

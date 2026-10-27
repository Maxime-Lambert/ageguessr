package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhotoCandidateFilterTest {

    @Test
    void acceptsAJpegUnderAFreeLicenseOfOnePerson() {
        assertThat(PhotoCandidateFilter.check(file("image/jpeg", "CC BY-SA 4.0"), new MediaInfo(List.of(), 1))).isEmpty();
    }

    @Test
    void acceptsAFileWithoutAnyDepiction() {
        // The main Wikidata image often has no Structured Data at all.
        assertThat(PhotoCandidateFilter.check(file("image/jpeg", "CC BY-SA 4.0"), MediaInfo.NONE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/gif", "image/tiff", "image/svg+xml", "image/webp"})
    void rejectsAnythingButJpeg(String mime) {
        assertThat(PhotoCandidateFilter.check(file(mime, "CC0"), MediaInfo.NONE))
                .contains(PhotoRejection.UNSUPPORTED_FORMAT);
    }

    @Test
    void rejectsAFileWithoutThumbnailOrSourcePage() {
        CommonsFile noThumb = new CommonsFile("File:A.jpg", 1, "image/jpeg", null, "https://commons/A", null, null, "CC0", null, null);
        CommonsFile noPage = new CommonsFile("File:A.jpg", 1, "image/jpeg", "https://upload/A", null, null, null, "CC0", null, null);

        assertThat(PhotoCandidateFilter.check(noThumb, MediaInfo.NONE)).contains(PhotoRejection.UNSUPPORTED_FORMAT);
        assertThat(PhotoCandidateFilter.check(noPage, MediaInfo.NONE)).contains(PhotoRejection.UNSUPPORTED_FORMAT);
    }

    @Test
    void rejectsALicenseOutsideTheAllowList() {
        assertThat(PhotoCandidateFilter.check(file("image/jpeg", "GFDL"), MediaInfo.NONE))
                .contains(PhotoRejection.LICENSE_NOT_ALLOWED);
    }

    @Test
    void rejectsAGroupPhoto() {
        assertThat(PhotoCandidateFilter.check(file("image/jpeg", "CC0"), new MediaInfo(List.of(), 2)))
                .contains(PhotoRejection.GROUP_PHOTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CC0", "CC0 1.0", "CC BY 2.0", "CC BY 4.0", "CC BY", "CC BY-SA 3.0", "CC BY-SA 3.0 de", "CC BY-SA 4.0",
        "Public domain", "PD-USGov", "PD-Finland", "PD-Poland", "cc by-sa 2.0"
    })
    void allowsPublicDomainAndCreativeCommonsByAndBySa(String license) {
        assertThat(PhotoCandidateFilter.isAllowedLicense(license)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", "Attribution", "GFDL", "GFDL 1.2", "Copyrighted free use", "FAL", "CC BY-NC 2.0", "CC BY-ND 4.0",
        "CC BY-NC-SA 3.0", "CC BY-SAX", "CC BYE", "CC0X", "PDF", "CCBY", "All rights reserved"
    })
    void refusesEverythingElse(String license) {
        assertThat(PhotoCandidateFilter.isAllowedLicense(license)).isFalse();
    }

    private static CommonsFile file(String mime, String license) {
        return new CommonsFile(
                "File:A.jpg", 1, mime, "https://upload.wikimedia.org/a.jpg", "https://commons.wikimedia.org/wiki/File:A.jpg",
                null, null, license, null, null);
    }
}

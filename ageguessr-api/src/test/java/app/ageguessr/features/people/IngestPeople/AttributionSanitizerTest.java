package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttributionSanitizerTest {

    @Test
    void keepsOnlyTheTextOfARealCommonsAuthor() {
        // From commons-imageinfo-Q262665.json.
        String html = "<bdi><a href=\"https://en.wikipedia.org/wiki/en:Bollywood_Hungama\" class=\"extiw\" "
                + "title=\"w:en:Bollywood Hungama\"><span title=\"news provider\">Bollywood Hungama</span></a>\n</bdi>";

        assertThat(AttributionSanitizer.toPlainText(html)).isEqualTo("Bollywood Hungama");
    }

    @Test
    void decodesEntitiesAndCollapsesWhitespace() {
        assertThat(AttributionSanitizer.toPlainText("Jean&nbsp;&amp;\n  Marie   O&#39;Neil"))
                .isEqualTo("Jean & Marie O'Neil");
    }

    @Test
    void dropsScriptsAndTheirContent() {
        assertThat(AttributionSanitizer.toPlainText("<script>alert(1)</script>Someone<img src=x onerror=alert(2)>"))
                .isEqualTo("Someone");
    }

    @Test
    void anEmptyOrMissingAuthorIsNull() {
        assertThat(AttributionSanitizer.toPlainText(null)).isNull();
        assertThat(AttributionSanitizer.toPlainText("  <span> </span> ")).isNull();
    }

    @Test
    void truncatesToTheColumnSize() {
        String text = AttributionSanitizer.toPlainText("a".repeat(2000));

        assertThat(text).hasSize(AttributionSanitizer.MAX_LENGTH).endsWith("…");
        assertThat(AttributionSanitizer.toPlainText("a".repeat(AttributionSanitizer.MAX_LENGTH)))
                .hasSize(AttributionSanitizer.MAX_LENGTH)
                .doesNotContain("…");
    }
}

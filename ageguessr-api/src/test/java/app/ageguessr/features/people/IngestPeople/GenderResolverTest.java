package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Gender;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenderResolverTest {

    @Test
    void readsMaleAndFemale() {
        assertThat(GenderResolver.resolve(Set.of("Q6581097"))).contains(Gender.MALE);
        assertThat(GenderResolver.resolve(Set.of("Q6581072"))).contains(Gender.FEMALE);
    }

    @Test
    void countsTransWomenAsWomenAndTransMenAsMen() {
        assertThat(GenderResolver.resolve(Set.of("Q1052281"))).contains(Gender.FEMALE);
        assertThat(GenderResolver.resolve(Set.of("Q2449503"))).contains(Gender.MALE);
    }

    @Test
    void readsTheCisgenderValuesLikeThePlainOnes() {
        assertThat(GenderResolver.resolve(Set.of("Q15145779"))).contains(Gender.FEMALE);
        assertThat(GenderResolver.resolve(Set.of("Q15145778"))).contains(Gender.MALE);
        // Two values meaning the same gender do not disagree.
        assertThat(GenderResolver.resolve(Set.of("Q6581072", "Q15145779"))).contains(Gender.FEMALE);
    }

    @Test
    void anyOtherValueIsOther() {
        // Non-binary.
        assertThat(GenderResolver.resolve(Set.of("Q48270"))).contains(Gender.OTHER);
    }

    @Test
    void valuesThatDisagreeGiveOther() {
        assertThat(GenderResolver.resolve(Set.of("Q6581097", "Q6581072"))).contains(Gender.OTHER);
        assertThat(GenderResolver.resolve(Set.of("Q6581072", "Q48270"))).contains(Gender.OTHER);
    }

    @Test
    void noValueIsUnknown() {
        assertThat(GenderResolver.resolve(Set.of())).isEmpty();
    }
}

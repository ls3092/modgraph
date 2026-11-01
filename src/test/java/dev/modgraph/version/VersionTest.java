package dev.modgraph.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionTest {

    private static int compare(final String left, final String right) {
        return Version.parse(left).compareTo(Version.parse(right));
    }

    @Test
    void numericSegmentsCompareNumerically() {
        assertThat(compare("1.10", "1.9")).isPositive();
        assertThat(compare("2.0", "10.0")).isNegative();
        assertThat(compare("1.0", "1.0")).isZero();
    }

    @Test
    void missingTrailingSegmentCountsAsZero() {
        assertThat(compare("1.21", "1.21.0")).isZero();
        assertThat(compare("1.21.0", "1.21")).isZero();
        assertThat(compare("1.21", "1.21.1")).isNegative();
    }

    @Test
    void preReleaseSortsBelowTheRelease() {
        assertThat(compare("1.21.1-beta", "1.21.1")).isNegative();
        assertThat(compare("21.0.0-beta", "21.0.0")).isNegative();
        assertThat(compare("21.0.110-beta", "21.0.110")).isNegative();
        assertThat(compare("1.21.1-rc1", "1.21.1-beta")).isPositive();
    }

    @Test
    void nonNumericSegmentsCompareLexicographically() {
        assertThat(compare("1.0.0-alpha", "1.0.0-beta")).isNegative();
        assertThat(compare("1.0.0-snapshot", "1.0.0-release")).isPositive();
    }

    @Test
    void buildMetadataIsIgnored() {
        assertThat(compare("0.8.13+mc1.21.1", "0.8.13")).isZero();
        assertThat(compare("0.116.17+1.21.1", "0.116.6+1.21.1")).isPositive();
        assertThat(Version.parse("0.8.13+mc1.21.1").source()).isEqualTo("0.8.13+mc1.21.1");
    }

    @Test
    void veryLargeNumericSegmentsDoNotOverflow() {
        assertThat(compare("1.20240101120000", "1.20240101120001")).isNegative();
        assertThat(compare("1.99999999999999999999", "1.9")).isPositive();
    }

    @Test
    void trailingDashDoesNotChangeTheVersion() {
        assertThat(compare("1.21-", "1.21")).isZero();
        assertThat(compare("1.21-", "1.21.0")).isZero();
    }

    @Test
    void wildcardMatchesAnyValueInThatPosition() {
        final Version pattern = Version.parse("1.21.x");
        assertThat(pattern.hasWildcard()).isTrue();
        assertThat(pattern.matchesWildcard(Version.parse("1.21.1"))).isTrue();
        assertThat(pattern.matchesWildcard(Version.parse("1.21"))).isTrue();
        assertThat(pattern.matchesWildcard(Version.parse("1.21.1.5"))).isTrue();
        assertThat(pattern.matchesWildcard(Version.parse("1.22"))).isFalse();
        assertThat(pattern.matchesWildcard(Version.parse("0.21.1"))).isFalse();

        assertThat(Version.parse("0.8.*").matchesWildcard(Version.parse("0.8.13+mc1.21.1")))
                .isTrue();
    }

    @Test
    void nonWildcardPatternStillRequiresEqualTrailingSegments() {
        final Version pattern = Version.parse("1.21.x");
        assertThat(pattern.matchesWildcard(Version.parse("1.21.0.7"))).isTrue();

        final Version middle = Version.parse("1.x.3");
        assertThat(middle.matchesWildcard(Version.parse("1.9.3"))).isTrue();
        assertThat(middle.matchesWildcard(Version.parse("1.9.3.1"))).isFalse();
    }

    @Test
    void buildPlaceholdersAreRejected() {
        assertThatThrownBy(() -> Version.parse("${file.jarVersion}"))
                .isInstanceOf(VersionParseException.class)
                .hasMessageContaining("占位符");
        assertThatThrownBy(() -> Version.parse("#compatible_neoforge#"))
                .isInstanceOf(VersionParseException.class);
    }

    @Test
    void emptyAndMalformedVersionsAreRejected() {
        assertThatThrownBy(() -> Version.parse("")).isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> Version.parse("   ")).isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> Version.parse("+build")).isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> Version.parse("1..2")).isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> Version.parse("1--2")).isInstanceOf(VersionParseException.class);
    }
}

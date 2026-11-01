package dev.modgraph.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NeoForgeVersionRangeTest {

    private static boolean matches(final String range, final String version) {
        return NeoForgeVersionRange.parse(range).matches(Version.parse(version));
    }

    @Test
    void lowerBoundInclusiveAndExclusive() {
        assertThat(matches("[21.1.219,)", "21.1.219")).isTrue();
        assertThat(matches("[21.1.219,)", "21.1.218")).isFalse();
        assertThat(matches("(21.1.219,)", "21.1.219")).isFalse();
        assertThat(matches("(21.1.219,)", "21.1.220")).isTrue();
    }

    @Test
    void upperBoundInclusiveAndExclusive() {
        assertThat(matches("(,1.22]", "1.22")).isTrue();
        assertThat(matches("(,1.22]", "1.22.1")).isFalse();
        assertThat(matches("[1.21,1.22)", "1.21.1")).isTrue();
        assertThat(matches("[1.21,1.22)", "1.22")).isFalse();
        assertThat(matches("[1.21,1.21.1)", "1.21.1")).isFalse();
    }

    @Test
    void exactVersionUsesSquareBrackets() {
        assertThat(matches("[1.21.1]", "1.21.1")).isTrue();
        assertThat(matches("[1.21.1]", "1.21.1.0")).isTrue();
        assertThat(matches("[1.21.1]", "1.21.2")).isFalse();
    }

    @Test
    void unbracketedValueIsASoftRequirement() {
        final NeoForgeVersionRange range = NeoForgeVersionRange.parse("21.1.219");
        assertThat(range.unbracketed()).isTrue();
        assertThat(range.matches(Version.parse("21.1.219"))).isTrue();
        assertThat(range.matches(Version.parse("21.1.262"))).isTrue();
        assertThat(range.matches(Version.parse("21.1.218"))).isFalse();

        assertThat(NeoForgeVersionRange.parse("[21.1.219,)").unbracketed()).isFalse();
    }

    @Test
    void spacesInsideTheRangeAreTolerated() {
        assertThat(matches("[1.21, 1.22)", "1.21.1")).isTrue();
        assertThat(matches("[21.0.110-beta, 21.2.0)", "21.1.262")).isTrue();
        assertThat(matches("[21.0.110-beta, 21.2.0)", "21.2.0")).isFalse();
    }

    @Test
    void mismatchedClosingBracketIsAcceptedAsUnboundedUpper() {
        assertThat(matches("[21.1.247,]", "21.1.247")).isTrue();
        assertThat(matches("[21.1.247,]", "21.1.262")).isTrue();
        assertThat(matches("[21.1.247,]", "21.1.246")).isFalse();
    }

    @Test
    void starAndBlankMeanAnyVersion() {
        assertThat(NeoForgeVersionRange.parse("*").unbracketed()).isFalse();
        assertThat(matches("*", "21.1.262")).isTrue();
        assertThat(matches("", "0.0.1")).isTrue();
    }

    @Test
    void unionOfRangesIsSupported() {
        assertThat(matches("(,1.0],[1.2,)", "0.5")).isTrue();
        assertThat(matches("(,1.0],[1.2,)", "1.1")).isFalse();
        assertThat(matches("(,1.0],[1.2,)", "1.5")).isTrue();
    }

    @Test
    void preReleaseBoundsCompareLikeVersions() {
        assertThat(matches("[21.0.0-beta,)", "21.0.0-beta")).isTrue();
        assertThat(matches("[21.0.0-beta,)", "21.0.0")).isTrue();
        assertThat(matches("[21.0.0-beta,)", "21.0.0-alpha")).isFalse();
        assertThat(matches("[21-beta,)", "21.1.262")).isTrue();
    }

    @Test
    void malformedRangesAreRejected() {
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("[#compatible_neoforge#)"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("[1.21"))
                .isInstanceOf(VersionParseException.class)
                .hasMessageContaining("闭合");
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("1.21,1.22"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("(1.21.1)"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("[1.21,1.22,1.23)"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> NeoForgeVersionRange.parse("[1.21,1.22)x"))
                .isInstanceOf(VersionParseException.class);
    }
}

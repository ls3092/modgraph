package dev.modgraph.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FabricVersionRangeTest {

    private static boolean matches(final String range, final String version) {
        return FabricVersionRange.parse(range).matches(Version.parse(version));
    }

    @Test
    void starMatchesEverything() {
        assertThat(matches("*", "1.21.1")).isTrue();
        assertThat(matches("*", "0.0.1")).isTrue();
        assertThat(matches(" * ", "21")).isTrue();
    }

    @Test
    void blankRangeIsUnconstrained() {
        assertThat(matches("", "1.21.1")).isTrue();
        assertThat(matches("   ", "0.0.0")).isTrue();
    }

    @Test
    void comparisonOperatorsFollowFabricSemantics() {
        assertThat(matches(">=1.21", "1.21")).isTrue();
        assertThat(matches(">=1.21", "1.21.1")).isTrue();
        assertThat(matches(">=1.21", "1.20.9")).isFalse();
        assertThat(matches(">1.21.1", "1.21.1")).isFalse();
        assertThat(matches(">1.21.1", "1.21.1.1")).isTrue();
        assertThat(matches("<1.21.2", "1.21.1")).isTrue();
        assertThat(matches("<1.21.2", "1.21.2")).isFalse();
        assertThat(matches("<=1.21", "1.21.0")).isTrue();
        assertThat(matches("<=1.21", "1.21.1")).isFalse();
        assertThat(matches("==1.21.1", "1.21.1")).isTrue();
        assertThat(matches("=1.21.1", "1.21.1")).isTrue();
        assertThat(matches("=1.21.1", "1.21.2")).isFalse();
    }

    @Test
    void bareVersionIsAnExactMatch() {
        assertThat(matches("1.21.1", "1.21.1")).isTrue();
        assertThat(matches("1.21.1", "1.21.1.0")).isTrue();
        assertThat(matches("1.21.1", "1.21.2")).isFalse();
        assertThat(matches("21", "21")).isTrue();
        assertThat(matches("1.21", "1.21.1")).isFalse();
    }

    @Test
    void tildeBumpsTheMinorSegment() {
        assertThat(matches("~1.2", "1.2")).isTrue();
        assertThat(matches("~1.2", "1.2.9")).isTrue();
        assertThat(matches("~1.2", "1.3")).isFalse();
        assertThat(matches("~1.2", "1.1.9")).isFalse();
        assertThat(matches("~1.21", "1.21.1")).isTrue();
        assertThat(matches("~1.21", "1.22")).isFalse();
        assertThat(matches("~1.21.1", "1.21.9")).isTrue();
        assertThat(matches("~1.21.1", "1.22")).isFalse();
    }

    @Test
    void wildcardSegmentMatchesAnyValue() {
        assertThat(matches("1.21.x", "1.21.1")).isTrue();
        assertThat(matches("1.21.X", "1.21.0")).isTrue();
        assertThat(matches("0.8.x", "0.8.13+mc1.21.1")).isTrue();
        assertThat(matches("1.21.x", "1.22")).isFalse();
    }

    @Test
    void spaceSeparatedConstraintsAllMustHold() {
        assertThat(matches(">=1.21 <=1.21.1", "1.21.1")).isTrue();
        assertThat(matches(">=1.21 <=1.21.1", "1.21.2")).isFalse();
        assertThat(matches(">=1.21 <1.21.2", "1.21.1")).isTrue();
        assertThat(matches(">=6.6.3 <6.7", "6.6.9")).isTrue();
        assertThat(matches(">=6.6.3 <6.7", "6.7")).isFalse();
    }

    @Test
    void tildeCanBeCombinedWithOtherConstraints() {
        assertThat(matches("~1.21 <1.21.2", "1.21.1")).isTrue();
        assertThat(matches("~1.21 <1.21.2", "1.21.2")).isFalse();
    }

    @Test
    void trailingDashInRealDataIsAccepted() {
        assertThat(matches(">=1.21-", "1.21.1")).isTrue();
        assertThat(matches(">=1.21-", "1.21")).isTrue();
        assertThat(matches(">=1.21- <1.21.2-", "1.21.1")).isTrue();
        assertThat(matches("~1.21-", "1.21.1")).isTrue();
    }

    @Test
    void unknownOrAmbiguousSyntaxIsRejected() {
        assertThatThrownBy(() -> FabricVersionRange.parse("^1.2"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> FabricVersionRange.parse(">=1.2.x"))
                .isInstanceOf(VersionParseException.class)
                .hasMessageContaining("通配");
        assertThatThrownBy(() -> FabricVersionRange.parse(">="))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> FabricVersionRange.parse("abc"))
                .isInstanceOf(VersionParseException.class);
        assertThatThrownBy(() -> FabricVersionRange.parse(">=${version}"))
                .isInstanceOf(VersionParseException.class);
    }
}

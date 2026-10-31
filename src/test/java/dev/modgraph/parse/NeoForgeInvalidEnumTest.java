package dev.modgraph.parse;

import dev.modgraph.model.FailureCode;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NeoForgeInvalidEnumTest {

    private static final Path FIXTURE_ROOT = Path.of("src", "test", "resources", "fixtures");

    private static List<ScannedJar> scanned;

    @BeforeAll
    static void scanFixtureJars() throws IOException {
        scanned = new JarScanner().scan(FIXTURE_ROOT);
    }

    @Test
    void invalidOrderingIsFailedAndLoggedAsError() {
        final ParseResult result = parseWithErrorCapture("neoforge-invalid-ordering.jar");

        assertThat(result.candidate().status()).isEqualTo(Status.FAILED);
        assertThat(result.candidate().failureCode()).isEqualTo(FailureCode.MALFORMED_TOML);
        assertThat(result.candidate().failureMessage())
                .contains("ordering")
                .contains("SIDEWAYS");
        assertThat(result.errors())
                .anySatisfy(message -> assertThat(message)
                        .contains("MALFORMED_TOML")
                        .contains("ordering")
                        .contains("SIDEWAYS"));
    }

    @Test
    void invalidSideIsFailedAndLoggedAsError() {
        final ParseResult result = parseWithErrorCapture("neoforge-invalid-side.jar");

        assertThat(result.candidate().status()).isEqualTo(Status.FAILED);
        assertThat(result.candidate().failureCode()).isEqualTo(FailureCode.MALFORMED_TOML);
        assertThat(result.candidate().failureMessage())
                .contains("side")
                .contains("CLIENT_AND_SERVER");
        assertThat(result.errors())
                .anySatisfy(message -> assertThat(message)
                        .contains("MALFORMED_TOML")
                        .contains("side")
                        .contains("CLIENT_AND_SERVER"));
    }

    private static ParseResult parseWithErrorCapture(final String sourceFile) {
        final ScannedJar jar = fixture(sourceFile);
        final ErrorCollector collector = new ErrorCollector();
        collector.start();

        final Logger parserLogger = (Logger) LogManager.getLogger(NeoForgeMetadataParser.class);
        parserLogger.addAppender(collector);

        final ModCandidate candidate;
        try {
            candidate = new NeoForgeMetadataParser().parse(jar);
        } finally {
            parserLogger.removeAppender(collector);
        }
        return new ParseResult(candidate, collector.messages());
    }

    private static ScannedJar fixture(final String sourceFile) {
        return scanned.stream()
                .filter(candidate -> candidate.jarPath().getFileName().toString().equals(sourceFile))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少测试 fixture: " + sourceFile));
    }

    private record ParseResult(ModCandidate candidate, List<String> errors) {
    }

    private static final class ErrorCollector extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private ErrorCollector() {
            super("error-collector", null, null, false);
        }

        @Override
        public void append(final LogEvent event) {
            if (event.getLevel() == Level.ERROR) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
    }
}

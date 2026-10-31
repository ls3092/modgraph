package dev.modgraph.scan;

import dev.modgraph.model.FailureCode;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.parse.FabricMetadataParser;
import dev.modgraph.parse.NeoForgeMetadataParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC §4 关键约束的回归：单个 jar 失败不得中断整批，OK + FAILED 必须恒等于输入数。
 *
 * <p>全部断言基于 src/test/resources/fixtures 下的真实文件，不使用 mock。</p>
 */
class FailurePathTest {

    private static final Path FIXTURE_ROOT = Path.of("src", "test", "resources", "fixtures");

    private static List<ScannedJar> scanned;

    @BeforeAll
    static void scanFixtures() throws IOException {
        scanned = new JarScanner().scan(FIXTURE_ROOT);
    }

    @Test
    void everyJarOnDiskProducesExactlyOneRecord() throws IOException {
        final long onDisk = countJarsOnDisk();
        assertThat(onDisk).isPositive();
        assertThat(scanned).hasSize(Math.toIntExact(onDisk));
        assertThat(scanned.stream().map(jar -> jar.candidate().sourceFile()).distinct().count())
                .isEqualTo(onDisk);
    }

    @Test
    void okPlusFailedEqualsInputCountAndBrokenFileDoesNotAbortBatch() {
        final long ok = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.OK)
                .count();
        final long failed = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.FAILED)
                .count();

        assertThat(scanned).allSatisfy(jar ->
                assertThat(jar.candidate().status()).isIn(Status.OK, Status.FAILED));
        assertThat(ok + failed).isEqualTo(scanned.size());
        assertThat(failed).isPositive();

        // 非 zip 文件排在排序靠前的位置，若整批中断就不会有 OK 记录
        assertThat(ok).isPositive();
        assertThat(fixture("fail-not-a-zip.jar").candidate().failureCode())
                .isEqualTo(FailureCode.NOT_A_ZIP);
        assertThat(fixture("fabric-bom.jar").candidate().status()).isEqualTo(Status.OK);
    }

    @Test
    void failedScannerRecordCarriesFailureDetails() {
        assertThat(scanned)
                .filteredOn(jar -> jar.candidate().status() == Status.FAILED)
                .allSatisfy(jar -> {
                    assertThat(jar.candidate().failureCode()).isNotNull();
                    assertThat(jar.candidate().failureMessage()).isNotBlank();
                    assertThat(jar.candidate().modId()).isNull();
                    assertThat(jar.entryNames()).isEmpty();
                });
    }

    @Test
    void scanFailureIsLoggedExactlyOnceAtErrorLevel() {
        final ErrorCollector collector = new ErrorCollector();
        final Logger logger = (Logger) LogManager.getLogger(JarScanner.class);
        collector.start();
        logger.addAppender(collector);
        try {
            new JarScanner().scan(FIXTURE_ROOT);
        } catch (final IOException e) {
            throw new AssertionError("扫描不应抛出异常", e);
        } finally {
            logger.removeAppender(collector);
        }

        final List<String> errors = collector.messages();
        assertThat(errors).anySatisfy(message -> assertThat(message)
                .contains("fail-not-a-zip.jar")
                .contains("NOT_A_ZIP"));
        assertThat(errors.stream().filter(message -> message.contains("fail-not-a-zip.jar"))
                .count()).isEqualTo(1);
    }

    @Test
    void parserKeepsScanFailureInsteadOfReclassifyingIt() {
        final ScannedJar jar = fixture("fail-not-a-zip.jar");

        assertThat(new FabricMetadataParser().parse(jar).failureCode())
                .isEqualTo(FailureCode.NOT_A_ZIP);
        assertThat(new NeoForgeMetadataParser().parse(jar).failureCode())
                .isEqualTo(FailureCode.NOT_A_ZIP);
        assertThat(new FabricMetadataParser().parse(jar).status()).isEqualTo(Status.FAILED);
    }

    @Test
    void fabricFailureCodesAreTriggeredByRealFixtures() {
        final Map<FailureCode, String> expected = new EnumMap<>(FailureCode.class);
        expected.put(FailureCode.NOT_A_ZIP, "fail-not-a-zip.jar");
        expected.put(FailureCode.NO_METADATA, "fail-no-metadata.jar");
        expected.put(FailureCode.MALFORMED_JSON, "fail-malformed-json.jar");
        expected.put(FailureCode.MISSING_REQUIRED_FIELD, "fail-missing-id.jar");
        expected.put(FailureCode.IO_ERROR, "fail-corrupt-entry.jar");
        expected.put(FailureCode.INTERNAL_ERROR, "fail-internal-error.jar");

        expected.forEach((code, file) -> {
            assertThat(FIXTURE_ROOT.resolve(file)).as(file).isRegularFile();
            final ModCandidate candidate = new FabricMetadataParser().parse(fixture(file));
            assertThat(candidate.status()).as(file).isEqualTo(Status.FAILED);
            assertThat(candidate.failureCode()).as(file).isEqualTo(code);
            assertThat(candidate.failureMessage()).as(file).isNotBlank();
            assertThat(candidate.modId()).as(file).isNull();
        });
    }

    @Test
    void neoforgeFailureCodesAreTriggeredByRealFixtures() {
        final Map<String, FailureCode> expected = Map.of(
                "fail-not-a-zip.jar", FailureCode.NOT_A_ZIP,
                "fail-no-metadata.jar", FailureCode.NO_METADATA,
                "fail-malformed-toml.jar", FailureCode.MALFORMED_TOML,
                "neoforge-invalid-ordering.jar", FailureCode.MALFORMED_TOML,
                "neoforge-invalid-side.jar", FailureCode.MALFORMED_TOML);

        expected.forEach((file, code) -> {
            final ModCandidate candidate = new NeoForgeMetadataParser().parse(fixture(file));
            assertThat(candidate.status()).as(file).isEqualTo(Status.FAILED);
            assertThat(candidate.failureCode()).as(file).isEqualTo(code);
            assertThat(candidate.failureMessage()).as(file).isNotBlank();
        });
    }

    @Test
    void everyFailureCodeHasAtLeastOneRealFixture() {
        final List<FailureCode> triggered = new ArrayList<>();
        for (FailureCode code : FailureCode.values()) {
            final boolean covered = scanned.stream()
                    .map(jar -> new FabricMetadataParser().parse(jar))
                    .filter(candidate -> candidate.status() == Status.FAILED)
                    .anyMatch(candidate -> candidate.failureCode() == code)
                    || scanned.stream()
                    .map(jar -> new NeoForgeMetadataParser().parse(jar))
                    .filter(candidate -> candidate.status() == Status.FAILED)
                    .anyMatch(candidate -> candidate.failureCode() == code);
            if (covered) {
                triggered.add(code);
            }
        }

        assertThat(FailureCode.values()).hasSize(7);
        assertThat(triggered).containsExactlyInAnyOrder(FailureCode.values());
    }

    @Test
    void corruptEntryFixtureIsStillAValidZipWhoseEntryCannotBeRead() throws IOException {
        final Path path = FIXTURE_ROOT.resolve("fail-corrupt-entry.jar");

        try (ZipFile zip = new ZipFile(path.toFile())) {
            final ZipEntry entry = zip.getEntry("fabric.mod.json");
            assertThat(entry).isNotNull();
            assertThatThrownBy(() -> {
                try (InputStream input = zip.getInputStream(entry)) {
                    input.readAllBytes();
                }
            }).isInstanceOf(ZipException.class);
        }

        assertThat(new JarScanner().scan(FIXTURE_ROOT).stream()
                .filter(jar -> jar.jarPath().getFileName().toString().equals("fail-corrupt-entry.jar"))
                .findFirst()
                .orElseThrow()
                .candidate()
                .status()).isEqualTo(Status.OK);
    }

    @Test
    void internalErrorComesFromARealRuntimeExceptionPathNotFromDataClassification() {
        // 这不是数据分类样本：schemaVersion 超出 int 范围，代码未处理该数值，
        // 因此走内部异常路径并如实记为 INTERNAL_ERROR，而不是伪装成 MALFORMED_JSON。
        final ModCandidate candidate =
                new FabricMetadataParser().parse(fixture("fail-internal-error.jar"));

        assertThat(candidate.status()).isEqualTo(Status.FAILED);
        assertThat(candidate.failureCode()).isEqualTo(FailureCode.INTERNAL_ERROR);
        assertThat(candidate.failureMessage()).contains("NumberFormatException");
    }

    @Test
    void corruptEntryFailureIsLoggedOnceAtErrorLevel() {
        final ErrorCollector collector = new ErrorCollector();
        final Logger logger = (Logger) LogManager.getLogger(FabricMetadataParser.class);
        collector.start();
        logger.addAppender(collector);
        try {
            new FabricMetadataParser().parse(fixture("fail-corrupt-entry.jar"));
        } finally {
            logger.removeAppender(collector);
        }

        assertThat(collector.messages()).hasSize(1);
        assertThat(collector.messages().get(0))
                .contains("fail-corrupt-entry.jar")
                .contains("IO_ERROR");
    }

    private static long countJarsOnDisk() throws IOException {
        try (Stream<Path> paths = Files.walk(FIXTURE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .count();
        }
    }

    private static ScannedJar fixture(final String sourceFile) {
        return scanned.stream()
                .filter(jar -> jar.jarPath().getFileName().toString().equals(sourceFile))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少测试 fixture: " + sourceFile));
    }

    private static final class ErrorCollector extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private ErrorCollector() {
            super("failure-path-error-collector", null, null, false);
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

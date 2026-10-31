package dev.modgraph.parse;

import dev.modgraph.model.FailureCode;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import dev.modgraph.testsupport.DatasetRoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NeoForgeMetadataParserTest {

    private static final Path NEOFORGE_ROOT = DatasetRoot.neoforgeRoot();

    private static List<ScannedJar> scanned;
    private static List<ModCandidate> candidates;

    @BeforeAll
    static void parseNeoForgeFixtures() throws IOException {
        DatasetRoot.logMode("NeoForgeMetadataParserTest");
        scanned = new JarScanner().scan(NEOFORGE_ROOT);
        final NeoForgeMetadataParser parser = new NeoForgeMetadataParser();
        candidates = scanned.stream().map(parser::parse).toList();
    }

    @Test
    void okPlusFailedEqualsActualNeoForgeJarCount() throws IOException {
        final long expected;
        try (Stream<Path> paths = Files.walk(NEOFORGE_ROOT)) {
            expected = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .count();
        }

        assertThat(expected).isPositive();
        assertThat(scanned).hasSize(Math.toIntExact(expected));
        assertThat(candidates).hasSize(Math.toIntExact(expected));

        final long ok = count(Status.OK);
        final long failed = count(Status.FAILED);
        assertThat(ok + failed).isEqualTo(expected);
        assertThat(candidates).allSatisfy(candidate ->
                assertThat(candidate.status()).isIn(Status.OK, Status.FAILED));
    }

    @Test
    void everyOkCandidateCarriesNeoForgeMetadata() {
        assertThat(candidates)
                .filteredOn(candidate -> candidate.status() == Status.OK)
                .isNotEmpty()
                .allSatisfy(candidate -> {
                    assertThat(candidate.loaderFamily())
                            .isIn(LoaderFamily.NEOFORGE, LoaderFamily.FORGE);
                    assertThat(candidate.modId()).isNotBlank();
                    assertThat(candidate.version()).isNotBlank();
                    assertThat(candidate.failureCode()).isNull();
                    assertThat(candidate.failureMessage()).isNull();
                });
    }

    @Test
    void everyFailedCandidateCarriesFailureDetails() {
        assertThat(candidates)
                .filteredOn(candidate -> candidate.status() == Status.FAILED)
                .isNotEmpty()
                .allSatisfy(candidate -> {
                    assertThat(candidate.failureCode()).isNotNull();
                    assertThat(candidate.failureMessage()).isNotBlank();
                    assertThat(candidate.modId()).isNull();
                    if (candidate.failureCode() == FailureCode.NO_METADATA) {
                        assertThat(candidate.failureMessage()).contains("嵌套");
                    }
                });
    }

    @Test
    void versionPlaceholdersAreKeptVerbatim() {
        final List<ModCandidate> placeholders = candidates.stream()
                .filter(candidate -> "${file.jarVersion}".equals(candidate.version()))
                .toList();

        if (DatasetRoot.hasFullDataset()) {
            assertThat(placeholders).isNotEmpty();
        } else {
            // 内置样本里没有声明占位符版本的 mod，断言它确实不存在，
            // 避免用“空集合断言”假装这条规则被验证过。
            assertThat(placeholders).isEmpty();
        }
        assertThat(placeholders).allSatisfy(candidate ->
                assertThat(candidate.version()).isEqualTo("${file.jarVersion}"));
    }

    @Test
    void mixinConfigsAreReadOnlyAsPaths() {
        final List<String> configs = candidates.stream()
                .flatMap(candidate -> candidate.mixinConfigs().stream())
                .toList();

        if (DatasetRoot.hasFullDataset()) {
            assertThat(configs).isNotEmpty();
        } else {
            assertThat(configs).isEmpty();
        }
        assertThat(configs).allSatisfy(path -> assertThat(path).endsWith(".json"));
    }

    @Test
    void mixinTargetsRemainEmptyAndNotInferred() {
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.mixinTargets()).isEmpty();
            assertThat(candidate.mixinTargetsInferred()).isFalse();
        });
    }

    private static long count(final Status status) {
        return candidates.stream()
                .filter(candidate -> candidate.status() == status)
                .count();
    }
}

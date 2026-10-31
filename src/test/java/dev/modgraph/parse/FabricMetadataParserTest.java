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

class FabricMetadataParserTest {

    private static final Path FABRIC_ROOT = DatasetRoot.fabricRoot();

    private static List<ScannedJar> scanned;
    private static List<ModCandidate> candidates;

    @BeforeAll
    static void parseFabricFixtures() throws IOException {
        DatasetRoot.logMode("FabricMetadataParserTest");
        scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser parser = new FabricMetadataParser();
        candidates = scanned.stream().map(parser::parse).toList();
    }

    @Test
    void okPlusFailedEqualsActualFabricJarCount() throws IOException {
        final long expected;
        try (Stream<Path> paths = Files.walk(FABRIC_ROOT)) {
            expected = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .count();
        }

        assertThat(expected).isPositive();
        assertThat(scanned).hasSize(Math.toIntExact(expected));
        assertThat(candidates).hasSize(Math.toIntExact(expected));

        final long ok = candidates.stream()
                .filter(candidate -> candidate.status() == Status.OK)
                .count();
        final long failed = candidates.stream()
                .filter(candidate -> candidate.status() == Status.FAILED)
                .count();

        assertThat(ok + failed).isEqualTo(expected);
        assertThat(candidates).allSatisfy(candidate ->
                assertThat(candidate.status()).isIn(Status.OK, Status.FAILED));
    }

    @Test
    void everyOkCandidateCarriesFabricMetadata() {
        assertThat(candidates)
                .filteredOn(candidate -> candidate.status() == Status.OK)
                .isNotEmpty()
                .allSatisfy(candidate -> {
                    assertThat(candidate.loaderFamily())
                            .isIn(LoaderFamily.FABRIC, LoaderFamily.QUILT);
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
                .allSatisfy(candidate -> {
                    assertThat(candidate.failureCode()).isNotNull();
                    assertThat(candidate.failureMessage()).isNotBlank();
                    assertThat(candidate.modId()).isNull();
                });
    }

    @Test
    void noMetadataOnNestedJarMentionsJarInJar() throws IOException {
        final Path neoforgeRoot = DatasetRoot.neoforgeRoot();
        final List<ScannedJar> neoforgeJars = new JarScanner().scan(neoforgeRoot);
        // 完整数据集优先挑真正带内层 jar 的样本；内置样本没有嵌套 jar，
        // 退而用任意“无 fabric.mod.json 且有条目”的样本，验证 NO_METADATA 提示里仍带嵌套说明。
        final ScannedJar nested = neoforgeJars.stream()
                .filter(jar -> !jar.entryNames().contains("fabric.mod.json"))
                .filter(jar -> jar.entryNames().stream()
                        .anyMatch(name -> name.startsWith("META-INF/jars/")
                                || name.startsWith("META-INF/jarjar/")
                                || name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")))
                .findFirst()
                .orElseGet(() -> neoforgeJars.stream()
                        .filter(jar -> !jar.entryNames().contains("fabric.mod.json"))
                        .filter(jar -> !jar.entryNames().isEmpty())
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "没有可用于验证 NO_METADATA 的样本")));

        final ModCandidate candidate = new FabricMetadataParser().parse(nested);

        assertThat(candidate.status()).isEqualTo(Status.FAILED);
        assertThat(candidate.failureCode()).isEqualTo(FailureCode.NO_METADATA);
        assertThat(candidate.failureMessage()).contains("嵌套");
    }

    @Test
    void mixinTargetsRemainEmptyAndNotInferred() {
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.mixinTargets()).isEmpty();
            assertThat(candidate.mixinTargetsInferred()).isFalse();
        });
    }
}

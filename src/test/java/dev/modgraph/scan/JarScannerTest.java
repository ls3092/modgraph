package dev.modgraph.scan;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.Status;
import dev.modgraph.testsupport.DatasetRoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class JarScannerTest {

    private static final Path FIXTURES = DatasetRoot.root();
    private static List<ScannedJar> scanned;

    @BeforeAll
    static void scanFixtures() throws IOException {
        DatasetRoot.logMode("JarScannerTest");
        scanned = new JarScanner().scan(FIXTURES);
    }

    @Test
    void scanProducesOneCandidatePerJarFileOnDisk() throws IOException {
        final long expected;
        try (Stream<Path> paths = Files.walk(FIXTURES)) {
            expected = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .count();
        }
        final long ok = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.OK)
                .count();
        final long failed = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.FAILED)
                .count();

        assertThat(expected).isPositive();
        assertThat(scanned).hasSize(Math.toIntExact(expected));
        assertThat(scanned).allSatisfy(jar ->
                assertThat(jar.candidate().status()).isIn(Status.OK, Status.FAILED));
        assertThat(ok + failed).isEqualTo(expected);
    }

    @Test
    void everyCandidateCarriesRequiredFieldsAndZipEntryNames() {
        assertThat(scanned).allSatisfy(jar -> {
            assertThat(jar.jarPath()).isRegularFile();
            assertThat(jar.candidate().sourceFile()).isEqualTo(jar.jarPath().getFileName().toString());
            assertThat(jar.candidate().sha256()).isNotNull();
            assertThat(jar.candidate().loaderFamily()).isEqualTo(LoaderFamily.UNKNOWN);
            if (jar.candidate().status() == Status.OK) {
                assertThat(jar.candidate().sha256()).matches("[0-9a-f]{64}");
                assertThat(jar.candidate().failureCode()).isNull();
                assertThat(jar.entryNames()).isNotEmpty();
            } else {
                assertThat(jar.candidate().status()).isEqualTo(Status.FAILED);
                assertThat(jar.candidate().failureCode()).isNotNull();
                assertThat(jar.candidate().failureMessage()).isNotBlank();
            }
        });
    }

    @Test
    void parsingFieldsAreStillEmpty() {
        final List<ScannedJar> okJars = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.OK)
                .toList();

        assertThat(okJars).isNotEmpty();
        assertThat(okJars).allSatisfy(jar -> {
            assertThat(jar.candidate().modId()).isNull();
            assertThat(jar.candidate().version()).isNull();
            assertThat(jar.candidate().name()).isNull();
            assertThat(jar.candidate().side()).isNull();
            assertThat(jar.candidate().depends()).isEmpty();
            assertThat(jar.candidate().recommends()).isEmpty();
            assertThat(jar.candidate().breaks()).isEmpty();
            assertThat(jar.candidate().provides()).isEmpty();
            assertThat(jar.candidate().entrypoints()).isEmpty();
            assertThat(jar.candidate().mixinConfigs()).isEmpty();
            assertThat(jar.candidate().mixinTargets()).isEmpty();
            assertThat(jar.candidate().mixinTargetsInferred()).isFalse();
            assertThat(jar.candidate().nestedJars()).isEmpty();
            assertThat(jar.candidate().modType()).isNull();
            assertThat(jar.candidate().invasiveness()).isNull();
            assertThat(jar.candidate().invasivenessSignals()).isEmpty();
            assertThat(jar.candidate().supportPolicy()).isNull();
            assertThat(jar.candidate().failureCode()).isNull();
            assertThat(jar.candidate().failureMessage()).isNull();
            assertThat(jar.candidate().unknownFields()).isEmpty();
        });
    }

    @Test
    void entryNamesMatchZipCentralDirectory() throws IOException {
        final ScannedJar sample = scanned.stream()
                .filter(jar -> jar.candidate().status() == Status.OK)
                .findFirst()
                .orElseThrow();
        try (ZipFile zip = new ZipFile(sample.jarPath().toFile())) {
            final List<String> expected = zip.stream().map(ZipEntry::getName).toList();
            assertThat(sample.entryNames()).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}

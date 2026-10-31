package dev.modgraph.parse;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataBomTest {

    private static final Path FIXTURE_ROOT = Path.of("src", "test", "resources", "fixtures");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static List<ScannedJar> scanned;

    @BeforeAll
    static void scanFixtureJars() throws IOException {
        scanned = new JarScanner().scan(FIXTURE_ROOT);
    }

    @Test
    void bothFixturesStartWithRealUtf8BomBytes() throws IOException {
        assertStartsWithBom("fabric-bom.jar", "fabric.mod.json");
        assertStartsWithBom("neoforge-bom.jar", "META-INF/neoforge.mods.toml");
    }

    @Test
    void fabricJsonWithBomParsesSuccessfully() {
        final ModCandidate candidate = new FabricMetadataParser().parse(fixture("fabric-bom.jar"));

        assertThat(candidate.status()).isEqualTo(Status.OK);
        assertThat(candidate.loaderFamily()).isEqualTo(LoaderFamily.FABRIC);
        assertThat(candidate.modId()).isEqualTo("bom_fabric_sample");
        assertThat(candidate.version()).isEqualTo("1.0.0");
        assertThat(candidate.failureCode()).isNull();
    }

    @Test
    void neoforgeTomlWithBomParsesSuccessfully() {
        final ModCandidate candidate = new NeoForgeMetadataParser().parse(fixture("neoforge-bom.jar"));

        assertThat(candidate.status()).isEqualTo(Status.OK);
        assertThat(candidate.loaderFamily()).isEqualTo(LoaderFamily.NEOFORGE);
        assertThat(candidate.modId()).isEqualTo("bom_neoforge_sample");
        assertThat(candidate.version()).isEqualTo("1.0.0");
        assertThat(candidate.failureCode()).isNull();
    }

    private static ScannedJar fixture(final String sourceFile) {
        return scanned.stream()
                .filter(candidate -> candidate.jarPath().getFileName().toString().equals(sourceFile))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少测试 fixture: " + sourceFile));
    }

    private static void assertStartsWithBom(final String jarName,
                                            final String entryName) throws IOException {
        final Path jarPath = FIXTURE_ROOT.resolve(jarName);
        assertThat(jarPath).isRegularFile();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            final ZipEntry entry = zip.getEntry(entryName);
            assertThat(entry).as("fixture %s 缺少条目 %s", jarName, entryName).isNotNull();

            try (InputStream input = zip.getInputStream(entry)) {
                assertThat(input.readNBytes(UTF8_BOM.length)).containsExactly(UTF8_BOM);
            }
        }
    }
}

package dev.modgraph.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC §6.7：{@code duplicates.csv} 的格式与可复现性。
 */
class DuplicatesCsvTest {

    @Test
    void headerOnlyWhenThereIsNoDuplicateGroup(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("duplicates.csv");
        new ReportWriter().writeDuplicates(file, DuplicateIdReport.of(List.of(
                ReportFixtures.ok("only", "1.0", LoaderFamily.FABRIC))));

        assertThat(Files.readAllLines(file))
                .containsExactly("universe,modId,recordCount,pairCount,relation,versions");
    }

    @Test
    void oneRowPerGroupWithAllSixColumns(@TempDir final Path tempDir) throws IOException {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("continuity", "3.0.0+1.21", LoaderFamily.FABRIC),
                ReportFixtures.ok("continuity", "3.0.0+1.21.neoforge", LoaderFamily.FABRIC),
                ReportFixtures.ok("puzzle", "2.3.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("puzzle", "2.3.0", LoaderFamily.FABRIC)));

        final Path file = tempDir.resolve("duplicates.csv");
        new ReportWriter().writeDuplicates(file, report);

        assertThat(Files.readAllLines(file)).containsExactly(
                "universe,modId,recordCount,pairCount,relation,versions",
                "FABRIC,continuity,2,1,DIFFERENT_VERSION,3.0.0+1.21;3.0.0+1.21.neoforge",
                "FABRIC,puzzle,2,1,SAME_VERSION,2.3.0");
        for (String line : Files.readAllLines(file)) {
            assertThat(line.split(",", -1)).hasSize(6);
        }
    }

    @Test
    void rowsHaveNoArbitrationColumn(@TempDir final Path tempDir) throws IOException {
        final ReportWriter writer = new ReportWriter();
        final Path file = tempDir.resolve("duplicates.csv");
        writer.writeDuplicates(file, DuplicateIdReport.of(List.of(
                ReportFixtures.ok("mod", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("mod", "2.0", LoaderFamily.FABRIC))));

        final String header = Files.readAllLines(file).get(0);
        assertThat(header).isEqualTo("universe,modId,recordCount,pairCount,relation,versions");
        assertThat(header).doesNotContain("keep", "recommend", "chosen", "preferred");
    }

    @Test
    void twoConsecutiveRunsProduceAByteIdenticalFile(@TempDir final Path tempDir)
            throws IOException {
        final List<ModCandidate> records = List.of(
                ReportFixtures.ok("zeta", "2.0", LoaderFamily.NEOFORGE),
                ReportFixtures.ok("zeta", "1.0", LoaderFamily.NEOFORGE),
                ReportFixtures.ok("alpha", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("alpha", "1.0", LoaderFamily.FABRIC));

        final Path first = tempDir.resolve("first.csv");
        final Path second = tempDir.resolve("second.csv");
        final ReportWriter writer = new ReportWriter();
        writer.writeDuplicates(first, DuplicateIdReport.of(records));
        writer.writeDuplicates(second, DuplicateIdReport.of(records));

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }
}

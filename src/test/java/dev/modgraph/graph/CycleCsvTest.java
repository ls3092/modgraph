package dev.modgraph.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.report.ReportWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cycles.csv} 的格式与可复现性（SPEC §3.6.6、§3.6.7 第 7、8 条）。
 */
class CycleCsvTest {

    private static CycleReport detect(final List<ModCandidate> records) {
        return new CycleDetector().detect(new GraphBuilder().build(records));
    }

    @Test
    void headerOnlyWhenThereAreNoCycleGroups(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("cycles.csv");
        new ReportWriter().writeCycles(file, List.of());

        assertThat(Files.readAllLines(file))
                .containsExactly("universe,memberCount,members");
    }

    @Test
    void oneRowPerCycleGroupWithSortedSemicolonJoinedMembers(@TempDir final Path tempDir)
            throws IOException {
        final CycleReport report = detect(List.of(
                GraphFixtures.ok("gamma", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC, "gamma")));

        final Path file = tempDir.resolve("cycles.csv");
        new ReportWriter().writeCycles(file, report.cycleGroups());

        assertThat(Files.readAllLines(file)).containsExactly(
                "universe,memberCount,members",
                "FABRIC,3,alpha;beta;gamma");
    }

    @Test
    void rowsAreSortedByUniverseThenMembers(@TempDir final Path tempDir) throws IOException {
        final CycleReport report = detect(List.of(
                GraphFixtures.ok("zeta", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "zeta"),
                GraphFixtures.ok("zz", LoaderFamily.NEOFORGE, "aa"),
                GraphFixtures.ok("aa", LoaderFamily.NEOFORGE, "zz"),
                GraphFixtures.ok("gamma", LoaderFamily.NEOFORGE, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.NEOFORGE, "gamma")));

        final Path file = tempDir.resolve("cycles.csv");
        new ReportWriter().writeCycles(file, report.cycleGroups());

        assertThat(Files.readAllLines(file)).containsExactly(
                "universe,memberCount,members",
                "FABRIC,2,alpha;zeta",
                "NEOFORGE,2,aa;zz",
                "NEOFORGE,2,beta;gamma");
    }

    @Test
    void everyRowHasExactlyThreeColumns(@TempDir final Path tempDir) throws IOException {
        final CycleReport report = detect(List.of(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("beta", LoaderFamily.NEOFORGE, "gamma"),
                GraphFixtures.ok("gamma", LoaderFamily.NEOFORGE, "beta")));

        final Path file = tempDir.resolve("cycles.csv");
        new ReportWriter().writeCycles(file, report.cycleGroups());

        final List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(report.cycleGroupCount() + 1);
        for (String line : lines) {
            assertThat(line.split(",", -1)).hasSize(3);
        }
        for (String line : lines.subList(1, lines.size())) {
            final String[] columns = line.split(",", -1);
            final int memberCount = columns[2].split(";", -1).length;
            assertThat(Integer.parseInt(columns[1])).isEqualTo(memberCount);
        }
    }

    @Test
    void twoConsecutiveRunsProduceAByteIdenticalFile(@TempDir final Path tempDir)
            throws IOException {
        final List<ModCandidate> records = List.of(
                GraphFixtures.ok("zulu", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "mike"),
                GraphFixtures.ok("mike", LoaderFamily.FABRIC, "zulu"),
                GraphFixtures.ok("beta", LoaderFamily.NEOFORGE, "gamma"),
                GraphFixtures.ok("gamma", LoaderFamily.NEOFORGE, "beta"));

        final Path first = tempDir.resolve("first.csv");
        final Path second = tempDir.resolve("second.csv");
        final ReportWriter writer = new ReportWriter();
        writer.writeCycles(first, detect(records).cycleGroups());
        writer.writeCycles(second, detect(records).cycleGroups());

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));

        writer.writeCycles(second, detect(records).cycleGroups());
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }
}

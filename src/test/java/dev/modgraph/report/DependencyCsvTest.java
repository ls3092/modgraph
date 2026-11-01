package dev.modgraph.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyCsvTest {

    @Test
    void headerAndRowsFollowTheSpec(@TempDir final Path tempDir) throws IOException {
        final DependencyResult versioned = new DependencyResult("sodium", "0.8.13+mc1.21.1",
                "minecraft", DepKind.REQUIRED, "1.21;1.21.1", "1.21.1",
                DependencyOutcome.SATISFIED);
        final DependencyResult missing = new DependencyResult("iris", "1.8.14",
                "absent_mod", DepKind.RECOMMENDS, "*", null, DependencyOutcome.MISSING_ID);
        final DependencyResult mavenRange = new DependencyResult("jei", "19.70.0.467",
                "minecraft", DepKind.REQUIRED, "[1.21,1.22)", "1.21.1",
                DependencyOutcome.SATISFIED);
        final DependencyResult ambiguous = new DependencyResult("iris", "1.8.14",
                "fabric", DepKind.REQUIRED, "*", null, DependencyOutcome.AMBIGUOUS_ALIAS);

        final Path path = tempDir.resolve("dependencies.csv");
        new ReportWriter().writeDependencies(path,
                List.of(versioned, missing, mavenRange, ambiguous));

        final List<String> lines = Files.readAllLines(path);
        assertThat(lines).hasSize(5);
        assertThat(lines.get(0)).isEqualTo(
                "modId,version,depId,depKind,declaredRange,resolvedVersion,outcome");
        assertThat(lines.get(1)).isEqualTo(
                "sodium,0.8.13+mc1.21.1,minecraft,required,1.21;1.21.1,1.21.1,SATISFIED");
        // 缺失版本写成空单元格；outcome 仍然是 MISSING_ID，不会被折叠成 SATISFIED。
        assertThat(lines.get(2)).isEqualTo(
                "iris,1.8.14,absent_mod,recommends,*,,MISSING_ID");
        // Maven 区间含逗号，必须按 CSV 规则加引号。
        assertThat(lines.get(3)).isEqualTo(
                "jei,19.70.0.467,minecraft,required,\"[1.21,1.22)\",1.21.1,SATISFIED");
        // 歧义行没有选中的版本，写空单元格，但 outcome 单列为 AMBIGUOUS_ALIAS。
        assertThat(lines.get(4)).isEqualTo(
                "iris,1.8.14,fabric,required,*,,AMBIGUOUS_ALIAS");
    }

    @Test
    void emptyReportStillWritesTheHeader(@TempDir final Path tempDir) throws IOException {
        final Path path = tempDir.resolve("dependencies-empty.csv");
        new ReportWriter().writeDependencies(path, List.of());

        final List<String> lines = Files.readAllLines(path);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst())
                .isEqualTo("modId,version,depId,depKind,declaredRange,resolvedVersion,outcome");
    }
}

package dev.modgraph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.report.ReportSummary;
import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyReport;
import dev.modgraph.resolve.DependencyResult;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.7.4：三行 stdout 的标签、顺序与括号里的理由固定，数字随数据变化。
 */
class MainReportLogTest {

    @Test
    void threeLinesUseTheFixedLabelsAndOrder() {
        final List<ModCandidate> candidates = List.of(
                candidate("mod", "1.0", LoaderFamily.FABRIC),
                candidate("mod", "2.0", LoaderFamily.FABRIC));
        final DependencyReport dependencies = new DependencyReport(List.of(
                new DependencyResult("mod", "1.0", "target", DepKind.REQUIRED, "*", null,
                        DependencyOutcome.MISSING_ID)));

        final List<String> lines = captureInfo(
                ReportSummary.of(candidates, dependencies));

        assertThat(lines).containsExactly(
                "不满足的硬依赖：1（硬 MISSING_ID 1 + 硬 VERSION_MISMATCH 0）",
                "未计入 unsatisfiedRequired：AMBIGUOUS_ALIAS 0（工具无法判定，非模组缺陷）"
                        + "| BREAKS_VIOLATED 0（冲突命中，非依赖不满足）"
                        + "| 软 MISSING_ID 0 | 软 VERSION_MISMATCH 0 | UNPARSEABLE 0（实现缺陷）",
                "重复 modId：1 组 / 1 对 | 版本相同 0 对 | 版本不同 1 对");
    }

    @Test
    void zeroValuesAreStillPrinted() {
        final List<String> lines = captureInfo(ReportSummary.of(
                List.of(candidate("only", "1.0", LoaderFamily.FABRIC)),
                new DependencyReport(List.of())));

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("不满足的硬依赖：0（硬 MISSING_ID 0 + 硬 VERSION_MISMATCH 0）");
        assertThat(lines.get(2)).isEqualTo("重复 modId：0 组 / 0 对 | 版本相同 0 对 | 版本不同 0 对");
    }

    private static List<String> captureInfo(final ReportSummary summary) {
        final InfoCollector collector = new InfoCollector();
        collector.start();
        final Logger mainLogger = (Logger) LogManager.getLogger(Main.class);
        mainLogger.addAppender(collector);
        try {
            Main.logReportSummary(summary);
        } finally {
            mainLogger.removeAppender(collector);
            collector.stop();
        }
        return collector.messages();
    }

    private static ModCandidate candidate(final String modId,
                                          final String version,
                                          final LoaderFamily family) {
        return new ModCandidate(
                modId + "-" + version + ".jar",
                "0".repeat(64),
                family,
                Status.OK,
                modId,
                version,
                modId,
                dev.modgraph.model.Side.BOTH,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                dev.modgraph.model.ModType.NORMAL,
                dev.modgraph.model.Invasiveness.API,
                List.of(),
                dev.modgraph.model.SupportPolicy.SUPPORTED,
                null,
                null,
                List.of());
    }

    private static final class InfoCollector extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private InfoCollector() {
            super("report-info-collector", null, null, false);
        }

        @Override
        public void append(final LogEvent event) {
            if (event.getLevel() == Level.INFO) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
    }
}

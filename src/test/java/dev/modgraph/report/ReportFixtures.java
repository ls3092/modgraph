package dev.modgraph.report;

import dev.modgraph.model.FailureCode;
import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.ModType;
import dev.modgraph.model.Side;
import dev.modgraph.model.Status;
import dev.modgraph.model.SupportPolicy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** M1.5 测试用的构造记录：只关心 modId / version / 加载器家族 / 状态，不含依赖声明。 */
final class ReportFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ReportFixtures() {
    }

    static ModCandidate ok(final String modId, final String version, final LoaderFamily family) {
        return candidate(modId, version, family, Status.OK);
    }

    static ModCandidate failed(final String modId, final String version, final LoaderFamily family) {
        return candidate(modId, version, family, Status.FAILED);
    }

    private static ModCandidate candidate(final String modId,
                                          final String version,
                                          final LoaderFamily family,
                                          final Status status) {
        final int index = SEQUENCE.incrementAndGet();
        return new ModCandidate(
                modId + "-" + index + ".jar",
                "0".repeat(64),
                family,
                status,
                modId,
                version,
                modId,
                Side.BOTH,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                ModType.NORMAL,
                status == Status.OK ? Invasiveness.API : null,
                List.of(),
                status == Status.OK ? SupportPolicy.SUPPORTED : null,
                status == Status.OK ? null : FailureCode.NO_METADATA,
                status == Status.OK ? null : "构造的失败记录",
                List.of());
    }
}

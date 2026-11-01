package dev.modgraph.graph;

import dev.modgraph.model.Constraint;
import dev.modgraph.model.FailureCode;
import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.ModType;
import dev.modgraph.model.Ordering;
import dev.modgraph.model.Side;
import dev.modgraph.model.Status;
import dev.modgraph.model.SupportPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M1 测试用的构造记录（不是 mock，也不是真实 jar）。
 *
 * <p>和真实解析器保持一致：{@code depends}、{@code recommends}、{@code breaks} 三种约束的
 * {@code required} 都是 {@code true}。这一点很重要——如果被测代码拿 {@code required} 当判据，
 * {@code breaks} 就会被误建成边，测试必须能抓住它。</p>
 */
final class GraphFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private GraphFixtures() {
    }

    static ModCandidate ok(final String modId, final LoaderFamily family, final String... depends) {
        return candidate(modId, family, Status.OK, List.of(depends), List.of(), List.of(), List.of());
    }

    static ModCandidate ok(final String modId,
                           final LoaderFamily family,
                           final List<String> depends,
                           final List<String> recommends,
                           final List<String> breaks,
                           final List<String> provides) {
        return candidate(modId, family, Status.OK, depends, recommends, breaks, provides);
    }

    static ModCandidate failed(final String sourceFile) {
        return new ModCandidate(
                sourceFile,
                "0".repeat(64),
                LoaderFamily.UNKNOWN,
                Status.FAILED,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                FailureCode.NO_METADATA,
                "构造的失败记录",
                List.of());
    }

    private static ModCandidate candidate(final String modId,
                                          final LoaderFamily family,
                                          final Status status,
                                          final List<String> depends,
                                          final List<String> recommends,
                                          final List<String> breaks,
                                          final List<String> provides) {
        final int index = SEQUENCE.incrementAndGet();
        return new ModCandidate(
                modId + "-" + index + ".jar",
                "0".repeat(64),
                family,
                status,
                modId,
                "1.0",
                modId,
                Side.BOTH,
                constraints(depends),
                constraints(recommends),
                constraints(breaks),
                provides,
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                ModType.NORMAL,
                Invasiveness.API,
                List.of(),
                SupportPolicy.SUPPORTED,
                null,
                null,
                List.of());
    }

    private static List<Constraint> constraints(final List<String> targets) {
        final List<Constraint> constraints = new ArrayList<>(targets.size());
        for (String target : targets) {
            constraints.add(new Constraint(target, List.of("*"), true, Ordering.NONE, Side.UNKNOWN));
        }
        return List.copyOf(constraints);
    }
}

package dev.modgraph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.graph.CycleGroup;
import dev.modgraph.graph.LoaderUniverse;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.6.6 的 ERROR 行：每个环组恰好一条，格式 {@code 依赖环 {}：{}}；
 * 环组为 0 时一条都不打（§3.6.7 第 10 条）。
 */
class MainCycleLogTest {

    @Test
    void noCycleGroupsEmitNoError() {
        final List<String> errors = captureErrors(List.of());
        assertThat(errors).isEmpty();
    }

    @Test
    void eachCycleGroupEmitsExactlyOneErrorInTheFixedFormat() {
        final List<String> errors = captureErrors(List.of(
                new CycleGroup(LoaderUniverse.FABRIC, List.of("beta", "alpha")),
                new CycleGroup(LoaderUniverse.NEOFORGE, List.of("gamma"))));

        assertThat(errors).containsExactly(
                "依赖环 FABRIC：alpha | beta",
                "依赖环 NEOFORGE：gamma");
    }

    private static List<String> captureErrors(final List<CycleGroup> groups) {
        final ErrorCollector collector = new ErrorCollector();
        collector.start();
        final Logger mainLogger = (Logger) LogManager.getLogger(Main.class);
        mainLogger.addAppender(collector);
        try {
            Main.logCycleGroups(groups);
        } finally {
            mainLogger.removeAppender(collector);
            collector.stop();
        }
        return collector.messages();
    }

    private static final class ErrorCollector extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private ErrorCollector() {
            super("cycle-error-collector", null, null, false);
        }

        @Override
        public void append(final LogEvent event) {
            if (event.getLevel() == Level.ERROR) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
    }
}

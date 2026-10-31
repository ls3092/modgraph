package dev.modgraph.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试数据集根目录选择。
 *
 * <p>优先使用 {@code fixtures/jars} 下的完整数据集；该目录不存在、或其中没有任何 .jar 时，
 * 回退到提交进仓库的 {@code src/test/resources/fixtures}（内置样本）。
 * 两种模式都会在日志里明确说明，测试不会因为缺数据而跳过。</p>
 */
public final class DatasetRoot {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetRoot.class);
    private static final Path FULL = Path.of("fixtures", "jars");
    private static final Path BUNDLED = Path.of("src", "test", "resources", "fixtures");

    private static Boolean fullDataset;

    private DatasetRoot() {
    }

    /** 完整数据集是否存在且至少含一个 jar。 */
    public static synchronized boolean hasFullDataset() {
        if (fullDataset == null) {
            fullDataset = countJars(FULL) > 0;
        }
        return fullDataset;
    }

    /** 扫描根目录：完整数据集优先，否则内置样本。 */
    public static Path root() {
        return hasFullDataset() ? FULL : BUNDLED;
    }

    /** Fabric 侧扫描根目录；内置样本没有加载器子目录，直接返回扁平目录。 */
    public static Path fabricRoot() {
        return hasFullDataset() ? FULL.resolve("fabric") : BUNDLED;
    }

    /** NeoForge 侧扫描根目录；内置样本没有加载器子目录，直接返回扁平目录。 */
    public static Path neoforgeRoot() {
        return hasFullDataset() ? FULL.resolve("neoforge") : BUNDLED;
    }

    /** 真实列举目录下的 .jar 数量；目录不存在时返回 0。 */
    public static long countJars(final Path root) {
        if (!Files.isDirectory(root)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .count();
        } catch (IOException e) {
            throw new IllegalStateException("无法列举数据集目录：" + root, e);
        }
    }

    /** 打一条 INFO，说明本次测试用的是完整数据集还是内置样本。 */
    public static void logMode(final String testClass) {
        final boolean full = hasFullDataset();
        LOG.info("{} 当前使用{}：{}（{} 个 jar）",
                testClass,
                full ? "完整数据集" : "内置样本（未找到 fixtures/jars 或其下没有 jar）",
                root().toAbsolutePath(),
                countJars(root()));
    }
}

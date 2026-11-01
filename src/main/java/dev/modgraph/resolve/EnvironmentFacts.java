package dev.modgraph.resolve;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 运行环境事实（SPEC §3.5.2）：加载器与平台 ID 不是数据集里的模组，必须按这里声明的版本解析，
 * 永远不能报 {@code MISSING_ID}。
 *
 * <p>声明依据（全部可审计，业务代码不引用这些常量）：</p>
 * <ul>
 *   <li>{@code minecraft=1.21.1}：整个数据集的目标游戏版本。</li>
 *   <li>{@code fabricloader=0.20.1}：数据集内所有 {@code fabricloader} 依赖要求的下界最大值。</li>
 *   <li>{@code neoforge=21.1.262}：数据集内所有 {@code neoforge} 依赖要求的下界最大值。</li>
 *   <li>{@code java=21}：SPEC §8 规定的工具链版本。</li>
 *   <li>{@code quilt_loader=0.27.0}、{@code forge=52.0.0}：当前数据集没有引用它们，
 *       只为满足“§3.5.2 的 ID 绝不能被判 MISSING_ID”而声明；它们是声明值，不是观测事实。</li>
 * </ul>
 *
 * <p>这些值在启动时以 INFO 打印一行，任何数字都可以据此复现。</p>
 */
public final class EnvironmentFacts {

    private static final String MINECRAFT = "minecraft";
    private static final String FABRIC_LOADER = "fabricloader";
    private static final String QUILT_LOADER = "quilt_loader";
    private static final String NEOFORGE = "neoforge";
    private static final String FORGE = "forge";
    private static final String JAVA = "java";

    private static final Map<String, String> DECLARED;
    private static final Map<String, String> ALIASES;

    static {
        final Map<String, String> declared = new LinkedHashMap<>();
        declared.put(MINECRAFT, "1.21.1");
        declared.put(FABRIC_LOADER, "0.20.1");
        declared.put(QUILT_LOADER, "0.27.0");
        declared.put(NEOFORGE, "21.1.262");
        declared.put(FORGE, "52.0.0");
        declared.put(JAVA, "21");
        DECLARED = Collections.unmodifiableMap(declared);

        final Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("minecraft_version", MINECRAFT);
        aliases.put("fabric-loader", FABRIC_LOADER);
        aliases.put("quilt-loader", QUILT_LOADER);
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private static final EnvironmentFacts DECLARED_FACTS = new EnvironmentFacts();

    /** 本次运行声明的环境事实。 */
    public static EnvironmentFacts declared() {
        return DECLARED_FACTS;
    }

    /** 该 ID（含别名）是否是环境提供的；是则返回声明的版本。 */
    public Optional<String> versionOf(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        final String normalized = id.trim().toLowerCase(Locale.ROOT);
        final String canonical = ALIASES.getOrDefault(normalized, normalized);
        return Optional.ofNullable(DECLARED.get(canonical));
    }

    /** 声明的全部环境事实，按固定顺序。 */
    public Map<String, String> all() {
        return DECLARED;
    }

    /** 启动时打印的一行文本，形如 {@code minecraft=1.21.1 | fabricloader=0.20.1 | ...}。 */
    public String describe() {
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : DECLARED.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}

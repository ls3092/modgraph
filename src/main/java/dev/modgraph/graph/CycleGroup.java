package dev.modgraph.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 一个环组（SPEC §3.6.4）：写成成员集合，永不写成路径。
 *
 * <p>{@code members} 创建时按 ASCII 升序排好，因此第一个成员永远是字典序最小的那个——
 * 既没有起点，也没有方向需要规范化。一行只声明“每个成员都在某个环上”，
 * 不声明“存在一个包含全部成员的环”。</p>
 */
public record CycleGroup(LoaderUniverse universe, List<String> members) {

    public CycleGroup {
        Objects.requireNonNull(universe, "universe");
        Objects.requireNonNull(members, "members");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("环组的成员集合不能为空");
        }
        final List<String> sorted = new ArrayList<>(members);
        // modId 是 ASCII 字符串时，自然序与 ASCII 码点升序完全一致；
        // 排序只为了两次运行逐字节可比，不表达任何安装或加载次序（§3.6.5）。
        sorted.sort(Comparator.naturalOrder());
        members = List.copyOf(sorted);
    }

    public int memberCount() {
        return members.size();
    }

    /** {@code cycles.csv} 里的写法：成员用 {@code ;} 连接。 */
    public String csvMembers() {
        return String.join(";", members);
    }

    /** ERROR 日志里的写法：成员用 {@code  | } 连接。 */
    public String logMembers() {
        return String.join(" | ", members);
    }
}

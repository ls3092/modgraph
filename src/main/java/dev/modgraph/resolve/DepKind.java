package dev.modgraph.resolve;

/** 依赖声明种类，对应 SPEC §3.5.6 的 {@code depKind} 列。 */
public enum DepKind {

    REQUIRED("required"),
    RECOMMENDS("recommends"),
    BREAKS("breaks");

    private final String csvName;

    DepKind(final String csvName) {
        this.csvName = csvName;
    }

    /** CSV 中的取值，小写。 */
    public String csvName() {
        return csvName;
    }
}

package dev.modgraph.version;

/** 已解析的版本区间，只回答“某个版本是否在区间内”。 */
public interface VersionRange {

    boolean matches(Version version);
}

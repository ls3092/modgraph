package dev.modgraph.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.modgraph.analysis.ModConflict;
import dev.modgraph.graph.CycleGroup;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.resolve.DependencyResult;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 写出 candidates.jsonl、summary.csv、conflicts.csv、dependencies.csv、cycles.csv、duplicates.csv。
 *
 * <p>{@code cycles.csv} 一行只写一个环组的成员集合（按 ASCII 升序、用 {@code ;} 连接），
 * 不写路径、不写起点、不写方向——它既不表达也不暗示任何安装顺序或加载顺序（SPEC §3.6.5）。</p>
 *
 * <p>{@code duplicates.csv} 一行一个重复组，只陈述事实：没有推荐列、没有"保留哪一份"列（SPEC §3.7.3）。</p>
 */
public final class ReportWriter {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public void writeCandidates(final Path path, final List<ModCandidate> candidates) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (ModCandidate candidate : candidates) {
                writer.write(GSON.toJson(candidate));
                writer.newLine();
            }
        }
    }

    public void writeSummary(final Path path, final List<ModCandidate> candidates) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("sourceFile,sha256,loaderFamily,status,modId,version,modType,"
                    + "invasiveness,supportPolicy,failureCode");
            writer.newLine();
            for (ModCandidate candidate : candidates) {
                writer.write(String.join(",",
                        csv(candidate.sourceFile()),
                        csv(candidate.sha256()),
                        csv(enumName(candidate.loaderFamily())),
                        csv(enumName(candidate.status())),
                        csv(candidate.modId()),
                        csv(candidate.version()),
                        csv(enumName(candidate.modType())),
                        csv(enumName(candidate.invasiveness())),
                        csv(enumName(candidate.supportPolicy())),
                        csv(enumName(candidate.failureCode()))));
                writer.newLine();
            }
        }
    }

    public void writeConflicts(final Path path, final List<ModConflict> conflicts) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("modA,modB,level,sharedTargets");
            writer.newLine();
            for (ModConflict conflict : conflicts) {
                writer.write(String.join(",",
                        csv(conflict.modA()),
                        csv(conflict.modB()),
                        csv(conflict.level().name()),
                        csv(String.join(";", conflict.sharedTargets()))));
                writer.newLine();
            }
        }
    }

    public void writeDependencies(final Path path, final List<DependencyResult> results)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("modId,version,depId,depKind,declaredRange,resolvedVersion,outcome");
            writer.newLine();
            for (DependencyResult result : results) {
                writer.write(String.join(",",
                        csv(result.modId()),
                        csv(result.version()),
                        csv(result.depId()),
                        csv(result.depKind().csvName()),
                        csv(result.declaredRange()),
                        csv(result.resolvedVersion()),
                        csv(result.outcome().name())));
                writer.newLine();
            }
        }
    }

    public void writeCycles(final Path path, final List<CycleGroup> cycleGroups) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("universe,memberCount,members");
            writer.newLine();
            for (CycleGroup group : cycleGroups) {
                writer.write(String.join(",",
                        csv(group.universe().name()),
                        Integer.toString(group.memberCount()),
                        csv(group.csvMembers())));
                writer.newLine();
            }
        }
    }

    public void writeDuplicates(final Path path, final DuplicateIdReport report) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("universe,modId,recordCount,pairCount,relation,versions");
            writer.newLine();
            for (DuplicateIdGroup group : report.groups()) {
                writer.write(String.join(",",
                        csv(group.universe().name()),
                        csv(group.modId()),
                        Integer.toString(group.recordCount()),
                        Integer.toString(group.pairCount()),
                        group.relation().name(),
                        csv(group.csvVersions())));
                writer.newLine();
            }
        }
    }

    private static String enumName(final Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String csv(final String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0
                && value.indexOf('"') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

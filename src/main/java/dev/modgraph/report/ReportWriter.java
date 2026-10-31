package dev.modgraph.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.modgraph.analysis.ModConflict;
import dev.modgraph.model.ModCandidate;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 写出 candidates.jsonl、summary.csv、conflicts.csv。
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

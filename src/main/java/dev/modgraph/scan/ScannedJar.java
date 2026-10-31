package dev.modgraph.scan;

import dev.modgraph.model.ModCandidate;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ScannedJar(Path jarPath, ModCandidate candidate, List<String> entryNames) {

    public ScannedJar {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(candidate, "candidate");
        entryNames = List.copyOf(entryNames);
    }
}

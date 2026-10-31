package dev.modgraph.analysis;

import java.util.List;
import java.util.Objects;

public record ModConflict(
        String modA,
        String modB,
        ConflictLevel level,
        List<String> sharedTargets) {

    public ModConflict {
        Objects.requireNonNull(modA, "modA");
        Objects.requireNonNull(modB, "modB");
        Objects.requireNonNull(level, "level");
        sharedTargets = List.copyOf(sharedTargets);
    }
}

package dev.modgraph.model;

import java.util.List;
import java.util.Objects;

public record Constraint(
        String targetId,
        List<String> versionRanges,
        boolean required,
        Ordering ordering,
        Side side) {

    public Constraint {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(versionRanges, "versionRanges");
        Objects.requireNonNull(ordering, "ordering");
        Objects.requireNonNull(side, "side");
        versionRanges = List.copyOf(versionRanges);
    }
}

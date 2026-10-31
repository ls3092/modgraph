package dev.modgraph.model;

import java.util.List;
import java.util.Objects;

public record ModCandidate(
        String sourceFile,
        String sha256,
        LoaderFamily loaderFamily,
        Status status,
        String modId,
        String version,
        String name,
        Side side,
        List<Constraint> depends,
        List<Constraint> recommends,
        List<Constraint> breaks,
        List<String> provides,
        List<String> entrypoints,
        List<String> mixinConfigs,
        List<String> mixinTargets,
        boolean mixinTargetsInferred,
        List<String> nestedJars,
        ModType modType,
        Invasiveness invasiveness,
        List<String> invasivenessSignals,
        SupportPolicy supportPolicy,
        FailureCode failureCode,
        String failureMessage,
        List<String> unknownFields) {

    public ModCandidate {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(loaderFamily, "loaderFamily");
        Objects.requireNonNull(status, "status");
    }
}

package dev.modgraph.model;

public enum FailureCode {
    NOT_A_ZIP,
    NO_METADATA,
    MALFORMED_JSON,
    MALFORMED_TOML,
    MISSING_REQUIRED_FIELD,
    IO_ERROR,
    INTERNAL_ERROR
}

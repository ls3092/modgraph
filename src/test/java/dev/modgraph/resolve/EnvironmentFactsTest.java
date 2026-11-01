package dev.modgraph.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnvironmentFactsTest {

    private final EnvironmentFacts facts = EnvironmentFacts.declared();

    @Test
    void everyEnvironmentIdFromTheSpecIsDeclared() {
        assertThat(facts.all()).containsKeys(
                "minecraft", "fabricloader", "quilt_loader", "neoforge", "forge", "java");
        assertThat(facts.all().values()).allSatisfy(version -> assertThat(version).isNotBlank());
    }

    @Test
    void aliasesResolveToTheSameDeclaredVersion() {
        assertThat(facts.versionOf("minecraft_version")).contains("1.21.1");
        assertThat(facts.versionOf("fabric-loader")).contains("0.20.1");
        assertThat(facts.versionOf("quilt-loader")).contains("0.27.0");
        assertThat(facts.versionOf("MINECRAFT")).contains("1.21.1");
        assertThat(facts.versionOf(" minecraft ")).contains("1.21.1");
    }

    @Test
    void ordinaryModIdsAreNotEnvironmentProvided() {
        assertThat(facts.versionOf("sodium")).isEmpty();
        assertThat(facts.versionOf("fabric")).isEmpty();
        assertThat(facts.versionOf(null)).isEmpty();
    }

    @Test
    void descriptionListsEveryFactForAudit() {
        final String description = facts.describe();
        for (final String id : facts.all().keySet()) {
            assertThat(description).contains(id + "=");
        }
    }
}

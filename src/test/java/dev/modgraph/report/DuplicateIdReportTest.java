package dev.modgraph.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.graph.LoaderUniverse;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.7.3：{@code duplicateIds} 的定义——只在宇宙内、按版本原串分两类、只报告。
 */
class DuplicateIdReportTest {

    @Test
    void sameVersionGroupIsReportedAsSameVersion() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("puzzle", "2.3.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("puzzle", "2.3.0", LoaderFamily.FABRIC)));

        assertThat(report.groupCount()).isEqualTo(1);
        assertThat(report.pairCount()).isEqualTo(1);
        assertThat(report.sameVersionPairs()).isEqualTo(1);
        assertThat(report.differentVersionPairs()).isZero();
        final DuplicateIdGroup group = report.groups().get(0);
        assertThat(group.universe()).isEqualTo(LoaderUniverse.FABRIC);
        assertThat(group.modId()).isEqualTo("puzzle");
        assertThat(group.recordCount()).isEqualTo(2);
        assertThat(group.relation()).isEqualTo(VersionRelation.SAME_VERSION);
        assertThat(group.versions()).containsExactly("2.3.0");
    }

    @Test
    void differentVersionGroupIsReportedAsDifferentAndVersionsAreSorted() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("jei", "19.70.0.467", LoaderFamily.NEOFORGE),
                ReportFixtures.ok("jei", "19.64.0.455", LoaderFamily.NEOFORGE)));

        final DuplicateIdGroup group = report.groups().get(0);
        assertThat(group.relation()).isEqualTo(VersionRelation.DIFFERENT_VERSION);
        assertThat(group.versions()).containsExactly("19.64.0.455", "19.70.0.467");
        assertThat(group.csvVersions()).isEqualTo("19.64.0.455;19.70.0.467");
        assertThat(report.differentVersionPairs()).isEqualTo(1);
    }

    @Test
    void theSameModIdInTwoUniversesIsNotADuplicate() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("shared", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("shared", "1.0", LoaderFamily.NEOFORGE)));

        assertThat(report.isEmpty()).isTrue();
        assertThat(report.pairCount()).isZero();
    }

    @Test
    void quiltFoldsIntoFabricSoTwoRecordsAreOneGroup() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("mod", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("mod", "1.0", LoaderFamily.QUILT)));

        assertThat(report.groupCount()).isEqualTo(1);
        assertThat(report.groups().get(0).universe()).isEqualTo(LoaderUniverse.FABRIC);
        assertThat(report.groups().get(0).relation()).isEqualTo(VersionRelation.SAME_VERSION);
    }

    @Test
    void threeRecordsTwoVersionsHaveThreePairs() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("mod", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("mod", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("mod", "1.1", LoaderFamily.FABRIC)));

        final DuplicateIdGroup group = report.groups().get(0);
        assertThat(group.recordCount()).isEqualTo(3);
        assertThat(group.pairCount()).isEqualTo(3);
        assertThat(group.versions()).containsExactly("1.0", "1.1");
        assertThat(group.relation()).isEqualTo(VersionRelation.DIFFERENT_VERSION);
        assertThat(report.pairCount()).isEqualTo(3);
        assertThat(report.differentVersionPairs()).isEqualTo(3);
    }

    @Test
    void failedRecordsAreNotDuplicates() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("mod", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.failed("mod", "1.0", LoaderFamily.FABRIC)));

        assertThat(report.isEmpty()).isTrue();
    }

    @Test
    void groupsAreSortedByUniverseThenModId() {
        final DuplicateIdReport report = DuplicateIdReport.of(List.of(
                ReportFixtures.ok("zeta", "1.0", LoaderFamily.NEOFORGE),
                ReportFixtures.ok("zeta", "1.0", LoaderFamily.NEOFORGE),
                ReportFixtures.ok("beta", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("beta", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("alpha", "1.0", LoaderFamily.FABRIC),
                ReportFixtures.ok("alpha", "1.0", LoaderFamily.FABRIC)));

        assertThat(report.groups())
                .extracting(group -> group.universe().name() + ":" + group.modId())
                .containsExactly("FABRIC:alpha", "FABRIC:beta", "NEOFORGE:zeta");
    }
}

package edu.college.smartmatcher.intelligence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Dependency-free calculation checks; run after `mvn clean package` with the README command. */
public final class TeamIntelligenceChecks {
    private static final TeamIntelligenceService.Requirement JAVA =
            new TeamIntelligenceService.Requirement(1, "Java", 3);
    private static final TeamIntelligenceService.Requirement SQL =
            new TeamIntelligenceService.Requirement(2, "SQL", 3);
    private static int passed;

    private TeamIntelligenceChecks() { }

    public static void main(String[] args) {
        check("exact advanced match", TeamIntelligenceChecks::exactAdvancedMatchScoresOneHundred);
        check("partial match", TeamIntelligenceChecks::partialMatchScoresCoveredRequirementOnly);
        check("zero matching skills", TeamIntelligenceChecks::candidateWithNoSavedMatchingSkillsScoresZero);
        check("proficiency bands", TeamIntelligenceChecks::proficiencyBandsProduceDifferentScores);
        check("importance weighting", TeamIntelligenceChecks::highImportanceRequirementHasMoreScoreWeight);
        check("missing requirement", TeamIntelligenceChecks::missingRequirementAddsZeroToWeightedScore);
        check("no requirements", TeamIntelligenceChecks::projectsWithoutRequirementsHaveNoFabricatedScore);
        check("empty team", TeamIntelligenceChecks::emptyTeamStartsAtZeroCoverageWithEveryRequirementMissing);
        check("complete coverage", TeamIntelligenceChecks::intermediateOrAdvancedMemberProvidesCompleteCoverage);
        check("duplicate skill holders", TeamIntelligenceChecks::duplicateSkillHoldersDoNotInflateMaximumCoverage);
        check("critical gap impact", TeamIntelligenceChecks::candidateCanBeIdentifiedAsFillingACriticalMissingSkill);
        check("candidate impact preview", TeamIntelligenceChecks::impactPreviewReturnsCurrentAndProjectedCoverageAndBalance);
        check("capacity and duplicate workflow rules", TeamIntelligenceChecks::capacityAndDuplicateWorkflowRulesGateInvitations);
        check("project access rules", TeamIntelligenceChecks::projectAnalysisRequiresOwnershipAndNonRemovedStatus);
        System.out.println("Passed " + passed + " TeamIntelligenceService checks.");
    }

    private static void exactAdvancedMatchScoresOneHundred() {
        assertPercent("100.00", TeamIntelligenceService.matchPercent(List.of(JAVA, SQL), profile(1, "A", Map.of(1L, 5, 2L, 5))));
    }

    private static void partialMatchScoresCoveredRequirementOnly() {
        assertPercent("50.00", TeamIntelligenceService.matchPercent(List.of(JAVA, SQL), profile(1, "A", Map.of(1L, 5))));
    }

    private static void candidateWithNoSavedMatchingSkillsScoresZero() {
        assertPercent("0.00", TeamIntelligenceService.matchPercent(List.of(JAVA, SQL), profile(1, "A", Map.of())));
    }

    private static void proficiencyBandsProduceDifferentScores() {
        assertPercent("20.00", TeamIntelligenceService.matchPercent(List.of(JAVA), profile(1, "A", Map.of(1L, 1))));
        assertPercent("60.00", TeamIntelligenceService.matchPercent(List.of(JAVA), profile(2, "B", Map.of(1L, 4))));
        assertPercent("100.00", TeamIntelligenceService.matchPercent(List.of(JAVA), profile(3, "C", Map.of(1L, 5))));
    }

    private static void highImportanceRequirementHasMoreScoreWeight() {
        var critical = new TeamIntelligenceService.Requirement(1, "Critical", 5);
        var optional = new TeamIntelligenceService.Requirement(2, "Optional", 1);
        assertPercent("86.67", TeamIntelligenceService.matchPercent(List.of(critical, optional), profile(1, "A", Map.of(1L, 5, 2L, 1))));
    }

    private static void missingRequirementAddsZeroToWeightedScore() {
        assertPercent("50.00", TeamIntelligenceService.matchPercent(List.of(JAVA, SQL), profile(1, "A", Map.of(1L, 5))));
    }

    private static void projectsWithoutRequirementsHaveNoFabricatedScore() {
        if (TeamIntelligenceService.matchPercent(List.of(), profile(1, "A", Map.of())) != null) fail("no requirement score must be unavailable");
        if (TeamIntelligenceService.analyze(List.of(), List.of()).hasRequirements()) fail("no requirement analysis must be unavailable");
    }

    private static void emptyTeamStartsAtZeroCoverageWithEveryRequirementMissing() {
        var analysis = TeamIntelligenceService.analyze(List.of(JAVA, SQL), List.of());
        assertPercent("0.00", analysis.coveragePercent());
        if (analysis.missingCount() != 2 || analysis.isCompleteCoverage()) fail("empty team should leave all skills missing");
    }

    private static void intermediateOrAdvancedMemberProvidesCompleteCoverage() {
        var analysis = TeamIntelligenceService.analyze(List.of(JAVA, SQL), List.of(profile(1, "A", Map.of(1L, 3, 2L, 5))));
        if (!analysis.isCompleteCoverage() || analysis.fullCount() != 2 || analysis.missingCount() != 0) fail("all requirements should be covered");
    }

    private static void duplicateSkillHoldersDoNotInflateMaximumCoverage() {
        var one = profile(1, "A", Map.of(1L, 5));
        var alone = TeamIntelligenceService.analyze(List.of(JAVA, SQL), List.of(one));
        var duplicate = TeamIntelligenceService.analyze(List.of(JAVA, SQL), List.of(one, profile(2, "B", Map.of(1L, 5))));
        assertPercent(alone.coveragePercent().toPlainString(), duplicate.coveragePercent());
        if (duplicate.skills().get(0).contributorCount() != 2) fail("both contributors should be listed");
    }

    private static void candidateCanBeIdentifiedAsFillingACriticalMissingSkill() {
        var critical = new TeamIntelligenceService.Requirement(2, "Critical SQL", 5);
        var impact = TeamIntelligenceService.preview(List.of(JAVA, critical), List.of(profile(1, "A", Map.of(1L, 5))), profile(2, "B", Map.of(2L, 5)));
        if (!impact.fillsMissingSkills().equals(List.of("Critical SQL")) || impact.coverageGain().signum() <= 0) fail("candidate should cover the critical gap");
    }

    private static void impactPreviewReturnsCurrentAndProjectedCoverageAndBalance() {
        var impact = TeamIntelligenceService.preview(List.of(JAVA, SQL), List.of(profile(1, "A", Map.of(1L, 5))), profile(2, "B", Map.of(2L, 5)));
        assertPercent("50.00", impact.currentCoverage());
        assertPercent("100.00", impact.projectedCoverage());
        if (impact.projectedBalance().compareTo(impact.currentBalance()) <= 0) fail("preview should improve contribution balance");
    }

    private static void capacityAndDuplicateWorkflowRulesGateInvitations() {
        if (!TeamIntelligenceService.canInvite(true, true, false, 1, false, false, false, false, true)) fail("eligible candidate should be inviteable");
        if (TeamIntelligenceService.canInvite(true, true, false, 0, false, false, false, false, true)) fail("full team should block invite");
        if (TeamIntelligenceService.canInvite(true, true, false, 1, false, false, true, false, true)) fail("active request should block duplicate invite");
    }

    private static void projectAnalysisRequiresOwnershipAndNonRemovedStatus() {
        if (!TeamIntelligenceService.canViewProject(true, false)) fail("owner should see active project");
        if (TeamIntelligenceService.canViewProject(false, false)) fail("non-owner must not see project analysis");
        if (TeamIntelligenceService.canViewProject(true, true)) fail("removed project must not be analyzed");
    }

    private static TeamIntelligenceService.Profile profile(long id, String name, Map<Long, Integer> skills) {
        return new TeamIntelligenceService.Profile(id, name, "Computing", 2, skills);
    }

    private static void assertPercent(String expected, BigDecimal actual) {
        if (actual == null || new BigDecimal(expected).compareTo(actual) != 0) {
            fail("expected " + expected + " but received " + actual);
        }
    }

    private static void check(String name, Runnable test) {
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void fail(String message) { throw new AssertionError(message); }
}

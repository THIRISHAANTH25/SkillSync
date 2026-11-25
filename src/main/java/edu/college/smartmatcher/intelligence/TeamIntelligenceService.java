package edu.college.smartmatcher.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure calculations used by teammate recommendations, skill-gap analysis, and team previews. */
public final class TeamIntelligenceService {
    private TeamIntelligenceService() { }

    /** Project skill weight. Importance is kept in the MySQL 1-5 range. */
    public record Requirement(long skillId, String name, int importance) {
        public Requirement {
            name = name == null ? "Unnamed skill" : name;
            importance = Math.max(1, Math.min(5, importance));
        }
    }

    /** Public profile fields and skill levels needed for project analysis. */
    public record Profile(long studentId, String name, String department, int yearOfStudy,
                          Map<Long, Integer> proficiencyBySkill) {
        public Profile {
            name = name == null ? "Student" : name;
            department = department == null ? "Department not specified" : department;
            proficiencyBySkill = proficiencyBySkill == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(proficiencyBySkill));
        }
    }

    public record SkillCoverage(Requirement requirement, String level, int maximumProficiency,
                                int contributorCount, List<String> contributors) { }

    public record MemberContribution(Profile profile, BigDecimal contributionPercent) { }

    public record Analysis(boolean hasRequirements, BigDecimal balanceScore, BigDecimal coveragePercent,
                           BigDecimal breadthPercent, BigDecimal memberBalancePercent,
                           int fullCount, int partialCount, int missingCount,
                           List<SkillCoverage> skills, List<MemberContribution> memberContributions) {
        public boolean isCompleteCoverage() { return hasRequirements && missingCount == 0 && partialCount == 0; }
    }

    public record CandidateImpact(Profile candidate, BigDecimal matchPercent, BigDecimal currentCoverage,
                                  BigDecimal projectedCoverage, BigDecimal currentBalance,
                                  BigDecimal projectedBalance, BigDecimal coverageGain,
                                  List<String> fillsMissingSkills, List<String> explanation) { }

    /**
     * Legacy Student_Skills proficiency levels are grouped as Beginner (1-2),
     * Intermediate (3-4), and Advanced (5), matching the existing matcher UI.
     */
    public static int proficiencyWeight(int level) {
        if (level <= 0) return 0;
        if (level <= 2) return 1;
        if (level <= 4) return 3;
        return 5;
    }

    public static String proficiencyLabel(int level) {
        if (level <= 0) return "Not recorded";
        if (level <= 2) return "Beginner";
        if (level <= 4) return "Intermediate";
        return "Advanced";
    }

    /** Importance-weighted skill fit; a requirement with no saved skill contributes zero. */
    public static BigDecimal matchPercent(List<Requirement> requirements, Profile candidate) {
        if (requirements == null || requirements.isEmpty() || candidate == null) return null;
        double weightedFit = 0;
        double totalImportance = 0;
        for (Requirement requirement : requirements) {
            int proficiency = candidate.proficiencyBySkill().getOrDefault(requirement.skillId(), 0);
            weightedFit += requirement.importance() * (proficiencyWeight(proficiency) / 5.0);
            totalImportance += requirement.importance();
        }
        if (totalImportance == 0) return null;
        return rounded(weightedFit * 100.0 / totalImportance);
    }

    public static Analysis analyze(List<Requirement> requirements, List<Profile> members) {
        List<Requirement> safeRequirements = requirements == null ? List.of() : requirements;
        List<Profile> safeMembers = members == null ? List.of() : members;
        if (safeRequirements.isEmpty()) {
            return new Analysis(false, null, null, null, null, 0, 0, 0, List.of(), List.of());
        }

        double importanceTotal = safeRequirements.stream().mapToDouble(Requirement::importance).sum();
        double covered = 0;
        double breadth = 0;
        int full = 0, partial = 0, missing = 0;
        List<SkillCoverage> rows = new ArrayList<>();
        Map<Long, Double> memberContributions = new LinkedHashMap<>();
        for (Profile member : safeMembers) memberContributions.put(member.studentId(), 0.0);

        for (Requirement requirement : safeRequirements) {
            int maxLevel = 0;
            int contributors = 0;
            List<String> names = new ArrayList<>();
            for (Profile member : safeMembers) {
                int level = member.proficiencyBySkill().getOrDefault(requirement.skillId(), 0);
                if (level > 0) {
                    contributors++;
                    names.add(member.name());
                    memberContributions.merge(member.studentId(),
                            requirement.importance() * (proficiencyWeight(level) / 5.0), Double::sum);
                }
                maxLevel = Math.max(maxLevel, level);
            }
            double coverage = proficiencyWeight(maxLevel) / 5.0;
            covered += requirement.importance() * coverage;
            if (maxLevel >= 3) {
                full++;
                breadth += requirement.importance();
                rows.add(new SkillCoverage(requirement, "Covered", maxLevel, contributors, List.copyOf(names)));
            } else if (maxLevel > 0) {
                partial++;
                rows.add(new SkillCoverage(requirement, "Partial", maxLevel, contributors, List.copyOf(names)));
            } else {
                missing++;
                rows.add(new SkillCoverage(requirement, "Missing", 0, 0, List.of()));
            }
        }

        double coveragePercent = covered * 100.0 / importanceTotal;
        double breadthPercent = breadth * 100.0 / importanceTotal;
        double balancePercent = distributionBalance(memberContributions);
        // Explainable heuristic: coverage 75%, intermediate-or-better breadth 15%,
        // and spread of contributions across capable members 10%.
        double balanceScore = coveragePercent * 0.75 + breadthPercent * 0.15 + balancePercent * 0.10;
        List<MemberContribution> contributionRows = contributionRows(safeMembers, memberContributions);
        return new Analysis(true, rounded(balanceScore), rounded(coveragePercent), rounded(breadthPercent),
                rounded(balancePercent), full, partial, missing, List.copyOf(rows), contributionRows);
    }

    public static CandidateImpact preview(List<Requirement> requirements, List<Profile> currentMembers,
                                          Profile candidate) {
        Analysis before = analyze(requirements, currentMembers);
        if (!before.hasRequirements() || candidate == null) return null;
        List<Profile> afterMembers = new ArrayList<>(currentMembers == null ? List.of() : currentMembers);
        if (afterMembers.stream().noneMatch(member -> member.studentId() == candidate.studentId())) {
            afterMembers.add(candidate);
        }
        Analysis after = analyze(requirements, afterMembers);
        Set<Long> currentlyCovered = new LinkedHashSet<>();
        for (SkillCoverage skill : before.skills()) if (!"Missing".equals(skill.level())) {
            currentlyCovered.add(skill.requirement().skillId());
        }
        List<String> newSkills = new ArrayList<>();
        for (Requirement requirement : requirements) {
            if (!currentlyCovered.contains(requirement.skillId())
                    && candidate.proficiencyBySkill().getOrDefault(requirement.skillId(), 0) > 0) {
                newSkills.add(requirement.name());
            }
        }
        List<String> reasons = new ArrayList<>();
        if (!newSkills.isEmpty()) reasons.add("Adds coverage for " + String.join(", ", newSkills) + ".");
        if (after.coveragePercent().compareTo(before.coveragePercent()) > 0) {
            reasons.add("Raises weighted team coverage from " + before.coveragePercent().toPlainString()
                    + "% to " + after.coveragePercent().toPlainString() + "%.");
        } else if (newSkills.isEmpty()) {
            reasons.add("Adds depth to skills already represented on the team.");
        }
        if (after.balanceScore().compareTo(before.balanceScore()) > 0) {
            reasons.add("Improves the team balance score by distributing skill contribution.");
        }
        return new CandidateImpact(candidate, matchPercent(requirements, candidate), before.coveragePercent(),
                after.coveragePercent(), before.balanceScore(), after.balanceScore(),
                rounded(after.coveragePercent().doubleValue() - before.coveragePercent().doubleValue()),
                List.copyOf(newSkills), List.copyOf(reasons));
    }

    /** Pure eligibility rule mirrored by the server-side invitation workflow. */
    public static boolean canInvite(boolean requesterOwnsProject, boolean projectOpen, boolean projectRemoved,
                                    int availableSeats, boolean candidateIsOwner, boolean candidateIsMember,
                                    boolean hasActiveRequest, boolean hasActiveInvitation, boolean candidateActive) {
        return requesterOwnsProject && projectOpen && !projectRemoved && availableSeats > 0 && !candidateIsOwner
                && !candidateIsMember && !hasActiveRequest && !hasActiveInvitation && candidateActive;
    }

    /** Ownership and removal guard used before analysis data is loaded for a requested project. */
    public static boolean canViewProject(boolean requesterOwnsProject, boolean projectRemoved) {
        return requesterOwnsProject && !projectRemoved;
    }

    private static double distributionBalance(Map<Long, Double> contributions) {
        List<Double> positive = contributions.values().stream().filter(value -> value > 0).toList();
        int count = positive.size();
        if (count < 2) return 0;
        double total = positive.stream().mapToDouble(Double::doubleValue).sum();
        double hhi = positive.stream().mapToDouble(value -> Math.pow(value / total, 2)).sum();
        return clamp((1.0 - hhi) / (1.0 - (1.0 / count)) * 100.0, 0, 100);
    }

    private static List<MemberContribution> contributionRows(List<Profile> members, Map<Long, Double> contributions) {
        double total = contributions.values().stream().mapToDouble(Double::doubleValue).sum();
        List<MemberContribution> rows = new ArrayList<>();
        for (Profile member : members) {
            double percent = total == 0 ? 0 : contributions.getOrDefault(member.studentId(), 0.0) * 100 / total;
            rows.add(new MemberContribution(member, rounded(percent)));
        }
        rows.sort(Comparator.comparing(MemberContribution::contributionPercent).reversed()
                .thenComparing(row -> row.profile().name(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(rows);
    }

    private static BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}

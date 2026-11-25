package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.Analysis;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.CandidateImpact;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.Profile;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.Requirement;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.SkillCoverage;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Authenticated, owner-scoped views for project skill gaps and team balance. */
public class TeamAnalysisServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String PAGE = "/WEB-INF/team-analysis.jsp";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        Long ownerId = authenticatedStudentId(request, response);
        if (ownerId == null) return;
        boolean gapPage = "/skill-gaps".equals(request.getServletPath());
        String mode = gapPage ? "skill-gaps" : "team-balance";
        request.setAttribute("analysisMode", mode);
        TeamWorkflowServlet.applyMessage(request);
        List<ProjectOption> projects = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection()) {
            loadOwnedProjects(connection, ownerId, projects);
            ProjectOption selected = chooseProject(request.getParameter("project_id"), projects, request);
            request.setAttribute("projects", projects);
            request.setAttribute("selectedProject", selected);
            request.setAttribute("inviteCsrfToken",
                    TeamWorkflowServlet.issueInviteCsrfToken(request.getSession(false)));
            if (selected != null) populateAnalysis(connection, ownerId, selected, mode);
        } catch (SQLException exception) {
            getServletContext().log("Loading project team analysis failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "Project analysis is temporarily unavailable. Please try again shortly.");
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    private Long authenticatedStudentId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("studentId");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0) {
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        return ((Number) value).longValue();
    }

    private void loadOwnedProjects(Connection connection, long ownerId, List<ProjectOption> projects)
            throws SQLException {
        String sql = "SELECT p.project_id,p.title,p.status,p.required_team_size,p.creator_id,p.created_at, "
                + "(SELECT COUNT(DISTINCT tm.student_id) FROM Teams t JOIN Team_Members tm "
                + "ON tm.team_id=t.team_id WHERE t.project_id=p.project_id) AS member_count, "
                + "(SELECT COUNT(*) FROM Project_Invitations pi WHERE pi.project_id=p.project_id "
                + "AND pi.status='PENDING') AS pending_count, "
                + "EXISTS(SELECT 1 FROM Teams t0 JOIN Team_Members tm0 ON tm0.team_id=t0.team_id "
                + "WHERE t0.project_id=p.project_id AND tm0.student_id=p.creator_id) AS owner_is_member "
                + "FROM Projects p WHERE p.creator_id=? AND p.status<>'REMOVED' "
                + "ORDER BY p.created_at DESC,p.project_id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp timestamp = result.getTimestamp("created_at");
                    int memberCount = result.getInt("member_count") + (result.getBoolean("owner_is_member") ? 0 : 1);
                    projects.add(new ProjectOption(result.getLong("project_id"), result.getString("title"),
                            result.getString("status"), result.getInt("required_team_size"),
                            Math.min(result.getInt("required_team_size"), memberCount), result.getInt("pending_count"),
                            timestamp == null ? "" : DATE_FORMAT.format(timestamp.toLocalDateTime())));
                }
            }
        }
    }

    private ProjectOption chooseProject(String rawId, List<ProjectOption> projects, HttpServletRequest request) {
        if (rawId != null && !rawId.isBlank()) {
            try {
                long id = Long.parseLong(rawId);
                for (ProjectOption project : projects) if (project.projectId == id) return project;
            } catch (NumberFormatException ignored) { /* Invalid input is handled like an inaccessible project. */ }
            request.setAttribute("error", "That project is unavailable or you do not have access to it.");
            return null;
        }
        return projects.isEmpty() ? null : projects.get(0);
    }

    private void populateAnalysis(Connection connection, long ownerId, ProjectOption project, String mode)
            throws SQLException {
        List<Requirement> requirements = loadRequirements(connection, project.projectId);
        Map<Long, String> roleByStudent = loadCurrentMemberRoles(connection, project.projectId);
        roleByStudent.putIfAbsent(ownerId, "LEADER");
        List<Long> memberIds = new ArrayList<>(roleByStudent.keySet());
        Map<Long, Profile> membersById = loadProfiles(connection, memberIds, requirements, false);
        List<Profile> members = new ArrayList<>();
        for (Long memberId : memberIds) {
            Profile profile = membersById.get(memberId);
            if (profile != null) members.add(profile);
        }

        Map<Long, Profile> candidatesById = loadProfiles(connection, memberIds, requirements, true,
                project.projectId, ownerId);
        Map<Long, String> inviteStatus = loadInteractions(connection, project.projectId, candidatesById.keySet(),
                "Project_Invitations", "status", "PENDING");
        Map<Long, String> requestStatus = loadInteractions(connection, project.projectId, candidatesById.keySet(),
                "Project_Applications", "status", "PENDING", "SHORTLISTED", "ACCEPTED");

        Analysis analysis = TeamIntelligenceService.analyze(requirements, members);
        project.setAnalysis(analysis, requirements, members, roleByStudent);
        List<CandidateDisplay> candidates = new ArrayList<>();
        for (Profile candidate : candidatesById.values()) {
            CandidateImpact impact = TeamIntelligenceService.preview(requirements, members, candidate);
            if (impact == null || impact.matchPercent() == null || impact.matchPercent().signum() <= 0) continue;
            candidates.add(new CandidateDisplay(impact, requirements, inviteStatus.get(candidate.studentId()),
                    requestStatus.containsKey(candidate.studentId()), project, roleByStudent.containsKey(candidate.studentId()),
                    candidate.studentId() == ownerId));
        }
        Comparator<CandidateDisplay> order;
        if ("skill-gaps".equals(mode)) {
            order = Comparator.comparing(CandidateDisplay::getCoverageGain, Comparator.reverseOrder())
                    .thenComparing(Comparator.comparingInt(CandidateDisplay::getGapFillCount).reversed())
                    .thenComparing(CandidateDisplay::getMatchPercent, Comparator.reverseOrder())
                    .thenComparing(CandidateDisplay::getFullName, String.CASE_INSENSITIVE_ORDER);
        } else {
            order = Comparator.comparing(CandidateDisplay::getProjectedBalanceGain, Comparator.reverseOrder())
                    .thenComparing(CandidateDisplay::getCoverageGain, Comparator.reverseOrder())
                    .thenComparing(CandidateDisplay::getMatchPercent, Comparator.reverseOrder())
                    .thenComparing(CandidateDisplay::getFullName, String.CASE_INSENSITIVE_ORDER);
        }
        candidates.sort(order);
        project.setCandidates(candidates);
    }

    private List<Requirement> loadRequirements(Connection connection, long projectId) throws SQLException {
        List<Requirement> requirements = new ArrayList<>();
        String sql = "SELECT s.skill_id,s.skill_name,ps.importance_level FROM Project_Skills ps "
                + "JOIN Skills s ON s.skill_id=ps.skill_id WHERE ps.project_id=? ORDER BY ps.importance_level DESC,s.skill_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) requirements.add(new Requirement(result.getLong("skill_id"),
                        result.getString("skill_name"), result.getInt("importance_level")));
            }
        }
        return requirements;
    }

    private Map<Long, String> loadCurrentMemberRoles(Connection connection, long projectId) throws SQLException {
        Map<Long, String> roles = new LinkedHashMap<>();
        String sql = "SELECT tm.student_id,tm.member_role FROM Teams t JOIN Team_Members tm ON tm.team_id=t.team_id "
                + "WHERE t.project_id=? ORDER BY tm.member_role DESC,tm.joined_at,tm.student_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) roles.putIfAbsent(result.getLong("student_id"), result.getString("member_role"));
            }
        }
        return roles;
    }

    private Map<Long, Profile> loadProfiles(Connection connection, List<Long> memberIds,
                                            List<Requirement> requirements, boolean candidates)
            throws SQLException {
        return loadProfiles(connection, memberIds, requirements, candidates, -1, -1);
    }

    private Map<Long, Profile> loadProfiles(Connection connection, List<Long> memberIds,
                                            List<Requirement> requirements, boolean candidates,
                                            long projectId, long ownerId) throws SQLException {
        Map<Long, MutableProfile> mutable = new LinkedHashMap<>();
        List<Long> skillIds = requirements.stream().map(Requirement::skillId).toList();
        if (candidates && skillIds.isEmpty()) return Map.of();
        String skillPlaceholders = skillIds.isEmpty() ? "" : placeholders(skillIds.size());
        String sql;
        if (candidates) {
            sql = "SELECT DISTINCT st.student_id,st.full_name,st.department,st.year_of_study FROM Students st "
                    + "JOIN Student_Skills ss ON ss.student_id=st.student_id WHERE st.is_suspended=FALSE "
                    + "AND st.student_id<>? AND ss.skill_id IN (" + skillPlaceholders + ") "
                    + "AND NOT EXISTS (SELECT 1 FROM Teams t JOIN Team_Members tm ON tm.team_id=t.team_id "
                    + "WHERE t.project_id=? AND tm.student_id=st.student_id) "
                    + "ORDER BY st.full_name";
        } else {
            if (memberIds.isEmpty()) return Map.of();
            sql = "SELECT st.student_id,st.full_name,st.department,st.year_of_study FROM Students st "
                    + "WHERE st.student_id IN (" + placeholders(memberIds.size()) + ") ORDER BY st.full_name";
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (candidates) statement.setLong(index++, ownerId);
            else for (Long id : memberIds) statement.setLong(index++, id);
            if (candidates) {
                for (Long skillId : skillIds) statement.setLong(index++, skillId);
                statement.setLong(index, projectId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = result.getLong("student_id");
                    mutable.put(id, new MutableProfile(id, result.getString("full_name"),
                            result.getString("department"), result.getInt("year_of_study")));
                }
            }
        }
        if (mutable.isEmpty()) return Map.of();
        String idPlaceholders = placeholders(mutable.size());
        if (!skillIds.isEmpty()) {
            String skillSql = "SELECT student_id,skill_id,proficiency_level FROM Student_Skills WHERE student_id IN ("
                    + idPlaceholders + ") AND skill_id IN (" + skillPlaceholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(skillSql)) {
                int index = 1;
                for (Long id : mutable.keySet()) statement.setLong(index++, id);
                for (Long skillId : skillIds) statement.setLong(index++, skillId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        MutableProfile profile = mutable.get(result.getLong("student_id"));
                        if (profile != null) profile.skills.put(result.getLong("skill_id"), result.getInt("proficiency_level"));
                    }
                }
            }
        }
        Map<Long, Profile> profiles = new LinkedHashMap<>();
        mutable.forEach((id, value) -> profiles.put(id,
                new Profile(id, value.name, value.department, value.year, value.skills)));
        return profiles;
    }

    private Map<Long, String> loadInteractions(Connection connection, long projectId, Set<Long> studentIds,
                                               String table, String column, String... statuses) throws SQLException {
        if (studentIds.isEmpty()) return Map.of();
        // table and column names are constants selected only by this class, never by browser input.
        String statusPlaceholders = placeholders(statuses.length);
        String idPlaceholders = placeholders(studentIds.size());
        String sql = "SELECT student_id," + column + " FROM " + table + " WHERE project_id=? AND student_id IN ("
                + idPlaceholders + ") AND " + column + " IN (" + statusPlaceholders + ")";
        Map<Long, String> resultMap = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setLong(index++, projectId);
            for (Long id : studentIds) statement.setLong(index++, id);
            for (String status : statuses) statement.setString(index++, status);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) resultMap.put(result.getLong("student_id"), result.getString(column));
            }
        }
        return resultMap;
    }

    private String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }

    private static final class MutableProfile {
        private final long id; private final String name, department; private final int year;
        private final Map<Long, Integer> skills = new LinkedHashMap<>();
        private MutableProfile(long id, String name, String department, int year) {
            this.id = id; this.name = name; this.department = department; this.year = year;
        }
    }

    public static final class ProjectOption {
        private final long projectId;
        private final String title, status, createdDate;
        private final int teamSize, occupiedSlots, pendingInvitations;
        private Analysis analysis;
        private List<SkillRow> skillRows = List.of();
        private List<MemberRow> members = List.of();
        private List<CandidateDisplay> candidates = List.of();
        private ProjectOption(long id, String title, String status, int teamSize, int occupiedSlots,
                              int pendingInvitations, String createdDate) {
            this.projectId = id; this.title = title; this.status = status; this.teamSize = teamSize;
            this.occupiedSlots = occupiedSlots; this.pendingInvitations = pendingInvitations;
            this.createdDate = createdDate;
        }
        private void setAnalysis(Analysis analysis, List<Requirement> requirements, List<Profile> members,
                                 Map<Long, String> roles) {
            this.analysis = analysis;
            List<SkillRow> rows = new ArrayList<>();
            for (SkillCoverage coverage : analysis.skills()) rows.add(new SkillRow(coverage));
            skillRows = List.copyOf(rows);
            Map<Long, java.math.BigDecimal> contribution = new LinkedHashMap<>();
            for (var row : analysis.memberContributions()) contribution.put(row.profile().studentId(), row.contributionPercent());
            List<MemberRow> memberRows = new ArrayList<>();
            for (Profile profile : members) memberRows.add(new MemberRow(profile, roles.getOrDefault(profile.studentId(), "MEMBER"),
                    contribution.getOrDefault(profile.studentId(), java.math.BigDecimal.ZERO), requirements));
            this.members = List.copyOf(memberRows);
        }
        private void setCandidates(List<CandidateDisplay> candidates) { this.candidates = List.copyOf(candidates); }
        public long getProjectId() { return projectId; }
        public String getTitle() { return title; }
        public String getStatus() { return status; }
        public String getCreatedDate() { return createdDate; }
        public int getTeamSize() { return teamSize; }
        public int getOccupiedSlots() { return occupiedSlots; }
        public int getPendingInvitations() { return pendingInvitations; }
        public int getAvailableSeats() { return Math.max(0, teamSize - occupiedSlots - pendingInvitations); }
        public boolean isOpen() { return "OPEN".equals(status); }
        public boolean isFull() { return getAvailableSeats() == 0; }
        public Analysis getAnalysis() { return analysis; }
        public boolean isHasRequirements() { return analysis != null && analysis.hasRequirements(); }
        public boolean isCompleteCoverage() { return analysis != null && analysis.isCompleteCoverage(); }
        public java.math.BigDecimal getBalanceScore() { return analysis == null ? null : analysis.balanceScore(); }
        public java.math.BigDecimal getCoveragePercent() { return analysis == null ? null : analysis.coveragePercent(); }
        public java.math.BigDecimal getBreadthPercent() { return analysis == null ? null : analysis.breadthPercent(); }
        public java.math.BigDecimal getMemberBalancePercent() { return analysis == null ? null : analysis.memberBalancePercent(); }
        public int getFullCount() { return analysis == null ? 0 : analysis.fullCount(); }
        public int getPartialCount() { return analysis == null ? 0 : analysis.partialCount(); }
        public int getMissingCount() { return analysis == null ? 0 : analysis.missingCount(); }
        public List<SkillRow> getSkillRows() { return skillRows; }
        public List<MemberRow> getMembers() { return members; }
        public List<CandidateDisplay> getCandidates() { return candidates; }
    }

    public static final class SkillRow {
        private final String name, level;
        private final int importance, proficiency, contributorCount;
        private final List<String> contributors;
        private SkillRow(SkillCoverage row) {
            this.name = row.requirement().name(); this.importance = row.requirement().importance();
            this.level = row.level(); this.proficiency = row.maximumProficiency();
            this.contributorCount = row.contributorCount(); this.contributors = row.contributors();
        }
        public String getName() { return name; }
        public int getImportance() { return importance; }
        public String getLevel() { return level; }
        public String getLevelClass() { return level.toLowerCase(Locale.ROOT); }
        public int getProficiency() { return proficiency; }
        public int getContributorCount() { return contributorCount; }
        public String getContributorsText() { return contributors.isEmpty() ? "No current member" : String.join(", ", contributors); }
        public int getBarPercent() { return TeamIntelligenceService.proficiencyWeight(proficiency) * 20; }
    }

    public static final class MemberRow {
        private final Profile profile;
        private final String role;
        private final java.math.BigDecimal contribution;
        private final List<MemberSkill> skills;
        private MemberRow(Profile profile, String role, java.math.BigDecimal contribution, List<Requirement> requirements) {
            this.profile = profile; this.role = role; this.contribution = contribution;
            List<MemberSkill> items = new ArrayList<>();
            for (Requirement requirement : requirements) {
                int level = profile.proficiencyBySkill().getOrDefault(requirement.skillId(), 0);
                if (level > 0) items.add(new MemberSkill(requirement.name(), TeamIntelligenceService.proficiencyLabel(level), level));
            }
            this.skills = List.copyOf(items);
        }
        public String getName() { return profile.name(); }
        public String getDepartment() { return profile.department(); }
        public int getYearOfStudy() { return profile.yearOfStudy(); }
        public String getRole() { return role; }
        public java.math.BigDecimal getContribution() { return contribution; }
        public List<MemberSkill> getSkills() { return skills; }
    }

    public static final class MemberSkill {
        private final String name, proficiency; private final int level;
        private MemberSkill(String name, String proficiency, int level) { this.name = name; this.proficiency = proficiency; this.level = level; }
        public String getName() { return name; }
        public String getProficiency() { return proficiency; }
        public String getProficiencyClass() { return proficiency.toLowerCase(Locale.ROOT); }
        public int getLevel() { return level; }
    }

    public static final class CandidateDisplay {
        private final CandidateImpact impact;
        private final List<CandidateSkill> matchedSkills;
        private final String invitationStatus;
        private final boolean activeRequest, member, owner;
        private final ProjectOption project;
        private CandidateDisplay(CandidateImpact impact, List<Requirement> requirements,
                                 String invitationStatus, boolean activeRequest,
                                 ProjectOption project, boolean member, boolean owner) {
            this.impact = impact; this.invitationStatus = invitationStatus; this.activeRequest = activeRequest;
            this.project = project; this.member = member; this.owner = owner;
            List<CandidateSkill> matches = new ArrayList<>();
            for (Requirement requirement : requirements) {
                int proficiency = impact.candidate().proficiencyBySkill().getOrDefault(requirement.skillId(), 0);
                if (proficiency > 0) matches.add(new CandidateSkill(requirement.name(), requirement.importance(), proficiency));
            }
            this.matchedSkills = List.copyOf(matches);
        }
        public long getStudentId() { return impact.candidate().studentId(); }
        public String getFullName() { return impact.candidate().name(); }
        public String getDepartment() { return impact.candidate().department(); }
        public int getYearOfStudy() { return impact.candidate().yearOfStudy(); }
        public java.math.BigDecimal getMatchPercent() { return impact.matchPercent(); }
        public java.math.BigDecimal getCurrentCoverage() { return impact.currentCoverage(); }
        public java.math.BigDecimal getProjectedCoverage() { return impact.projectedCoverage(); }
        public java.math.BigDecimal getCurrentBalance() { return impact.currentBalance(); }
        public java.math.BigDecimal getProjectedBalance() { return impact.projectedBalance(); }
        public java.math.BigDecimal getCoverageGain() { return impact.coverageGain(); }
        public java.math.BigDecimal getProjectedBalanceGain() {
            return impact.projectedBalance().subtract(impact.currentBalance()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        public int getGapFillCount() { return impact.fillsMissingSkills().size(); }
        public List<CandidateSkill> getMatchedSkills() { return matchedSkills; }
        public List<String> getFillsMissingSkills() { return impact.fillsMissingSkills(); }
        public List<String> getExplanations() { return impact.explanation(); }
        public String getInvitationStatus() { return invitationStatus == null ? "" : invitationStatus; }
        public boolean hasActiveRequest() { return activeRequest; }
        public boolean isInvitationActive() { return "PENDING".equals(invitationStatus) || "ACCEPTED".equals(invitationStatus); }
        public boolean isInviteAvailable() {
            return TeamIntelligenceService.canInvite(true, project.isOpen(), "REMOVED".equals(project.status),
                    project.getAvailableSeats(), owner, member, activeRequest, isInvitationActive(), true);
        }
        public String getInviteUnavailableReason() {
            if (!project.isOpen()) return "Invitations are available for open projects only.";
            if (project.getAvailableSeats() <= 0) return "No seats are currently available.";
            if (isInvitationActive()) return "An invitation is already active.";
            if (activeRequest) return "This student already has an active request.";
            if (member) return "This student is already a team member.";
            return "This student cannot be invited right now.";
        }
    }

    public static final class CandidateSkill {
        private final String name, proficiency;
        private final int importance;
        private CandidateSkill(String name, int importance, int level) {
            this.name = name; this.importance = importance;
            this.proficiency = TeamIntelligenceService.proficiencyLabel(level);
        }
        public String getName() { return name; }
        public int getImportance() { return importance; }
        public String getProficiency() { return proficiency; }
        public String getProficiencyClass() { return proficiency.toLowerCase(Locale.ROOT); }
    }
}

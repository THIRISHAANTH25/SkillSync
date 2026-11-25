package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.Profile;
import edu.college.smartmatcher.intelligence.TeamIntelligenceService.Requirement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recommends students for projects owned by the authenticated student. */
public class FindTeammatesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String PAGE = "/WEB-INF/teammates.jsp";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) return;
        TeamWorkflowServlet.applyMessage(request);

        List<ProjectView> projects = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection()) {
            loadOwnedProjects(connection, studentId, projects);
            Map<Long, CandidateSkills> candidates = loadCandidateSkills(connection, studentId, projects);
            loadProjectInteractions(connection, studentId, projects);
            buildRecommendations(projects, candidates);
            saveCurrentScores(connection, projects);
            request.setAttribute("projects", projects);
            request.setAttribute("loggedInStudentId", studentId);
            request.setAttribute("inviteCsrfToken",
                    TeamWorkflowServlet.issueInviteCsrfToken(request.getSession(false)));
        } catch (SQLException exception) {
            getServletContext().log("Loading teammate recommendations failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "Recommendations are temporarily unavailable. Please try again shortly.");
            request.setAttribute("projects", projects);
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    /** Student identity comes only from the authenticated session, never a request parameter. */
    private Long authenticatedStudentId(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("studentId");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0) {
            if (session != null && value instanceof Number) session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        return ((Number) value).longValue();
    }

    /** Load only the current owner's projects; removed projects are hidden from student listings. */
    private void loadOwnedProjects(Connection connection, long ownerId, List<ProjectView> projects)
            throws SQLException {
        String sql = "SELECT p.project_id, p.title, p.description, p.creator_id, p.required_team_size, p.status, "
                + "p.created_at, s.skill_id, s.skill_name, ps.importance_level, "
                + "(SELECT COUNT(DISTINCT tm.student_id) FROM Team_Members tm JOIN Teams t "
                + "ON t.team_id = tm.team_id WHERE t.project_id = p.project_id) "
                + "+ CASE WHEN EXISTS (SELECT 1 FROM Teams t0 JOIN Team_Members tm0 "
                + "ON tm0.team_id = t0.team_id WHERE t0.project_id = p.project_id "
                + "AND tm0.student_id = p.creator_id) THEN 0 ELSE 1 END AS occupied_slots, "
                + "(SELECT COUNT(*) FROM Project_Invitations pi WHERE pi.project_id = p.project_id "
                + "AND pi.status = 'PENDING') AS pending_invitations "
                + "FROM Projects p LEFT JOIN Project_Skills ps ON ps.project_id = p.project_id "
                + "LEFT JOIN Skills s ON s.skill_id = ps.skill_id "
                + "WHERE p.creator_id = ? AND p.status <> 'REMOVED' "
                + "ORDER BY p.created_at DESC, p.project_id DESC, s.skill_name";
        Map<Long, ProjectView> byId = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long projectId = result.getLong("project_id");
                    ProjectView project = byId.get(projectId);
                    if (project == null) {
                        Timestamp createdAt = result.getTimestamp("created_at");
                        project = new ProjectView(projectId, result.getString("title"),
                                result.getString("description"), result.getLong("creator_id"),
                                result.getInt("required_team_size"), result.getString("status"),
                                createdAt == null ? "" : DATE_FORMAT.format(createdAt.toLocalDateTime()),
                                result.getInt("occupied_slots"), result.getInt("pending_invitations"));
                        byId.put(projectId, project);
                    }
                    long skillId = result.getLong("skill_id");
                    if (!result.wasNull()) project.addRequiredSkill(skillId, result.getString("skill_name"),
                            result.getInt("importance_level"));
                }
            }
        }
        projects.addAll(byId.values());
    }

    /** Load candidate skills once and omit suspended accounts and the project owner. */
    private Map<Long, CandidateSkills> loadCandidateSkills(Connection connection, long loggedInStudentId,
                                                            List<ProjectView> projects) throws SQLException {
        Set<Long> requiredSkillIds = new LinkedHashSet<>();
        for (ProjectView project : projects) {
            if (project.canShowRecommendations()) requiredSkillIds.addAll(project.requiredSkills.keySet());
        }
        Map<Long, CandidateSkills> candidates = new LinkedHashMap<>();
        if (requiredSkillIds.isEmpty()) return candidates;

        String placeholders = String.join(",", Collections.nCopies(requiredSkillIds.size(), "?"));
        String sql = "SELECT st.student_id, st.full_name, st.department, st.year_of_study, "
                + "sk.skill_id, sk.skill_name, ss.proficiency_level FROM Student_Skills ss "
                + "JOIN Students st ON st.student_id = ss.student_id "
                + "JOIN Skills sk ON sk.skill_id = ss.skill_id "
                + "WHERE st.is_suspended = FALSE AND st.student_id <> ? AND ss.skill_id IN (" + placeholders + ") "
                + "ORDER BY st.full_name, sk.skill_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, loggedInStudentId);
            int index = 2;
            for (Long skillId : requiredSkillIds) statement.setLong(index++, skillId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long candidateId = result.getLong("student_id");
                    CandidateSkills candidate = candidates.get(candidateId);
                    if (candidate == null) {
                        candidate = new CandidateSkills(candidateId, result.getString("full_name"),
                                result.getString("department"), result.getInt("year_of_study"));
                        candidates.put(candidateId, candidate);
                    }
                    candidate.addSkill(result.getLong("skill_id"), result.getString("skill_name"),
                            result.getInt("proficiency_level"));
                }
            }
        }
        return candidates;
    }

    /** Add existing invitations, active requests, and team memberships for accurate button states. */
    private void loadProjectInteractions(Connection connection, long ownerId, List<ProjectView> projects)
            throws SQLException {
        if (projects.isEmpty()) return;
        String ids = String.join(",", Collections.nCopies(projects.size(), "?"));
        String invitations = "SELECT project_id, student_id, status FROM Project_Invitations "
                + "WHERE project_id IN (" + ids + ") AND status IN ('PENDING', 'ACCEPTED')";
        try (PreparedStatement statement = connection.prepareStatement(invitations)) {
            bindProjectIds(statement, projects);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) findProject(projects, result.getLong("project_id"))
                        .setInvitationStatus(result.getLong("student_id"), result.getString("status"));
            }
        }
        String applications = "SELECT project_id, student_id FROM Project_Applications WHERE project_id IN (" + ids
                + ") AND status IN ('PENDING', 'SHORTLISTED', 'ACCEPTED')";
        try (PreparedStatement statement = connection.prepareStatement(applications)) {
            bindProjectIds(statement, projects);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) findProject(projects, result.getLong("project_id"))
                        .activeRequests.add(result.getLong("student_id"));
            }
        }
        String members = "SELECT t.project_id, tm.student_id FROM Teams t JOIN Team_Members tm "
                + "ON tm.team_id = t.team_id WHERE t.project_id IN (" + ids + ")";
        try (PreparedStatement statement = connection.prepareStatement(members)) {
            bindProjectIds(statement, projects);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) findProject(projects, result.getLong("project_id"))
                        .members.add(result.getLong("student_id"));
            }
        }
    }

    private void bindProjectIds(PreparedStatement statement, List<ProjectView> projects) throws SQLException {
        for (int i = 0; i < projects.size(); i++) statement.setLong(i + 1, projects.get(i).projectId);
    }

    private ProjectView findProject(List<ProjectView> projects, long projectId) throws SQLException {
        for (ProjectView project : projects) if (project.projectId == projectId) return project;
        throw new SQLException("A project interaction referenced an unloaded project.");
    }

    /** Uses importance-weighted requirements and returns one result per student/project pair. */
    private void buildRecommendations(List<ProjectView> projects, Map<Long, CandidateSkills> candidates) {
        for (ProjectView project : projects) {
            int requiredCount = project.requiredSkills.size();
            if (!project.canShowRecommendations() || requiredCount == 0) continue;
            List<Requirement> requirements = new ArrayList<>();
            for (Map.Entry<Long, String> required : project.requiredSkills.entrySet()) {
                requirements.add(new Requirement(required.getKey(), required.getValue(),
                        project.requiredImportance.getOrDefault(required.getKey(), 3)));
            }
            for (CandidateSkills candidate : candidates.values()) {
                if (project.members.contains(candidate.studentId)) continue;
                List<MatchedSkill> matchedSkills = new ArrayList<>();
                List<MissingSkill> missingSkills = new ArrayList<>();
                for (Map.Entry<Long, String> required : project.requiredSkills.entrySet()) {
                    StudentSkill skill = candidate.skills.get(required.getKey());
                    if (skill == null) {
                        missingSkills.add(new MissingSkill(required.getValue(),
                                project.requiredImportance.getOrDefault(required.getKey(), 3)));
                    } else {
                        matchedSkills.add(new MatchedSkill(required.getValue(), skill.getProficiencyName(),
                                project.requiredImportance.getOrDefault(required.getKey(), 3)));
                    }
                }
                if (matchedSkills.isEmpty()) continue;
                Map<Long, Integer> skillLevels = new LinkedHashMap<>();
                candidate.skills.forEach((id, skill) -> skillLevels.put(id, skill.proficiencyLevel));
                Profile profile = new Profile(candidate.studentId, candidate.fullName, candidate.department,
                        candidate.yearOfStudy, skillLevels);
                BigDecimal score = TeamIntelligenceService.matchPercent(requirements, profile);
                project.recommendations.add(new Recommendation(candidate, matchedSkills, missingSkills, score,
                        project.invitationStatuses.get(candidate.studentId),
                        project.activeRequests.contains(candidate.studentId)));
            }
            project.recommendations.sort((left, right) -> {
                int scoreOrder = right.score.compareTo(left.score);
                return scoreOrder != 0 ? scoreOrder
                        : left.getFullName().compareToIgnoreCase(right.getFullName());
            });
        }
    }

    /** Match_Results has a unique (student_id, project_id) key, so refresh current recommendations. */
    private void saveCurrentScores(Connection connection, List<ProjectView> projects) throws SQLException {
        String sql = "INSERT INTO Match_Results (student_id, project_id, matching_score) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE matching_score = VALUES(matching_score), "
                + "recommendation_date = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProjectView project : projects) {
                for (Recommendation recommendation : project.recommendations) {
                    statement.setLong(1, recommendation.candidate.studentId);
                    statement.setLong(2, project.projectId);
                    statement.setBigDecimal(3, recommendation.score);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    public static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static final class ProjectView {
        private final long projectId;
        private final String title, description, status, createdDate;
        private final long creatorId;
        private final int teamSize, occupiedSlots, pendingInvitations;
        private final Map<Long, String> requiredSkills = new LinkedHashMap<>();
        private final Map<Long, Integer> requiredImportance = new LinkedHashMap<>();
        private final Map<Long, String> invitationStatuses = new LinkedHashMap<>();
        private final Set<Long> activeRequests = new LinkedHashSet<>(), members = new LinkedHashSet<>();
        private final List<Recommendation> recommendations = new ArrayList<>();

        private ProjectView(long projectId, String title, String description, long creatorId, int teamSize,
                            String status, String createdDate, int occupiedSlots, int pendingInvitations) {
            this.projectId = projectId; this.title = title; this.description = description;
            this.creatorId = creatorId; this.teamSize = teamSize; this.status = status;
            this.createdDate = createdDate; this.occupiedSlots = occupiedSlots;
            this.pendingInvitations = pendingInvitations;
        }
        private void addRequiredSkill(long id, String name, int importance) {
            requiredSkills.putIfAbsent(id, name);
            requiredImportance.putIfAbsent(id, importance);
        }
        private void setInvitationStatus(long studentId, String status) { invitationStatuses.put(studentId, status); }
        private boolean canShowRecommendations() { return "OPEN".equals(status) && occupiedSlots < teamSize; }
        public long getProjectId() { return projectId; }
        public long getCreatorId() { return creatorId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public String getCreatedDate() { return createdDate; }
        public int getTeamSize() { return teamSize; }
        public int getOccupiedSlots() { return occupiedSlots; }
        public int getPendingInvitations() { return pendingInvitations; }
        public int getAvailableSeats() { return Math.max(0, teamSize - occupiedSlots - pendingInvitations); }
        public boolean canShowRecommendationsToOwner() { return canShowRecommendations(); }
        public boolean isEligible() { return canShowRecommendations() && getAvailableSeats() > 0; }
        public List<String> getRequiredSkills() { return new ArrayList<>(requiredSkills.values()); }
        public List<Recommendation> getRecommendations() { return recommendations; }
    }

    private static final class CandidateSkills {
        private final long studentId;
        private final String fullName, department;
        private final int yearOfStudy;
        private final Map<Long, StudentSkill> skills = new LinkedHashMap<>();
        private CandidateSkills(long studentId, String fullName, String department, int yearOfStudy) {
            this.studentId = studentId; this.fullName = fullName; this.department = department;
            this.yearOfStudy = yearOfStudy;
        }
        private void addSkill(long id, String name, int proficiencyLevel) {
            skills.putIfAbsent(id, new StudentSkill(name, proficiencyLevel));
        }
    }

    /** Existing proficiency records are bands 1-2, 3-4, and 5. */
    private static final class StudentSkill {
        private final String name;
        private final int proficiencyLevel;
        private StudentSkill(String name, int proficiencyLevel) { this.name = name; this.proficiencyLevel = proficiencyLevel; }
        private String getProficiencyName() {
            return TeamIntelligenceService.proficiencyLabel(proficiencyLevel);
        }
    }

    public static final class MatchedSkill {
        private final String name, proficiency;
        private final int importance;
        private MatchedSkill(String name, String proficiency, int importance) {
            this.name = name; this.proficiency = proficiency; this.importance = importance;
        }
        public String getName() { return name; }
        public String getProficiency() { return proficiency; }
        public int getImportance() { return importance; }
    }

    public static final class MissingSkill {
        private final String name;
        private final int importance;
        private MissingSkill(String name, int importance) { this.name = name; this.importance = importance; }
        public String getName() { return name; }
        public int getImportance() { return importance; }
    }

    public static final class Recommendation {
        private final CandidateSkills candidate;
        private final List<MatchedSkill> matchedSkills;
        private final List<MissingSkill> missingSkills;
        private final BigDecimal score;
        private final String invitationStatus;
        private final boolean activeRequest;
        private Recommendation(CandidateSkills candidate, List<MatchedSkill> matchedSkills,
                              List<MissingSkill> missingSkills, BigDecimal score, String invitationStatus,
                              boolean activeRequest) {
            this.candidate = candidate; this.matchedSkills = matchedSkills; this.missingSkills = missingSkills;
            this.score = score; this.invitationStatus = invitationStatus; this.activeRequest = activeRequest;
        }
        public long getStudentId() { return candidate.studentId; }
        public String getFullName() { return candidate.fullName; }
        public String getDepartment() { return candidate.department; }
        public int getYearOfStudy() { return candidate.yearOfStudy; }
        public List<MatchedSkill> getMatchedSkills() { return matchedSkills; }
        public List<MissingSkill> getMissingSkills() { return missingSkills; }
        public BigDecimal getScore() { return score; }
        public String getInvitationStatus() { return invitationStatus == null ? "" : invitationStatus; }
        public boolean hasActiveRequest() { return activeRequest; }
        public boolean isInvitationActive() {
            return "PENDING".equals(invitationStatus) || "ACCEPTED".equals(invitationStatus);
        }
    }
}

package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
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
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Creates and manages projects owned by the signed-in student. */
public class ProjectServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String PAGE = "/WEB-INF/projects.jsp";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) {
            return;
        }

        String status = request.getParameter("status");
        if ("created".equals(status)) {
            request.setAttribute("notice", "Your project has been created.");
        } else if ("updated".equals(status)) {
            request.setAttribute("notice", "Your project has been updated.");
        } else if ("deleted".equals(status)) {
            request.setAttribute("notice", "Your project has been deleted.");
        }
        TeamWorkflowServlet.applyMessage(request);
        showProjects(request, response, studentId, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) {
            return;
        }

        String action = request.getParameter("action");
        if ("create".equals(action)) {
            createProject(request, response, studentId);
        } else if ("update".equals(action)) {
            updateProject(request, response, studentId);
        } else if ("delete".equals(action)) {
            deleteProject(request, response, studentId);
        } else {
            showProjects(request, response, studentId, null, "Choose a valid project action.");
        }
    }

    /** The session, never a form field, supplies the owner ID for every operation. */
    private Long authenticatedStudentId(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("studentId");
        if (!(value instanceof Number)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }

        long studentId = ((Number) value).longValue();
        if (studentId <= 0) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        return studentId;
    }

    private void createProject(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        ProjectInput input = readProjectInput(request, false);
        if (input.error != null) {
            showProjects(request, response, studentId, null, input.error);
            return;
        }

        String insertProject = "INSERT INTO Projects "
                + "(title, description, creator_id, required_team_size, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validateSkillIds(connection, input.skillIds);
                long projectId;
                try (PreparedStatement statement = connection.prepareStatement(insertProject,
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, input.title);
                    statement.setString(2, input.description);
                    statement.setLong(3, studentId);
                    statement.setInt(4, input.teamSize);
                    statement.setString(5, input.status);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Project ID was not returned after insert.");
                        }
                        projectId = keys.getLong(1);
                    }
                }
                insertProjectSkills(connection, projectId, input.skillIds);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
            response.sendRedirect(request.getContextPath() + "/projects?status=created");
        } catch (SQLException exception) {
            handleSaveError(request, response, studentId, exception, "create");
        }
    }

    private void updateProject(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        long projectId = parsePositiveId(request.getParameter("project_id"));
        ProjectInput input = readProjectInput(request, true);
        if (projectId <= 0 || input.error != null) {
            showProjects(request, response, studentId, null,
                    projectId <= 0 ? "Choose a valid project to edit." : input.error);
            return;
        }

        boolean foundAndOwned = false;
        String ownerCheck = "SELECT project_id FROM Projects WHERE project_id = ? AND creator_id = ? AND status <> 'REMOVED' FOR UPDATE";
        String updateProject = "UPDATE Projects SET title = ?, description = ?, required_team_size = ?, status = ? "
                + "WHERE project_id = ? AND creator_id = ? AND status <> 'REMOVED'";
        String deleteSkills = "DELETE FROM Project_Skills WHERE project_id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(ownerCheck)) {
                    statement.setLong(1, projectId);
                    statement.setLong(2, studentId);
                    try (ResultSet result = statement.executeQuery()) {
                        foundAndOwned = result.next();
                    }
                }
                if (foundAndOwned) {
                    validateSkillIds(connection, input.skillIds);
                    try (PreparedStatement statement = connection.prepareStatement(updateProject)) {
                        statement.setString(1, input.title);
                        statement.setString(2, input.description);
                        statement.setInt(3, input.teamSize);
                        statement.setString(4, input.status);
                        statement.setLong(5, projectId);
                        statement.setLong(6, studentId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(deleteSkills)) {
                        statement.setLong(1, projectId);
                        statement.executeUpdate();
                    }
                    insertProjectSkills(connection, projectId, input.skillIds);
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            handleSaveError(request, response, studentId, exception, "update");
            return;
        }

        if (!foundAndOwned) {
            showProjects(request, response, studentId, null,
                    "That project was not found in your projects.");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/projects?status=updated");
    }

    private void deleteProject(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        long projectId = parsePositiveId(request.getParameter("project_id"));
        if (projectId <= 0) {
            showProjects(request, response, studentId, null, "Choose a valid project to delete.");
            return;
        }

        String sql = "DELETE FROM Projects WHERE project_id = ? AND creator_id = ? AND status <> 'REMOVED'";
        boolean deleted;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setLong(2, studentId);
            deleted = statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            getServletContext().log("Deleting a project failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            showProjects(request, response, studentId, null,
                    "The project could not be deleted. Please try again.");
            return;
        }
        if (!deleted) {
            showProjects(request, response, studentId, null,
                    "That project was not found in your projects.");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/projects?status=deleted");
    }

    private ProjectInput readProjectInput(HttpServletRequest request, boolean editing) {
        String title = trim(request.getParameter("title"));
        String description = trim(request.getParameter("description"));
        int teamSize = parseInt(request.getParameter("required_team_size"));
        String status = normalizeStatus(request.getParameter("status"), editing);
        List<Long> skillIds = parseSkillIds(request.getParameterValues("skill_ids"));

        if (title.isEmpty() || title.codePointCount(0, title.length()) > 180
                || containsControlCharacter(title)) {
            return ProjectInput.error("Enter a project title of up to 180 characters.");
        }
        if (description.isEmpty() || description.codePointCount(0, description.length()) > 5000
                || containsForbiddenDescriptionControl(description)) {
            return ProjectInput.error("Enter a description of up to 5,000 characters.");
        }
        if (teamSize < 2 || teamSize > 20) {
            return ProjectInput.error("Required team size must be between 2 and 20 students.");
        }
        if (status == null) {
            return ProjectInput.error("Choose a valid project status.");
        }
        if (skillIds.isEmpty() || skillIds.size() > 50) {
            return ProjectInput.error("Choose at least one required skill (up to 50 skills).");
        }
        return ProjectInput.valid(title, description, teamSize, status, skillIds);
    }

    private void validateSkillIds(Connection connection, List<Long> skillIds) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(skillIds.size(), "?"));
        String sql = "SELECT skill_id FROM Skills WHERE skill_id IN (" + placeholders + ")";
        Set<Long> found = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < skillIds.size(); index++) {
                statement.setLong(index + 1, skillIds.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    found.add(result.getLong("skill_id"));
                }
            }
        }
        if (found.size() != skillIds.size()) {
            throw new SQLException("One or more selected skills no longer exist.");
        }
    }

    private void insertProjectSkills(Connection connection, long projectId, List<Long> skillIds)
            throws SQLException {
        String sql = "INSERT INTO Project_Skills (project_id, skill_id, importance_level) VALUES (?, ?, 3)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Long skillId : skillIds) {
                statement.setLong(1, projectId);
                statement.setLong(2, skillId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void showProjects(HttpServletRequest request, HttpServletResponse response, long studentId,
                              String notice, String error)
            throws ServletException, IOException {
        if (notice != null) {
            request.setAttribute("notice", notice);
        }
        if (error != null) {
            request.setAttribute("error", error);
        }

        List<SkillOption> skills = new ArrayList<>();
        List<ProjectView> ownProjects = new ArrayList<>();
        List<ProjectView> openProjects = new ArrayList<>();
        ProjectFilters filters = readProjectFilters(request);
        String skillSql = "SELECT skill_id, skill_name FROM Skills ORDER BY skill_name";
        String ownerSql = projectListSql("p.creator_id = ?", filters);
        String openSql = projectListSql("p.status = 'OPEN' AND p.creator_id <> ?", filters);

        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(skillSql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    skills.add(new SkillOption(result.getLong("skill_id"), result.getString("skill_name")));
                }
            }
            loadProjectList(connection, ownerSql, studentId, filters, ownProjects);
            loadProjectList(connection, openSql, studentId, filters, openProjects);
            request.setAttribute("skillOptions", skills);
            request.setAttribute("ownProjects", ownProjects);
            request.setAttribute("openProjects", openProjects);
        } catch (SQLException exception) {
            getServletContext().log("Loading project data failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "Projects are temporarily unavailable. Please try again shortly.");
            request.setAttribute("skillOptions", skills);
            request.setAttribute("ownProjects", ownProjects);
            request.setAttribute("openProjects", openProjects);
        }
        request.setAttribute("projectStatusFilter", filters.status);
        request.setAttribute("projectSkillFilter", filters.skillId);
        request.setAttribute("projectSort", filters.sort);
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    /** Only allowlisted filter values enter SQL as bind parameters; sort direction is allowlisted SQL. */
    private ProjectFilters readProjectFilters(HttpServletRequest request) {
        // Project forms also submit a field named "status"; never treat POST form data as list filters.
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return new ProjectFilters("ALL", 0, "newest");
        }
        String status = trim(request.getParameter("status")).toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "CLOSED", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
            status = "ALL";
        }
        long skillId = parsePositiveId(request.getParameter("skill_id"));
        String sort = "oldest".equalsIgnoreCase(trim(request.getParameter("sort"))) ? "oldest" : "newest";
        return new ProjectFilters(status, skillId > 0 ? skillId : 0, sort);
    }

    private String projectListSql(String filter, ProjectFilters filters) {
        String order = "oldest".equals(filters.sort) ? "ASC" : "DESC";
        return "SELECT p.project_id, p.title, p.description, p.creator_id, p.required_team_size, "
                + "p.status, p.created_at, owner.full_name AS creator_name, "
                + "s.skill_id AS required_skill_id, s.skill_name AS required_skill_name "
                + "FROM Projects p JOIN Students owner ON owner.student_id = p.creator_id "
                + "LEFT JOIN Project_Skills ps ON ps.project_id = p.project_id "
                + "LEFT JOIN Skills s ON s.skill_id = ps.skill_id WHERE (" + filter + ") AND p.status <> 'REMOVED'"
                + ("ALL".equals(filters.status) ? "" : " AND p.status = ?")
                + (filters.skillId == 0 ? "" : " AND EXISTS (SELECT 1 FROM Project_Skills filter_ps "
                + "WHERE filter_ps.project_id = p.project_id AND filter_ps.skill_id = ?)")
                + " ORDER BY p.created_at " + order + ", p.project_id " + order + ", s.skill_name";
    }

    private void loadProjectList(Connection connection, String sql, long studentId,
                                 ProjectFilters filters, List<ProjectView> projects) throws SQLException {
        Map<Long, ProjectView> byId = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            statement.setLong(parameter++, studentId);
            if (!"ALL".equals(filters.status)) {
                statement.setString(parameter++, filters.status);
            }
            if (filters.skillId > 0) {
                statement.setLong(parameter, filters.skillId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long projectId = result.getLong("project_id");
                    ProjectView project = byId.get(projectId);
                    if (project == null) {
                        Timestamp createdAt = result.getTimestamp("created_at");
                        project = new ProjectView(projectId, result.getString("title"),
                                result.getString("description"), result.getLong("creator_id"),
                                result.getString("creator_name"), result.getInt("required_team_size"),
                                result.getString("status"),
                                createdAt == null ? "" : DATE_FORMAT.format(createdAt.toLocalDateTime()));
                        byId.put(projectId, project);
                    }
                    long skillId = result.getLong("required_skill_id");
                    if (!result.wasNull()) {
                        project.addSkill(skillId, result.getString("required_skill_name"));
                    }
                }
            }
        }
        projects.addAll(byId.values());
    }

    private void handleSaveError(HttpServletRequest request, HttpServletResponse response, long studentId,
                                 SQLException exception, String action)
            throws ServletException, IOException {
        if (exception.getErrorCode() == 1062) {
            showProjects(request, response, studentId, null,
                    "You already have a project with that title.");
            return;
        }
        getServletContext().log("Saving a project failed during " + action, exception);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        showProjects(request, response, studentId, null,
                "The project could not be saved. Please try again.");
    }

    private void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            original.addSuppressed(rollbackException);
        }
    }

    private String normalizeStatus(String value, boolean editing) {
        if (value == null) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(status) || "CLOSED".equals(status)) {
            return status;
        }
        // Preserve an existing legacy lifecycle status if an owner edits its other fields.
        if (editing && ("IN_PROGRESS".equals(status) || "COMPLETED".equals(status)
                || "CANCELLED".equals(status))) {
            return status;
        }
        return null;
    }

    private List<Long> parseSkillIds(String[] values) {
        Set<Long> uniqueIds = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            long skillId = parsePositiveId(value);
            if (skillId <= 0) {
                return new ArrayList<>();
            }
            uniqueIds.add(skillId);
            if (uniqueIds.size() > 50) {
                return new ArrayList<>(uniqueIds);
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    private long parsePositiveId(String value) {
        if (value == null) {
            return -1;
        }
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private int parseInt(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private boolean containsForbiddenDescriptionControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    private static final class ProjectInput {
        private final String title;
        private final String description;
        private final int teamSize;
        private final String status;
        private final List<Long> skillIds;
        private final String error;

        private ProjectInput(String title, String description, int teamSize, String status,
                             List<Long> skillIds, String error) {
            this.title = title;
            this.description = description;
            this.teamSize = teamSize;
            this.status = status;
            this.skillIds = skillIds;
            this.error = error;
        }

        private static ProjectInput error(String message) {
            return new ProjectInput("", "", 0, null, new ArrayList<>(), message);
        }

        private static ProjectInput valid(String title, String description, int teamSize,
                                          String status, List<Long> skillIds) {
            return new ProjectInput(title, description, teamSize, status, skillIds, null);
        }
    }

    private static final class ProjectFilters {
        private final String status;
        private final long skillId;
        private final String sort;

        private ProjectFilters(String status, long skillId, String sort) {
            this.status = status;
            this.skillId = skillId;
            this.sort = sort;
        }
    }

    public static final class SkillOption {
        private final long skillId;
        private final String skillName;

        private SkillOption(long skillId, String skillName) {
            this.skillId = skillId;
            this.skillName = skillName;
        }

        public long getSkillId() {
            return skillId;
        }

        public String getSkillName() {
            return skillName;
        }
    }

    public static final class ProjectSkill {
        private final long skillId;
        private final String skillName;

        private ProjectSkill(long skillId, String skillName) {
            this.skillId = skillId;
            this.skillName = skillName;
        }

        public long getSkillId() {
            return skillId;
        }

        public String getSkillName() {
            return skillName;
        }
    }

    public static final class ProjectView {
        private final long projectId;
        private final String title;
        private final String description;
        private final long creatorId;
        private final String creatorName;
        private final int teamSize;
        private final String status;
        private final String createdDate;
        private final Map<Long, ProjectSkill> skills = new LinkedHashMap<>();

        private ProjectView(long projectId, String title, String description, long creatorId,
                            String creatorName, int teamSize, String status, String createdDate) {
            this.projectId = projectId;
            this.title = title;
            this.description = description;
            this.creatorId = creatorId;
            this.creatorName = creatorName;
            this.teamSize = teamSize;
            this.status = status;
            this.createdDate = createdDate;
        }

        private void addSkill(long skillId, String skillName) {
            skills.putIfAbsent(skillId, new ProjectSkill(skillId, skillName));
        }

        public long getProjectId() {
            return projectId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public long getCreatorId() {
            return creatorId;
        }

        public String getCreatorName() {
            return creatorName;
        }

        public int getTeamSize() {
            return teamSize;
        }

        public String getStatus() {
            return status;
        }

        public String getCreatedDate() {
            return createdDate;
        }

        public List<ProjectSkill> getSkills() {
            return new ArrayList<>(skills.values());
        }

        public boolean hasSkill(long skillId) {
            return skills.containsKey(skillId);
        }
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

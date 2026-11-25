package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin dashboard and transactional student/project moderation endpoints. */
public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String CSRF_KEY = "adminCsrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!authorize(request, response)) return;
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        String token = (String) session.getAttribute(CSRF_KEY);
        if (token == null) {
            token = newCsrfToken();
            session.setAttribute(CSRF_KEY, token);
        }
        request.setAttribute("csrfToken", token);
        request.setAttribute("adminName", escape(session.getAttribute("studentName")));
        String path = adminPath(request);
        String view;
        String active;
        if ("/admin".equals(path) || "/admin/".equals(path)) {
            view = "/WEB-INF/admin-dashboard.jsp"; active = "dashboard";
        } else if ("/admin/students".equals(path)) {
            view = "/WEB-INF/admin-students.jsp"; active = "students";
            request.setAttribute("search", escape(normalizeSearch(request.getParameter("q"))));
        } else if ("/admin/projects".equals(path)) {
            view = "/WEB-INF/admin-projects.jsp"; active = "projects";
        } else if ("/admin/history".equals(path)) {
            view = "/WEB-INF/admin-history.jsp"; active = "history";
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            if ("dashboard".equals(active)) {
                showDashboard(request, connection);
            } else if ("students".equals(active)) {
                request.setAttribute("students", loadStudents(connection, normalizeSearch(request.getParameter("q"))));
            } else if ("projects".equals(active)) {
                request.setAttribute("projects", loadProjects(connection, false));
            } else {
                request.setAttribute("actions", loadHistory(connection, 0));
            }
        } catch (SQLException exception) {
            getServletContext().log("Loading admin information failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("adminError", "Admin information is temporarily unavailable. Please try again shortly.");
        }
        forward(request, response, view, active);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        AdminSecurity.Account admin;
        try {
            admin = AdminSecurity.verifyRequestAdministrator(request);
        } catch (SQLException exception) {
            getServletContext().log("Admin authorization check failed", exception);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (admin == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrator access is required.");
            return;
        }
        HttpSession session = request.getSession(false);
        if (!validCsrf(session, request.getParameter("csrf_token"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The form expired. Reload the page and try again.");
            return;
        }

        String action = trim(request.getParameter("action"));
        String target = "";
        try {
            if ("ban-student".equals(action)) {
                long studentId = positiveId(request.getParameter("student_id"));
                moderateStudent(admin.studentId, studentId, requiredReason(request.getParameter("reason")), true);
                target = "/admin/students?notice=banned";
            } else if ("unban-student".equals(action)) {
                moderateStudent(admin.studentId, positiveId(request.getParameter("student_id")), null, false);
                target = "/admin/students?notice=unbanned";
            } else if ("remove-project".equals(action)) {
                moderateProject(admin.studentId, positiveId(request.getParameter("project_id")),
                        requiredReason(request.getParameter("reason")), false);
                target = "/admin/projects?notice=removed";
            } else if ("restore-project".equals(action)) {
                moderateProject(admin.studentId, positiveId(request.getParameter("project_id")), null, true);
                target = "/admin/projects?notice=restored";
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a valid moderation action.");
                return;
            }
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            return;
        } catch (SQLException exception) {
            getServletContext().log("Admin moderation action failed", exception);
            response.sendError(HttpServletResponse.SC_CONFLICT,
                    "The moderation action could not be completed. Refresh the page and check the current status.");
            return;
        }
        response.sendRedirect(request.getContextPath() + target);
    }

    private boolean authorize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (AdminSecurity.verifyRequestAdministrator(request) != null) return true;
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrator access is required.");
        } catch (SQLException exception) {
            getServletContext().log("Admin authorization check failed", exception);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return false;
    }

    private void showDashboard(HttpServletRequest request, Connection connection) throws SQLException {
        request.setAttribute("totalStudents", scalar(connection, "SELECT COUNT(*) FROM Students"));
        request.setAttribute("activeProjects", scalar(connection,
                "SELECT COUNT(*) FROM Projects WHERE status IN ('OPEN', 'IN_PROGRESS')"));
        request.setAttribute("bannedStudents", scalar(connection,
                "SELECT COUNT(*) FROM Students WHERE is_suspended = TRUE"));
        request.setAttribute("totalTeams", scalar(connection, "SELECT COUNT(*) FROM Teams"));
        request.setAttribute("projectsWithTeams", scalar(connection, "SELECT COUNT(DISTINCT p.project_id) "
                + "FROM Projects p JOIN Teams t ON t.project_id = p.project_id "
                + "WHERE p.status IN ('OPEN', 'IN_PROGRESS') AND t.status IN ('FORMING', 'ACTIVE')"));
        request.setAttribute("recentStudents", loadRecentStudents(connection, 6));
        request.setAttribute("recentProjects", loadProjects(connection, true));
        request.setAttribute("recentActions", loadHistory(connection, 8));
        request.setAttribute("registrationTrend", loadRegistrationTrend(connection));
        request.setAttribute("projectStatusMetrics", loadProjectStatusMetrics(connection));
        request.setAttribute("popularSkills", loadPopularSkills(connection));
    }

    private List<MetricBar> loadRegistrationTrend(Connection connection) throws SQLException {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int offset = 6; offset >= 0; offset--) counts.put(today.minusDays(offset), 0L);
        String sql = "SELECT DATE(registered_at) AS registration_day, COUNT(*) AS student_count "
                + "FROM Students WHERE registered_at >= CURRENT_DATE - INTERVAL 6 DAY "
                + "GROUP BY DATE(registered_at) ORDER BY registration_day";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                java.sql.Date day = result.getDate("registration_day");
                if (day != null && counts.containsKey(day.toLocalDate())) counts.put(day.toLocalDate(), result.getLong("student_count"));
            }
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE");
        List<MetricBar> values = new ArrayList<>();
        counts.forEach((day, count) -> values.add(new MetricBar(format.format(day), count,
                max == 0 ? 0 : (int) Math.round(count * 100.0 / max))));
        return values;
    }

    private List<MetricBar> loadProjectStatusMetrics(Connection connection) throws SQLException {
        String sql = "SELECT status, COUNT(*) AS project_count FROM Projects GROUP BY status ORDER BY status";
        List<MetricBar> values = new ArrayList<>();
        long max = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long count = result.getLong("project_count"); max = Math.max(max, count);
                values.add(new MetricBar(result.getString("status").replace('_', ' '), count, 0));
            }
        }
        for (MetricBar value : values) value.setPercent(max == 0 ? 0 : (int) Math.round(value.value * 100.0 / max));
        return values;
    }

    private List<MetricBar> loadPopularSkills(Connection connection) throws SQLException {
        String sql = "SELECT sk.skill_name, COUNT(DISTINCT ss.student_id) AS student_count "
                + "FROM Student_Skills ss JOIN Skills sk ON sk.skill_id = ss.skill_id "
                + "GROUP BY sk.skill_id, sk.skill_name ORDER BY student_count DESC, sk.skill_name LIMIT 6";
        List<MetricBar> values = new ArrayList<>();
        long max = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long count = result.getLong("student_count"); max = Math.max(max, count);
                values.add(new MetricBar(escape(result.getString("skill_name")), count, 0));
            }
        }
        for (MetricBar value : values) value.setPercent(max == 0 ? 0 : (int) Math.round(value.value * 100.0 / max));
        return values;
    }

    private long scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private List<StudentRow> loadStudents(Connection connection, String search) throws SQLException {
        List<StudentRow> rows = new ArrayList<>();
        String sql = "SELECT student_id, full_name, email, department, year_of_study, registered_at, "
                + "is_suspended, suspension_reason FROM Students "
                + (search.isEmpty() ? "" : "WHERE full_name LIKE ? OR email LIKE ? OR department LIKE ? ")
                + "ORDER BY registered_at DESC, student_id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!search.isEmpty()) {
                String like = "%" + search + "%";
                statement.setString(1, like); statement.setString(2, like); statement.setString(3, like);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new StudentRow(result.getLong("student_id"),
                        escape(result.getString("full_name")), escape(result.getString("email")),
                        escape(result.getString("department")), result.getInt("year_of_study"),
                        date(result.getTimestamp("registered_at")), result.getBoolean("is_suspended"),
                        escape(result.getString("suspension_reason"))));
            }
        }
        return rows;
    }

    private List<StudentRow> loadRecentStudents(Connection connection, int limit) throws SQLException {
        String sql = "SELECT student_id, full_name, email, department, year_of_study, registered_at, "
                + "is_suspended, suspension_reason FROM Students ORDER BY registered_at DESC, student_id DESC LIMIT ?";
        List<StudentRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new StudentRow(result.getLong("student_id"),
                        escape(result.getString("full_name")), escape(result.getString("email")),
                        escape(result.getString("department")), result.getInt("year_of_study"),
                        date(result.getTimestamp("registered_at")), result.getBoolean("is_suspended"),
                        escape(result.getString("suspension_reason"))));
            }
        }
        return rows;
    }

    private List<ProjectRow> loadProjects(Connection connection, boolean recent) throws SQLException {
        String sql = "SELECT p.project_id, p.title, p.status, p.created_at, p.required_team_size, "
                + "p.removal_reason, creator.full_name AS creator_name, "
                + "t.team_name, t.status AS team_status, COUNT(tm.student_id) AS team_members "
                + "FROM Projects p JOIN Students creator ON creator.student_id = p.creator_id "
                + "LEFT JOIN Teams t ON t.project_id = p.project_id "
                + "LEFT JOIN Team_Members tm ON tm.team_id = t.team_id "
                + "GROUP BY p.project_id, p.title, p.status, p.created_at, p.required_team_size, "
                + "p.removal_reason, creator.full_name, t.team_id, t.team_name, t.status "
                + "ORDER BY p.created_at DESC, p.project_id DESC" + (recent ? " LIMIT 8" : "");
        List<ProjectRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) rows.add(new ProjectRow(result.getLong("project_id"),
                    escape(result.getString("title")), escape(result.getString("creator_name")),
                    result.getString("status"), date(result.getTimestamp("created_at")),
                    result.getInt("required_team_size"), escape(result.getString("team_name")),
                    escape(result.getString("team_status")), result.getInt("team_members"),
                    escape(result.getString("removal_reason"))));
        }
        return rows;
    }

    private List<AuditRow> loadHistory(Connection connection, int limit) throws SQLException {
        String sql = "SELECT a.audit_id, a.action_type, a.target_type, a.target_id, a.reason, a.created_at, "
                + "actor.full_name AS actor_name FROM Moderation_Audit_Log a "
                + "JOIN Students actor ON actor.student_id = a.actor_student_id "
                + "ORDER BY a.created_at DESC, a.audit_id DESC" + (limit > 0 ? " LIMIT ?" : "");
        List<AuditRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (limit > 0) statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new AuditRow(result.getLong("audit_id"),
                        result.getString("action_type"), result.getString("target_type"), result.getLong("target_id"),
                        escape(result.getString("reason")), date(result.getTimestamp("created_at")),
                        escape(result.getString("actor_name"))));
            }
        }
        return rows;
    }

    private void moderateStudent(long adminId, long studentId, String reason, boolean ban) throws SQLException {
        if (studentId <= 0) throw new IllegalArgumentException("Choose a valid student.");
        if (ban && studentId == adminId) throw new IllegalArgumentException("You cannot ban your own administrator account.");
        String lockSql = "SELECT is_suspended FROM Students WHERE student_id = ? FOR UPDATE";
        String updateSql = ban
                ? "UPDATE Students SET is_suspended = TRUE, suspension_reason = ?, suspended_at = CURRENT_TIMESTAMP, suspended_by = ? WHERE student_id = ?"
                : "UPDATE Students SET is_suspended = FALSE, suspension_reason = NULL, suspended_at = NULL, suspended_by = NULL WHERE student_id = ?";
        String action = ban ? "STUDENT_BAN" : "STUDENT_UNBAN";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean exists; boolean suspended;
                try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
                    statement.setLong(1, studentId);
                    try (ResultSet result = statement.executeQuery()) {
                        exists = result.next(); suspended = exists && result.getBoolean("is_suspended");
                    }
                }
                if (!exists) throw new IllegalArgumentException("That student account no longer exists.");
                if (ban == suspended) throw new IllegalArgumentException(ban ? "That student is already banned." : "That student is not banned.");
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    if (ban) {
                        statement.setString(1, reason); statement.setLong(2, adminId); statement.setLong(3, studentId);
                    } else statement.setLong(1, studentId);
                    statement.executeUpdate();
                }
                insertAudit(connection, adminId, action, "STUDENT", studentId, reason);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception); throw exception;
            }
        }
    }

    private void moderateProject(long adminId, long projectId, String reason, boolean restore) throws SQLException {
        if (projectId <= 0) throw new IllegalArgumentException("Choose a valid project.");
        String lockSql = "SELECT status FROM Projects WHERE project_id = ? FOR UPDATE";
        String updateSql = restore
                ? "UPDATE Projects SET status = COALESCE(moderation_previous_status, 'OPEN'), moderation_previous_status = NULL, removed_at = NULL, removed_by = NULL, removal_reason = NULL WHERE project_id = ? AND status = 'REMOVED'"
                : "UPDATE Projects SET moderation_previous_status = status, status = 'REMOVED', removed_at = CURRENT_TIMESTAMP, removed_by = ?, removal_reason = ? WHERE project_id = ? AND status <> 'REMOVED'";
        String action = restore ? "PROJECT_RESTORE" : "PROJECT_REMOVE";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status = null;
                try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
                    statement.setLong(1, projectId);
                    try (ResultSet result = statement.executeQuery()) { if (result.next()) status = result.getString("status"); }
                }
                if (status == null) throw new IllegalArgumentException("That project no longer exists.");
                if (restore != "REMOVED".equals(status)) {
                    throw new IllegalArgumentException(restore ? "That project is not removed." : "That project is already removed.");
                }
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    if (restore) statement.setLong(1, projectId);
                    else { statement.setLong(1, adminId); statement.setString(2, reason); statement.setLong(3, projectId); }
                    statement.executeUpdate();
                }
                insertAudit(connection, adminId, action, "PROJECT", projectId, reason);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception); throw exception;
            }
        }
    }

    private void insertAudit(Connection connection, long adminId, String action, String targetType,
                             long targetId, String reason) throws SQLException {
        String sql = "INSERT INTO Moderation_Audit_Log (actor_student_id, action_type, target_type, target_id, reason) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, adminId); statement.setString(2, action); statement.setString(3, targetType);
            statement.setLong(4, targetId); statement.setString(5, reason); statement.executeUpdate();
        }
    }

    private String requiredReason(String value) {
        String reason = trim(value);
        boolean hasControl = reason.codePoints().anyMatch(Character::isISOControl);
        if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 1000 || hasControl) {
            throw new IllegalArgumentException("Enter a moderation reason of 1 to 1,000 characters.");
        }
        return reason;
    }

    private String normalizeSearch(String value) {
        String search = trim(value);
        return search.length() > 100 ? search.substring(0, 100) : search;
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, String page, String active)
            throws ServletException, IOException {
        request.setAttribute("activeAdminPage", active);
        request.getRequestDispatcher(page).forward(request, response);
    }

    private String adminPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private boolean validCsrf(HttpSession session, String submitted) {
        Object expected = session == null ? null : session.getAttribute(CSRF_KEY);
        return expected instanceof String && submitted != null
                && MessageDigest.isEqual(((String) expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                submitted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String newCsrfToken() {
        byte[] token = new byte[32]; RANDOM.nextBytes(token); return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private long positiveId(String text) {
        try { long value = Long.parseLong(text); if (value > 0) return value; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Choose a valid record.");
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String date(Timestamp value) { return value == null ? "" : DATE_FORMAT.format(value.toLocalDateTime()); }
    private Object escape(Object value) { return value == null ? "" : escape(value.toString()); }
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String initialFor(String value) {
        if (value == null || value.trim().isEmpty()) return "S";
        return escapeHtml(value.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
    }

    private void rollback(Connection connection, Exception original) {
        try { connection.rollback(); } catch (SQLException rollback) { original.addSuppressed(rollback); }
    }

    public static final class StudentRow {
        private final long id; private final String name, email, department, registered, reason;
        private final int year; private final boolean banned;
        StudentRow(long id, String name, String email, String department, int year,
                   String registered, boolean banned, String reason) {
            this.id=id; this.name=name; this.email=email; this.department=department;
            this.year=year; this.registered=registered; this.banned=banned; this.reason=reason;
        }
        public long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;}
        public String getDepartment(){return department;} public int getYear(){return year;}
        public String getRegistered(){return registered;} public boolean isBanned(){return banned;} public String getReason(){return reason;}
    }
    public static final class ProjectRow {
        private final long id; private final String title, creator, status, created, teamName, teamStatus, removalReason;
        private final int capacity, members;
        ProjectRow(long id, String title, String creator, String status, String created, int capacity,
                   String teamName, String teamStatus, int members, String removalReason) {
            this.id=id; this.title=title; this.creator=creator; this.status=status; this.created=created;
            this.capacity=capacity; this.teamName=teamName; this.teamStatus=teamStatus; this.members=members;
            this.removalReason=removalReason;
        }
        public long getId(){return id;} public String getTitle(){return title;} public String getCreator(){return creator;}
        public String getStatus(){return status;} public String getCreated(){return created;} public int getCapacity(){return capacity;}
        public String getTeamName(){return teamName;} public String getTeamStatus(){return teamStatus;}
        public int getMembers(){return members;} public String getRemovalReason(){return removalReason;}
        public boolean isRemoved(){return "REMOVED".equals(status);}
    }
    public static final class AuditRow {
        private final long id, targetId; private final String action, targetType, reason, created, actor;
        AuditRow(long id, String action, String targetType, long targetId, String reason, String created, String actor) {
            this.id=id; this.action=action; this.targetType=targetType; this.targetId=targetId;
            this.reason=reason; this.created=created; this.actor=actor;
        }
        public long getId(){return id;} public long getTargetId(){return targetId;} public String getAction(){return action;}
        public String getTargetType(){return targetType;} public String getReason(){return reason;}
        public String getCreated(){return created;} public String getActor(){return actor;}
    }
    public static final class MetricBar {
        private final String label;
        private final long value;
        private int percent;
        MetricBar(String label, long value, int percent) { this.label=label; this.value=value; this.percent=percent; }
        public String getLabel(){return label;} public long getValue(){return value;} public int getPercent(){return percent;}
        void setPercent(int value){percent=Math.max(0,Math.min(100,value));}
    }
}

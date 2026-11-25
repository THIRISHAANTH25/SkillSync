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

/** Read-only global search over project listings and public student profile fields. */
public class GlobalSearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int RESULT_LIMIT = 6;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");

        HttpSession session = request.getSession(false);
        Object sessionId = session == null ? null : session.getAttribute("studentId");
        if (!(sessionId instanceof Number) || ((Number) sessionId).longValue() <= 0) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        long studentId = ((Number) sessionId).longValue();
        boolean administrator = Boolean.TRUE.equals(session.getAttribute("isAdmin"));
        String query = request.getParameter("q");
        query = query == null ? "" : query.trim();
        if (query.length() < 2 || query.length() > 100) {
            response.getWriter().write("{\"projects\":[],\"students\":[]}");
            return;
        }

        StringBuilder json = new StringBuilder(1024).append("{\"projects\":[");
        try (Connection connection = DatabaseConnection.getConnection()) {
            appendProjects(connection, studentId, administrator, query, json);
            json.append("],\"students\":[");
            appendStudents(connection, studentId, query, json);
            json.append("]}");
            response.getWriter().write(json.toString());
        } catch (SQLException exception) {
            getServletContext().log("Global search failed", exception);
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"error\":\"Search is temporarily unavailable.\"}");
        }
    }

    private void appendProjects(Connection connection, long studentId, boolean administrator,
                                String query, StringBuilder json) throws SQLException {
        String visibility = administrator ? "" : " AND (p.creator_id = ? OR p.status = 'OPEN')";
        String sql = "SELECT p.project_id, p.title, p.status, owner.full_name AS creator_name, "
                + "GROUP_CONCAT(DISTINCT skill.skill_name ORDER BY skill.skill_name SEPARATOR ', ') AS skill_names "
                + "FROM Projects p JOIN Students owner ON owner.student_id = p.creator_id "
                + "LEFT JOIN Project_Skills ps ON ps.project_id = p.project_id "
                + "LEFT JOIN Skills skill ON skill.skill_id = ps.skill_id "
                + "WHERE p.status <> 'REMOVED' AND (p.title LIKE ? OR p.description LIKE ? "
                + "OR owner.full_name LIKE ? OR EXISTS (SELECT 1 FROM Project_Skills search_ps "
                + "JOIN Skills search_skill ON search_skill.skill_id = search_ps.skill_id "
                + "WHERE search_ps.project_id = p.project_id AND search_skill.skill_name LIKE ?))"
                + visibility + " GROUP BY p.project_id, p.title, p.status, owner.full_name "
                + "ORDER BY p.created_at DESC, p.project_id DESC LIMIT " + RESULT_LIMIT;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            for (int index = 1; index <= 4; index++) statement.setString(index, pattern);
            if (!administrator) statement.setLong(5, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                boolean first = true;
                while (rows.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(rows.getLong("project_id"))
                            .append(",\"title\":").append(quote(rows.getString("title")))
                            .append(",\"status\":").append(quote(rows.getString("status")))
                            .append(",\"creator\":").append(quote(rows.getString("creator_name")))
                            .append(",\"skills\":").append(quote(rows.getString("skill_names"))).append('}');
                }
            }
        }
    }

    /** Email, credentials, suspension reasons, and moderation fields are never returned here. */
    private void appendStudents(Connection connection, long studentId, String query,
                                StringBuilder json) throws SQLException {
        String sql = "SELECT s.student_id, s.full_name, s.department, s.year_of_study, s.bio, "
                + "s.availability_status, "
                + "GROUP_CONCAT(DISTINCT skill.skill_name ORDER BY skill.skill_name SEPARATOR ', ') AS skill_names "
                + ", GROUP_CONCAT(DISTINCT interest.interest_name ORDER BY interest.interest_name SEPARATOR ', ') AS interest_names "
                + "FROM Students s LEFT JOIN Student_Skills ss ON ss.student_id = s.student_id "
                + "LEFT JOIN Skills skill ON skill.skill_id = ss.skill_id "
                + "LEFT JOIN Student_Interests si ON si.student_id = s.student_id "
                + "LEFT JOIN Interests interest ON interest.interest_id = si.interest_id "
                + "WHERE s.student_id <> ? AND s.is_suspended = FALSE AND "
                + "(s.full_name LIKE ? OR s.department LIKE ? OR COALESCE(s.bio, '') LIKE ? "
                + "OR COALESCE(s.availability_status, '') LIKE ? "
                + "OR EXISTS (SELECT 1 FROM Student_Skills search_ss JOIN Skills search_skill "
                + "ON search_skill.skill_id = search_ss.skill_id WHERE search_ss.student_id = s.student_id "
                + "AND search_skill.skill_name LIKE ?) "
                + "OR EXISTS (SELECT 1 FROM Student_Interests search_si JOIN Interests search_interest "
                + "ON search_interest.interest_id = search_si.interest_id WHERE search_si.student_id = s.student_id "
                + "AND search_interest.interest_name LIKE ?)) "
                + "GROUP BY s.student_id, s.full_name, s.department, s.year_of_study, s.bio, s.availability_status "
                + "ORDER BY s.full_name LIMIT " + RESULT_LIMIT;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            statement.setLong(1, studentId);
            for (int index = 2; index <= 7; index++) statement.setString(index, pattern);
            try (ResultSet rows = statement.executeQuery()) {
                boolean first = true;
                while (rows.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(rows.getLong("student_id"))
                            .append(",\"name\":").append(quote(rows.getString("full_name")))
                            .append(",\"department\":").append(quote(rows.getString("department")))
                            .append(",\"year\":").append(rows.getInt("year_of_study"))
                            .append(",\"bio\":").append(quote(rows.getString("bio")))
                            .append(",\"availability\":").append(quote(rows.getString("availability_status")))
                            .append(",\"skills\":").append(quote(rows.getString("skill_names")))
                            .append(",\"interests\":").append(quote(rows.getString("interest_names"))).append('}');
                }
            }
        }
    }

    /** Minimal JSON string encoder for database text; browser code also renders with textContent. */
    private String quote(String value) {
        if (value == null) return "null";
        StringBuilder output = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        return output.append('"').toString();
    }
}

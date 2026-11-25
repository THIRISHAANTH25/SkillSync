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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manages the signed-in student's skills and proficiency levels. */
public class SkillServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String PAGE = "/WEB-INF/skills.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) {
            return;
        }

        String status = request.getParameter("status");
        if ("added".equals(status)) {
            request.setAttribute("notice", "Skill added to your profile.");
        } else if ("updated".equals(status)) {
            request.setAttribute("notice", "Proficiency updated.");
        } else if ("deleted".equals(status)) {
            request.setAttribute("notice", "Skill removed from your profile.");
        }
        showSkills(request, response, studentId, null, null);
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
        if ("add".equals(action)) {
            addSkill(request, response, studentId);
        } else if ("update".equals(action)) {
            updateSkill(request, response, studentId);
        } else if ("delete".equals(action)) {
            deleteSkill(request, response, studentId);
        } else {
            showSkills(request, response, studentId, null, "Choose a valid skill action.");
        }
    }

    /** Reads the student ID only from the existing authenticated session. */
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

    private void addSkill(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        String skillName = request.getParameter("skill_name");
        String levelText = request.getParameter("proficiency");
        if (skillName == null) {
            skillName = "";
        }
        skillName = skillName.trim();

        Integer level = proficiencyValue(levelText);
        if (skillName.isEmpty() || skillName.codePointCount(0, skillName.length()) > 100
                || containsControlCharacter(skillName) || level == null) {
            showSkills(request, response, studentId, null,
                    "Enter a skill name of up to 100 characters and choose a proficiency level.");
            return;
        }

        String insertSkill = "INSERT INTO Skills (skill_name) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE skill_id = LAST_INSERT_ID(skill_id)";
        String findSkill = "SELECT skill_id FROM Skills WHERE skill_name = ?";
        String insertStudentSkill = "INSERT INTO Student_Skills "
                + "(student_id, skill_id, proficiency_level) VALUES (?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long skillId;
                try (PreparedStatement statement = connection.prepareStatement(insertSkill)) {
                    statement.setString(1, skillName);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(findSkill)) {
                    statement.setString(1, skillName);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Inserted skill could not be found.");
                        }
                        skillId = result.getLong("skill_id");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(insertStudentSkill)) {
                    statement.setLong(1, studentId);
                    statement.setLong(2, skillId);
                    statement.setInt(3, level);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
            response.sendRedirect(request.getContextPath() + "/skills?status=added");
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) {
                showSkills(request, response, studentId, null,
                        "That skill is already in your profile.");
            } else {
                getServletContext().log("Adding a student skill failed", exception);
                showSkills(request, response, studentId, null,
                        "The skill could not be saved. Please try again.");
            }
        }
    }

    private void updateSkill(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        long skillId = parsePositiveId(request.getParameter("skill_id"));
        Integer level = proficiencyValue(request.getParameter("proficiency"));
        if (skillId <= 0 || level == null) {
            showSkills(request, response, studentId, null,
                    "Choose a valid skill and proficiency level.");
            return;
        }

        String sql = "UPDATE Student_Skills SET proficiency_level = ? "
                + "WHERE student_id = ? AND skill_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, level);
            statement.setLong(2, studentId);
            statement.setLong(3, skillId);
            if (statement.executeUpdate() == 0) {
                showSkills(request, response, studentId, null,
                        "That skill was not found in your profile.");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/skills?status=updated");
        } catch (SQLException exception) {
            getServletContext().log("Updating a student skill failed", exception);
            showSkills(request, response, studentId, null,
                    "The proficiency could not be updated. Please try again.");
        }
    }

    private void deleteSkill(HttpServletRequest request, HttpServletResponse response, long studentId)
            throws ServletException, IOException {
        long skillId = parsePositiveId(request.getParameter("skill_id"));
        if (skillId <= 0) {
            showSkills(request, response, studentId, null, "Choose a valid skill to remove.");
            return;
        }

        String sql = "DELETE FROM Student_Skills WHERE student_id = ? AND skill_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, skillId);
            if (statement.executeUpdate() == 0) {
                showSkills(request, response, studentId, null,
                        "That skill was not found in your profile.");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/skills?status=deleted");
        } catch (SQLException exception) {
            getServletContext().log("Deleting a student skill failed", exception);
            showSkills(request, response, studentId, null,
                    "The skill could not be removed. Please try again.");
        }
    }

    private void showSkills(HttpServletRequest request, HttpServletResponse response, long studentId,
                            String notice, String error)
            throws ServletException, IOException {
        if (notice != null) {
            request.setAttribute("notice", notice);
        }
        if (error != null) {
            request.setAttribute("error", error);
        }

        List<SkillRow> skills = new ArrayList<>();
        String sql = "SELECT s.skill_id, s.skill_name, ss.proficiency_level "
                + "FROM Student_Skills ss JOIN Skills s ON s.skill_id = ss.skill_id "
                + "WHERE ss.student_id = ? ORDER BY s.skill_name";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    skills.add(new SkillRow(result.getLong("skill_id"),
                            result.getString("skill_name"), result.getInt("proficiency_level")));
                }
            }
            request.setAttribute("skills", skills);
        } catch (SQLException exception) {
            getServletContext().log("Loading student skills failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("skills", skills);
            request.setAttribute("error", "Your skills could not be loaded. Please try again shortly.");
        }
        request.getRequestDispatcher(PAGE).forward(request, response);
    }

    private Integer proficiencyValue(String text) {
        if (text == null) {
            return null;
        }
        switch (text.toLowerCase(Locale.ROOT)) {
            case "beginner":
                return 1;
            case "intermediate":
                return 3;
            case "advanced":
                return 5;
            default:
                return null;
        }
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

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    /** Small view model for rendering the student's own rows in the JSP. */
    public static final class SkillRow {
        private final long skillId;
        private final String skillName;
        private final int proficiencyLevel;

        public SkillRow(long skillId, String skillName, int proficiencyLevel) {
            this.skillId = skillId;
            this.skillName = skillName;
            this.proficiencyLevel = proficiencyLevel;
        }

        public long getSkillId() {
            return skillId;
        }

        public String getSkillName() {
            return skillName;
        }

        public int getProficiencyLevel() {
            return proficiencyLevel;
        }

        public String getProficiencyLabel() {
            if (proficiencyLevel <= 2) {
                return "Beginner";
            }
            if (proficiencyLevel <= 4) {
                return "Intermediate";
            }
            return "Advanced";
        }
    }

    /** Escapes dynamic skill names before they are written into the JSP response. */
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

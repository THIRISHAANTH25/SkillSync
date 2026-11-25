package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Handles student registration, login, and logout. */
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String PROFILE_CSRF_KEY = AuthServlet.class.getName() + ".profileCsrf";
    private static final SecureRandom CSRF_RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getServletPath();
        if ("/logout".equals(action)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/login?loggedOut=1");
            return;
        }
        if ("/account".equals(action)) {
            showStudentDashboard(request, response);
            return;
        }

        if ("/login".equals(action) && "1".equals(request.getParameter("suspended"))) {
            request.setAttribute("message", "Your account has been suspended. Contact the administrator for assistance.");
        }
        request.getRequestDispatcher("/WEB-INF" + action + ".jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if ("/register".equals(request.getServletPath())) {
            register(request, response);
        } else if ("/account".equals(request.getServletPath())) {
            updateProfile(request, response);
        } else {
            login(request, response);
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String name = value(request, "full_name");
        String email = value(request, "email").toLowerCase(Locale.ROOT);
        String password = request.getParameter("password");
        String department = value(request, "department");
        String yearText = value(request, "year_of_study");

        String configuredAdmin = AdminSecurity.configuredAdminEmail();
        if (!configuredAdmin.isEmpty() && configuredAdmin.equals(email)) {
            show(request, response, "/register", "This email is reserved for the configured administrator account.");
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearText);
        } catch (NumberFormatException exception) {
            year = -1;
        }
        if (name.isEmpty() || name.length() > 160 || department.isEmpty()
                || department.length() > 120 || email.length() > 254
                || !EMAIL_PATTERN.matcher(email).matches()
                || password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || year < 1 || year > 8) {
            show(request, response, "/register", "Please complete every field. Use a valid email, a password of 8–72 UTF-8 bytes, and a study year from 1 to 8.");
            return;
        }

        String sql = "INSERT INTO Students (full_name, email, password_hash, department, year_of_study) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, BCrypt.hashpw(password, BCrypt.gensalt(12)));
            statement.setString(4, department);
            statement.setInt(5, year);
            statement.executeUpdate();
            response.sendRedirect(request.getContextPath() + "/login?registered=1");
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) {
                show(request, response, "/register", "An account with that email address already exists.");
            } else {
                getServletContext().log("Student registration failed", exception);
                show(request, response, "/register", "Registration is temporarily unavailable. Please try again.");
            }
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = value(request, "email").toLowerCase(Locale.ROOT);
        String password = request.getParameter("password");
        if (email.isEmpty() || email.length() > 254 || password == null || password.isEmpty()) {
            show(request, response, "/login", "Enter your email address and password.");
            return;
        }

        String sql = "SELECT student_id, full_name, password_hash, is_suspended FROM Students WHERE email = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    show(request, response, "/login", "Email or password is incorrect.");
                    return;
                }
                String storedHash = result.getString("password_hash");
                boolean valid;
                try {
                    valid = storedHash != null && storedHash.startsWith("$2")
                            && password.getBytes(StandardCharsets.UTF_8).length <= 72
                            && BCrypt.checkpw(password, storedHash);
                } catch (IllegalArgumentException exception) {
                    valid = false;
                }
                if (!valid) {
                    show(request, response, "/login", "Email or password is incorrect.");
                    return;
                }

                if (result.getBoolean("is_suspended")) {
                    show(request, response, "/login", "Your account has been suspended. Contact the administrator for assistance.");
                    return;
                }

                long studentId = result.getLong("student_id");
                String fullName = result.getString("full_name");
                HttpSession previous = request.getSession(false);
                if (previous != null) {
                    previous.invalidate();
                }
                HttpSession session = request.getSession(true);
                request.changeSessionId();
                session.setAttribute("studentId", studentId);
                session.setAttribute("studentName", fullName);
                session.setMaxInactiveInterval(30 * 60);
                boolean isAdmin = !AdminSecurity.configuredAdminEmail().isEmpty()
                        && AdminSecurity.configuredAdminEmail().equals(email);
                response.sendRedirect(request.getContextPath() + (isAdmin ? "/admin" : "/account"));
            }
        } catch (SQLException exception) {
            getServletContext().log("Student login failed", exception);
            show(request, response, "/login", "Login is temporarily unavailable. Please try again.");
        }
    }

    /** Loads the signed-in student's current profile before showing the protected dashboard. */
    private void showStudentDashboard(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute("studentId");
        if (!(sessionStudentId instanceof Number)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        long studentId = ((Number) sessionStudentId).longValue();
        if (studentId <= 0) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection();
             ) {
            StudentDashboardService.DashboardData data = new StudentDashboardService().load(connection, studentId);
            request.setAttribute("dashboard", data);
            request.setAttribute("studentName", data.getName());
            request.setAttribute("studentEmail", data.getEmail());
            request.setAttribute("studentDepartment", data.getDepartment());
            request.setAttribute("studentYear", data.getYear());
            request.setAttribute("studentId", studentId);
            request.setAttribute("profileCsrfToken", issueProfileCsrf(session));
            if ("updated".equals(request.getParameter("profile"))) {
                request.setAttribute("profileNotice", "Your profile has been updated.");
            }
        } catch (SQLException exception) {
            getServletContext().log("Student dashboard data lookup failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("dashboardError",
                    "Your dashboard is temporarily unavailable. Confirm that the Phase 1 profile migration has been applied, then try again.");
            request.getRequestDispatcher("/WEB-INF/account.jsp").forward(request, response);
            return;
        }
        request.getRequestDispatcher("/WEB-INF/account.jsp").forward(request, response);
    }

    private void updateProfile(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute("studentId");
        if (!(sessionStudentId instanceof Number) || ((Number) sessionStudentId).longValue() <= 0) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (!validProfileCsrf(session, request.getParameter("csrf_token"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The profile form expired. Reload and try again.");
            return;
        }

        long studentId = ((Number) sessionStudentId).longValue();
        String name = value(request, "full_name");
        String department = value(request, "department");
        String bio = value(request, "bio");
        String availability = value(request, "availability_status");
        int year;
        try { year = Integer.parseInt(value(request, "year_of_study")); }
        catch (NumberFormatException exception) { year = -1; }

        Set<Long> interests = new LinkedHashSet<>();
        String[] submittedInterests = request.getParameterValues("interest_ids");
        boolean invalidInterest = false;
        if (submittedInterests != null) {
            if (submittedInterests.length > 6) invalidInterest = true;
            for (String submitted : submittedInterests) {
                try {
                    long id = Long.parseLong(submitted);
                    if (id <= 0) invalidInterest = true;
                    interests.add(id);
                } catch (NumberFormatException exception) { invalidInterest = true; }
            }
        }
        if (name.isEmpty() || name.length() > 160 || department.isEmpty() || department.length() > 120
                || year < 1 || year > 8 || bio.length() > 1000 || invalidInterest
                || (!availability.isEmpty() && !Set.of("AVAILABLE", "LIMITED", "UNAVAILABLE").contains(availability))) {
            request.setAttribute("profileError", "Check the profile fields: name and department are required, year must be 1–8, bio up to 1,000 characters, and up to 6 interests may be selected.");
            showStudentDashboard(request, response);
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!interests.isEmpty()) {
                    String placeholders = String.join(",", java.util.Collections.nCopies(interests.size(), "?"));
                    try (PreparedStatement check = connection.prepareStatement(
                            "SELECT COUNT(*) FROM Interests WHERE interest_id IN (" + placeholders + ")")) {
                        int index = 1;
                        for (Long id : interests) check.setLong(index++, id);
                        try (ResultSet result = check.executeQuery()) {
                            if (!result.next() || result.getInt(1) != interests.size()) {
                                throw new IllegalArgumentException("Choose interests from the list provided.");
                            }
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE Students SET full_name = ?, department = ?, "
                        + "year_of_study = ?, bio = ?, availability_status = ? WHERE student_id = ?")) {
                    update.setString(1, name); update.setString(2, department); update.setInt(3, year);
                    update.setString(4, bio.isBlank() ? null : bio); update.setString(5, availability.isBlank() ? null : availability);
                    update.setLong(6, studentId);
                    if (update.executeUpdate() != 1) throw new SQLException("The student profile was not updated.");
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM Student_Interests WHERE student_id = ?")) {
                    delete.setLong(1, studentId); delete.executeUpdate();
                }
                if (!interests.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO Student_Interests (student_id, interest_id) VALUES (?, ?)")) {
                        for (Long interestId : interests) {
                            insert.setLong(1, studentId); insert.setLong(2, interestId); insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
            session.setAttribute("studentName", name);
            response.sendRedirect(request.getContextPath() + "/account?profile=updated");
        } catch (IllegalArgumentException exception) {
            request.setAttribute("profileError", exception.getMessage());
            showStudentDashboard(request, response);
        } catch (SQLException exception) {
            getServletContext().log("Student profile update failed", exception);
            request.setAttribute("profileError", "Your profile could not be saved. Please try again.");
            showStudentDashboard(request, response);
        }
    }

    private String issueProfileCsrf(HttpSession session) {
        Object token = session.getAttribute(PROFILE_CSRF_KEY);
        if (token instanceof String) return (String) token;
        byte[] bytes = new byte[32]; CSRF_RANDOM.nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(PROFILE_CSRF_KEY, generated);
        return generated;
    }

    private boolean validProfileCsrf(HttpSession session, String submitted) {
        Object expected = session == null ? null : session.getAttribute(PROFILE_CSRF_KEY);
        return expected instanceof String && submitted != null
                && MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }

    /** Escapes the characters that have meaning in HTML text and attribute values. */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void show(HttpServletRequest request, HttpServletResponse response,
                      String page, String message) throws ServletException, IOException {
        request.setAttribute("message", message);
        request.getRequestDispatcher("/WEB-INF" + page + ".jsp").forward(request, response);
    }

    private String value(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }
}

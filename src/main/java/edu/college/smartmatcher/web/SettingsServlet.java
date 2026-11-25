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

/** Serves appearance settings and securely changes the signed-in student's password. */
public class SettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String CSRF_ATTRIBUTE = SettingsServlet.class.getName() + ".csrf";
    private static final String FLASH_SUCCESS = SettingsServlet.class.getName() + ".success";
    private static final String FLASH_ERROR = SettingsServlet.class.getName() + ".error";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Long studentId = studentId(session);
        if (studentId == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String csrfToken = (String) session.getAttribute(CSRF_ATTRIBUTE);
        if (csrfToken == null) {
            csrfToken = newCsrfToken();
            session.setAttribute(CSRF_ATTRIBUTE, csrfToken);
        }
        request.setAttribute("csrfToken", csrfToken);
        request.setAttribute("settingsSuccess", session.getAttribute(FLASH_SUCCESS));
        request.setAttribute("settingsError", session.getAttribute(FLASH_ERROR));
        session.removeAttribute(FLASH_SUCCESS);
        session.removeAttribute(FLASH_ERROR);
        request.getRequestDispatcher("/WEB-INF/settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Long studentId = studentId(session);
        if (studentId == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (!validCsrfToken(session, request.getParameter("csrf_token"))) {
            flash(session, null, "This form expired or could not be verified. Reload Settings and try again.");
            redirectToSettings(request, response);
            return;
        }

        String currentPassword = request.getParameter("current_password");
        String newPassword = request.getParameter("new_password");
        String confirmPassword = request.getParameter("confirm_password");
        if (currentPassword == null || currentPassword.isEmpty()
                || newPassword == null || confirmPassword == null) {
            fail(request, response, session, "Complete all password fields.");
            return;
        }
        int newPasswordBytes = newPassword.getBytes(StandardCharsets.UTF_8).length;
        if (newPassword.length() < 8 || newPasswordBytes > 72) {
            fail(request, response, session, "Use a new password of at least 8 characters and no more than 72 UTF-8 bytes.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            fail(request, response, session, "The new password and confirmation do not match.");
            return;
        }
        if (newPassword.equals(currentPassword)) {
            fail(request, response, session, "Choose a new password that differs from your current password.");
            return;
        }
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            fail(request, response, session, "The current password is incorrect.");
            return;
        }

        changePassword(request, response, session, studentId, currentPassword, newPassword);
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response,
                                HttpSession session, long studentId,
                                String currentPassword, String newPassword)
            throws IOException {
        String selectSql = "SELECT password_hash FROM Students WHERE student_id = ? FOR UPDATE";
        String updateSql = "UPDATE Students SET password_hash = ? WHERE student_id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                select.setLong(1, studentId);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        session.invalidate();
                        response.sendRedirect(request.getContextPath() + "/login");
                        return;
                    }
                    String storedHash = result.getString("password_hash");
                    boolean matches;
                    try {
                        matches = storedHash != null && storedHash.startsWith("$2")
                                && BCrypt.checkpw(currentPassword, storedHash);
                    } catch (IllegalArgumentException invalidHash) {
                        matches = false;
                    }
                    if (!matches) {
                        connection.rollback();
                        fail(request, response, session, "The current password is incorrect.");
                        return;
                    }
                }
            }

            String passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, passwordHash);
                update.setLong(2, studentId);
                if (update.executeUpdate() != 1) {
                    connection.rollback();
                    fail(request, response, session, "The password could not be updated. Please try again.");
                    return;
                }
            }
            connection.commit();
            request.changeSessionId();
            session.setAttribute(CSRF_ATTRIBUTE, newCsrfToken());
            flash(session, "Your password has been changed successfully.", null);
            redirectToSettings(request, response);
        } catch (SQLException | RuntimeException exception) {
            getServletContext().log("Settings password update failed.", exception);
            flash(session, null, "Password change is temporarily unavailable. Please try again.");
            redirectToSettings(request, response);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response,
                      HttpSession session, String message) throws IOException {
        flash(session, null, message);
        redirectToSettings(request, response);
    }

    private void flash(HttpSession session, String success, String error) {
        if (success != null) session.setAttribute(FLASH_SUCCESS, success);
        else session.removeAttribute(FLASH_SUCCESS);
        if (error != null) session.setAttribute(FLASH_ERROR, error);
        else session.removeAttribute(FLASH_ERROR);
    }

    private void redirectToSettings(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/settings");
    }

    private Long studentId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute("studentId");
        if (!(value instanceof Number)) return null;
        long id = ((Number) value).longValue();
        return id > 0 ? id : null;
    }

    private boolean validCsrfToken(HttpSession session, String submitted) {
        Object expected = session.getAttribute(CSRF_ATTRIBUTE);
        if (!(expected instanceof String) || submitted == null || submitted.isEmpty()) return false;
        return MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.US_ASCII),
                submitted.getBytes(StandardCharsets.US_ASCII));
    }

    private String newCsrfToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}

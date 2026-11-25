package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Server-side admin identity is derived only from ADMIN_EMAIL and the database account. */
final class AdminSecurity {
    private AdminSecurity() { }

    static String configuredAdminEmail() {
        String value = System.getenv("ADMIN_EMAIL");
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static Account loadAccount(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT student_id, full_name, email, is_suspended FROM Students WHERE student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new Account(result.getLong("student_id"), result.getString("full_name"),
                        result.getString("email"), result.getBoolean("is_suspended"));
            }
        }
    }

    static boolean isAdministrator(Account account) {
        String adminEmail = configuredAdminEmail();
        return account != null && !account.suspended && !adminEmail.isEmpty()
                && adminEmail.equals(account.email.trim().toLowerCase(Locale.ROOT));
    }

    static Account verifyRequestAdministrator(jakarta.servlet.http.HttpServletRequest request)
            throws SQLException {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        Object rawId = session == null ? null : session.getAttribute("studentId");
        if (!(rawId instanceof Number) || configuredAdminEmail().isEmpty()) return null;
        try (Connection connection = DatabaseConnection.getConnection()) {
            Account account = loadAccount(connection, ((Number) rawId).longValue());
            return isAdministrator(account) ? account : null;
        }
    }

    static final class Account {
        final long studentId;
        final String fullName;
        final String email;
        final boolean suspended;
        Account(long studentId, String fullName, String email, boolean suspended) {
            this.studentId = studentId;
            this.fullName = fullName;
            this.email = email;
            this.suspended = suspended;
        }
    }
}

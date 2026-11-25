
package edu.college.smartmatcher.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/smart_project_matcher?serverTimezone=UTC";

    private DatabaseConnection() {
        // Utility class
    }

    public static Connection getConnection() throws SQLException {
        String url = environmentOrDefault("DB_URL", DEFAULT_URL);
        String user = requiredEnvironment("DB_USER");
        String password = requiredEnvironment("DB_PASSWORD");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found", e);
        }

        return DriverManager.getConnection(url, user, password);
    }

    private static String requiredEnvironment(String name)
            throws SQLException {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new SQLException(
                    "Required database environment variable is not set: "
                            + name);
        }

        return value;
    }

    private static String environmentOrDefault(
            String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank()
                ? defaultValue : value;
    }
}

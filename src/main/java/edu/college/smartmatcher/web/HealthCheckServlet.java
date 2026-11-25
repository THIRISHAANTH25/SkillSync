package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/** A small endpoint to confirm that the web app can reach its configured database. */
@WebServlet(urlPatterns = "/health", loadOnStartup = 1)
public class HealthCheckServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        // A successful connection is enough for this initial health check.
        try (Connection ignored = DatabaseConnection.getConnection()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("OK - application and database are reachable");
        } catch (SQLException exception) {
            // Keep details in the server log; never send connection details to the browser.
            getServletContext().log("Database health check failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().println("UNAVAILABLE - database connection failed");
        }
    }
}

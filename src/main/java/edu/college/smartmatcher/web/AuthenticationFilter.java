package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/** Revalidates every protected session against current suspension and admin configuration. */
public class AuthenticationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("studentId");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        AdminSecurity.Account account;
        try (Connection connection = DatabaseConnection.getConnection()) {
            account = AdminSecurity.loadAccount(connection, ((Number) value).longValue());
        } catch (SQLException exception) {
            request.getServletContext().log("Protected request account check failed", exception);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Account verification is temporarily unavailable.");
            return;
        }
        if (account == null) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (account.suspended) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?suspended=1");
            return;
        }

        boolean isAdmin = AdminSecurity.isAdministrator(account);
        session.setAttribute("studentName", account.fullName);
        session.setAttribute("isAdmin", isAdmin);
        if (isAdminPath(request) && !isAdmin) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrator access is required.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "/admin".equals(path) || path.startsWith("/admin/");
    }
}

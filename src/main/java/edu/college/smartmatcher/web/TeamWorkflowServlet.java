package edu.college.smartmatcher.web;

import edu.college.smartmatcher.config.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Handles authenticated join requests, invitations, and project-owner team selection. */
public class TeamWorkflowServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final String REQUESTS_PAGE = "/WEB-INF/my-requests.jsp";
    private static final String INVITATIONS_PAGE = "/WEB-INF/invitations.jsp";
    private static final String MANAGEMENT_PAGE = "/WEB-INF/team-management.jsp";
    private static final String TEAMS_PAGE = "/WEB-INF/my-teams.jsp";
    private static final String INVITE_CSRF_KEY = TeamWorkflowServlet.class.getName() + ".inviteCsrf";
    private static final SecureRandom CSRF_RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) return;
        String path = request.getServletPath();
        if ("/join".equals(path)) {
            response.sendRedirect(request.getContextPath() + "/projects");
            return;
        }
        applyMessage(request);
        try (Connection connection = DatabaseConnection.getConnection()) {
            if ("/my-requests".equals(path)) {
                request.setAttribute("requests", loadRequests(connection, studentId));
                request.getRequestDispatcher(REQUESTS_PAGE).forward(request, response);
            } else if ("/invitations".equals(path)) {
                request.setAttribute("invitations", loadInvitations(connection, studentId));
                request.getRequestDispatcher(INVITATIONS_PAGE).forward(request, response);
            } else if ("/team-management".equals(path)) {
                request.setAttribute("projects", loadLeaderProjects(connection, studentId));
                request.getRequestDispatcher(MANAGEMENT_PAGE).forward(request, response);
            } else if ("/my-teams".equals(path)) {
                request.setAttribute("teams", loadStudentTeams(connection, studentId));
                request.getRequestDispatcher(TEAMS_PAGE).forward(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException exception) {
            getServletContext().log("Loading team workflow data failed", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "Team information is temporarily unavailable. Please try again shortly.");
            String page = "/my-requests".equals(path) ? REQUESTS_PAGE
                    : "/invitations".equals(path) ? INVITATIONS_PAGE
                    : "/team-management".equals(path) ? MANAGEMENT_PAGE : TEAMS_PAGE;
            request.getRequestDispatcher(page).forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        Long studentId = authenticatedStudentId(request, response);
        if (studentId == null) return;

        String path = request.getServletPath();
        if ("/invitations".equals(path) && "invite".equals(request.getParameter("action"))
                && !validInviteCsrfToken(request.getSession(false), request.getParameter("csrf_token"))) {
            redirectWith(response, request, path, "invalid-csrf");
            return;
        }
        String result = "updated";
        try (Connection connection = DatabaseConnection.getConnection()) {
            try {
                if ("/join".equals(path)) {
                    requestProjectMembership(connection, studentId, positiveId(request.getParameter("project_id")),
                            request.getParameter("message"));
                    result = "requested";
                } else if ("/my-requests".equals(path)) {
                    withdrawRequest(connection, studentId, positiveId(request.getParameter("application_id")));
                    result = "withdrawn";
                } else if ("/invitations".equals(path)) {
                    if ("invite".equals(request.getParameter("action"))) {
                        createInvitation(connection, studentId, positiveId(request.getParameter("project_id")),
                                positiveId(request.getParameter("student_id")), request.getParameter("message"));
                        result = "invited";
                    } else {
                        respondToInvitation(connection, studentId,
                                positiveId(request.getParameter("invitation_id")), request.getParameter("decision"));
                        result = "accepted".equals(request.getParameter("decision")) ? "joined" : "declined";
                    }
                } else if ("/team-management".equals(path)) {
                    manageApplication(connection, studentId, positiveId(request.getParameter("application_id")),
                            request.getParameter("decision"));
                    result = "shortlisted".equals(request.getParameter("decision")) ? "shortlisted" :
                            "accepted".equals(request.getParameter("decision")) ? "member-added" : "rejected";
                } else {
                    redirectWith(response, request, "/projects", "invalid");
                    return;
                }
                connection.commit();
            } catch (WorkflowException exception) {
                rollbackQuietly(connection, exception);
                redirectWith(response, request, path, exception.key);
                return;
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            getServletContext().log("Saving team workflow change failed", exception);
            redirectWith(response, request, path, "failed");
            return;
        }
        redirectWith(response, request, path, result);
    }

    private Long authenticatedStudentId(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("studentId");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0) {
            if (session != null && value instanceof Number) session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        return ((Number) value).longValue();
    }

    private void requestProjectMembership(Connection connection, long studentId, long projectId, String rawMessage)
            throws SQLException, WorkflowException {
        String message = validateMessage(rawMessage);
        beginTransaction(connection);
        ProjectState project = lockProject(connection, projectId);
        requireOpen(project);
        if (project.creatorId == studentId) throw new WorkflowException("own-project");
        if (isProjectMember(connection, projectId, studentId)) throw new WorkflowException("already-member");
        if (hasPendingInvitation(connection, projectId, studentId)) throw new WorkflowException("has-invitation");

        String findSql = "SELECT application_id, status FROM Project_Applications "
                + "WHERE project_id = ? AND student_id = ? FOR UPDATE";
        Long applicationId = null;
        String status = null;
        try (PreparedStatement statement = connection.prepareStatement(findSql)) {
            statement.setLong(1, projectId);
            statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) { applicationId = result.getLong("application_id"); status = result.getString("status"); }
            }
        }
        if ("PENDING".equals(status) || "SHORTLISTED".equals(status) || "ACCEPTED".equals(status)) {
            throw new WorkflowException("already-requested");
        }
        if (applicationId == null) {
            String insert = "INSERT INTO Project_Applications (student_id, project_id, request_message, status) "
                    + "VALUES (?, ?, ?, 'PENDING')";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setLong(1, studentId); statement.setLong(2, projectId); statement.setString(3, message);
                statement.executeUpdate();
            }
        } else {
            String update = "UPDATE Project_Applications SET request_message = ?, status = 'PENDING', "
                    + "application_date = CURRENT_TIMESTAMP WHERE application_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, message); statement.setLong(2, applicationId); statement.executeUpdate();
            }
        }
    }

    private void withdrawRequest(Connection connection, long studentId, long applicationId)
            throws SQLException, WorkflowException {
        beginTransaction(connection);
        long projectId;
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT project_id FROM Project_Applications WHERE application_id = ? AND student_id = ? FOR UPDATE")) {
            lookup.setLong(1, applicationId); lookup.setLong(2, studentId);
            try (ResultSet result = lookup.executeQuery()) {
                if (!result.next()) throw new WorkflowException("cannot-withdraw");
                projectId = result.getLong("project_id");
            }
        }
        ProjectState project = lockProject(connection, projectId);
        if ("REMOVED".equals(project.status)) throw new WorkflowException("project-removed");
        String sql = "UPDATE Project_Applications SET status = 'WITHDRAWN' "
                + "WHERE application_id = ? AND student_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, applicationId); statement.setLong(2, studentId);
            if (statement.executeUpdate() != 1) throw new WorkflowException("cannot-withdraw");
        }
    }

    private void createInvitation(Connection connection, long ownerId, long projectId, long studentId,
                                  String rawMessage) throws SQLException, WorkflowException {
        String message = validateMessage(rawMessage);
        beginTransaction(connection);
        ProjectState project = lockProject(connection, projectId);
        requireOpen(project);
        if (project.creatorId != ownerId) throw new WorkflowException("not-owner");
        if (studentId == ownerId) throw new WorkflowException("own-project");
        if (!isStudentEligible(connection, studentId)) throw new WorkflowException("student-suspended");
        if (!hasMatchingSkill(connection, projectId, studentId)) throw new WorkflowException("not-recommended");
        if (isProjectMember(connection, projectId, studentId)) throw new WorkflowException("already-member");
        if (hasActiveApplication(connection, projectId, studentId)) throw new WorkflowException("has-request");

        String select = "SELECT invitation_id, status FROM Project_Invitations "
                + "WHERE project_id = ? AND student_id = ? FOR UPDATE";
        Long id = null;
        String status = null;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, projectId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) { id = result.getLong("invitation_id"); status = result.getString("status"); }
            }
        }
        if ("PENDING".equals(status) || "ACCEPTED".equals(status)) throw new WorkflowException("already-invited");
        int occupiedSlots = countProjectMembers(connection, projectId)
                + (isProjectMember(connection, projectId, project.creatorId) ? 0 : 1);
        if (occupiedSlots + countPendingInvitations(connection, projectId) >= project.capacity) {
            throw new WorkflowException("team-full");
        }
        if (id == null) {
            String insert = "INSERT INTO Project_Invitations "
                    + "(project_id, student_id, invited_by, invitation_message, status) VALUES (?, ?, ?, ?, 'PENDING')";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setLong(1, projectId); statement.setLong(2, studentId);
                statement.setLong(3, ownerId); statement.setString(4, message); statement.executeUpdate();
            }
        } else {
            String update = "UPDATE Project_Invitations SET invited_by = ?, invitation_message = ?, status = 'PENDING', "
                    + "invited_at = CURRENT_TIMESTAMP, responded_at = NULL WHERE invitation_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setLong(1, ownerId); statement.setString(2, message); statement.setLong(3, id);
                statement.executeUpdate();
            }
        }
    }

    private void respondToInvitation(Connection connection, long studentId, long invitationId, String decision)
            throws SQLException, WorkflowException {
        if (!"accepted".equals(decision) && !"declined".equals(decision)) {
            throw new WorkflowException("invalid-decision");
        }
        beginTransaction(connection);
        String lookup = "SELECT project_id FROM Project_Invitations WHERE invitation_id = ? AND student_id = ?";
        long projectId;
        try (PreparedStatement statement = connection.prepareStatement(lookup)) {
            statement.setLong(1, invitationId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new WorkflowException("invitation-not-found");
                projectId = result.getLong("project_id");
            }
        }
        ProjectState project = lockProject(connection, projectId);
        String currentStatus = lockInvitation(connection, invitationId, studentId);
        if (!"PENDING".equals(currentStatus)) throw new WorkflowException("invitation-not-pending");
        if ("REMOVED".equals(project.status)) throw new WorkflowException("project-removed");
        if ("declined".equals(decision)) {
            updateInvitationStatus(connection, invitationId, "DECLINED");
            return;
        }
        requireOpen(project);
        addMemberWithinCapacity(connection, project, studentId);
        updateInvitationStatus(connection, invitationId, "ACCEPTED");
    }

    private void manageApplication(Connection connection, long ownerId, long applicationId, String decision)
            throws SQLException, WorkflowException {
        if (!"shortlisted".equals(decision) && !"accepted".equals(decision) && !"rejected".equals(decision)) {
            throw new WorkflowException("invalid-decision");
        }
        beginTransaction(connection);
        long projectId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT project_id FROM Project_Applications WHERE application_id = ?")) {
            statement.setLong(1, applicationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new WorkflowException("request-not-found");
                projectId = result.getLong("project_id");
            }
        }
        ProjectState project = lockProject(connection, projectId);
        if (project.creatorId != ownerId) throw new WorkflowException("not-owner");
        if ("REMOVED".equals(project.status)) throw new WorkflowException("project-removed");
        String status = lockApplication(connection, applicationId, projectId);
        if ("rejected".equals(decision)) {
            if (!"PENDING".equals(status) && !"SHORTLISTED".equals(status)) {
                throw new WorkflowException("request-not-pending");
            }
            updateApplicationStatus(connection, applicationId, "REJECTED");
            return;
        }
        requireOpen(project);
        if ("shortlisted".equals(decision)) {
            if (!"PENDING".equals(status)) throw new WorkflowException("request-not-pending");
            updateApplicationStatus(connection, applicationId, "SHORTLISTED");
            return;
        }
        if (!"PENDING".equals(status) && !"SHORTLISTED".equals(status)) {
            throw new WorkflowException("request-not-pending");
        }
        addMemberWithinCapacity(connection, project, getApplicationStudentId(connection, applicationId));
        updateApplicationStatus(connection, applicationId, "ACCEPTED");
    }

    /** Must be called after the project row has been selected FOR UPDATE in this transaction. */
    private void addMemberWithinCapacity(Connection connection, ProjectState project, long studentId)
            throws SQLException, WorkflowException {
        if (project.creatorId == studentId) throw new WorkflowException("own-project");
        long teamId = getOrCreateTeam(connection, project);
        if (isProjectMember(connection, project.projectId, studentId)) return;

        int memberCount = countProjectMembers(connection, project.projectId);
        boolean leaderAlreadyMember = isProjectMember(connection, project.projectId, project.creatorId);
        int occupiedSlots = memberCount + (leaderAlreadyMember ? 0 : 1);
        if (occupiedSlots >= project.capacity) throw new WorkflowException("team-full");

        if (!leaderAlreadyMember) insertTeamMember(connection, teamId, project.creatorId, "LEADER");
        insertTeamMember(connection, teamId, studentId, "MEMBER");
    }

    private long getOrCreateTeam(Connection connection, ProjectState project) throws SQLException {
        String findLeaderTeam = "SELECT t.team_id FROM Teams t JOIN Team_Members tm ON tm.team_id = t.team_id "
                + "WHERE t.project_id = ? AND tm.student_id = ? ORDER BY t.created_at, t.team_id LIMIT 1 FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(findLeaderTeam)) {
            statement.setLong(1, project.projectId); statement.setLong(2, project.creatorId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getLong("team_id");
            }
        }
        String find = "SELECT team_id FROM Teams WHERE project_id = ? ORDER BY created_at, team_id LIMIT 1 FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(find)) {
            statement.setLong(1, project.projectId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getLong("team_id");
            }
        }
        String insert = "INSERT INTO Teams (project_id, team_name, status) VALUES (?, ?, 'FORMING')";
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, project.projectId);
            statement.setString(2, "Project " + project.projectId + " Team");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Team ID was not returned after insert.");
                return keys.getLong(1);
            }
        }
    }

    private void insertTeamMember(Connection connection, long teamId, long studentId, String role) throws SQLException {
        String sql = "INSERT INTO Team_Members (team_id, student_id, member_role) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teamId); statement.setLong(2, studentId); statement.setString(3, role);
            statement.executeUpdate();
        }
    }

    private boolean isProjectMember(Connection connection, long projectId, long studentId) throws SQLException {
        String sql = "SELECT 1 FROM Team_Members tm JOIN Teams t ON t.team_id = tm.team_id "
                + "WHERE t.project_id = ? AND tm.student_id = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private int countProjectMembers(Connection connection, long projectId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT tm.student_id) AS member_count FROM Team_Members tm "
                + "JOIN Teams t ON t.team_id = tm.team_id WHERE t.project_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt("member_count"); }
        }
    }

    private int countPendingInvitations(Connection connection, long projectId) throws SQLException {
        String sql = "SELECT COUNT(*) AS invitation_count FROM Project_Invitations "
                + "WHERE project_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt("invitation_count"); }
        }
    }

    private boolean isStudentEligible(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT is_suspended FROM Students WHERE student_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && !result.getBoolean("is_suspended");
            }
        }
    }

    private ProjectState lockProject(Connection connection, long projectId) throws SQLException, WorkflowException {
        if (projectId <= 0) throw new WorkflowException("invalid-project");
        String sql = "SELECT project_id, creator_id, required_team_size, status FROM Projects WHERE project_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new WorkflowException("project-not-found");
                return new ProjectState(result.getLong("project_id"), result.getLong("creator_id"),
                        result.getInt("required_team_size"), result.getString("status"));
            }
        }
    }

    private void requireOpen(ProjectState project) throws WorkflowException {
        if ("REMOVED".equals(project.status)) throw new WorkflowException("project-removed");
        if (!"OPEN".equals(project.status)) throw new WorkflowException("project-closed");
    }

    private boolean hasPendingInvitation(Connection connection, long projectId, long studentId) throws SQLException {
        return hasPairStatus(connection, "Project_Invitations", projectId, studentId, "PENDING");
    }

    private boolean hasActiveApplication(Connection connection, long projectId, long studentId) throws SQLException {
        String sql = "SELECT 1 FROM Project_Applications WHERE project_id = ? AND student_id = ? "
                + "AND status IN ('PENDING', 'SHORTLISTED', 'ACCEPTED') LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private boolean hasPairStatus(Connection connection, String table, long projectId, long studentId, String status)
            throws SQLException {
        // Table and status names are fixed internal constants at call sites, never request values.
        String sql = "SELECT 1 FROM " + table + " WHERE project_id = ? AND student_id = ? AND status = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId); statement.setLong(2, studentId); statement.setString(3, status);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private boolean hasMatchingSkill(Connection connection, long projectId, long studentId) throws SQLException {
        String sql = "SELECT 1 FROM Project_Skills ps JOIN Student_Skills ss ON ss.skill_id = ps.skill_id "
                + "WHERE ps.project_id = ? AND ss.student_id = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private String lockApplication(Connection connection, long applicationId, long projectId)
            throws SQLException, WorkflowException {
        String sql = "SELECT status FROM Project_Applications WHERE application_id = ? AND project_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, applicationId); statement.setLong(2, projectId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new WorkflowException("request-not-found");
                return result.getString("status");
            }
        }
    }

    private long getApplicationStudentId(Connection connection, long applicationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT student_id FROM Project_Applications WHERE application_id = ?")) {
            statement.setLong(1, applicationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Application disappeared while locked.");
                return result.getLong("student_id");
            }
        }
    }

    private void updateApplicationStatus(Connection connection, long applicationId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE Project_Applications SET status = ? WHERE application_id = ?")) {
            statement.setString(1, status); statement.setLong(2, applicationId); statement.executeUpdate();
        }
    }

    private String lockInvitation(Connection connection, long invitationId, long studentId)
            throws SQLException, WorkflowException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM Project_Invitations WHERE invitation_id = ? AND student_id = ? FOR UPDATE")) {
            statement.setLong(1, invitationId); statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new WorkflowException("invitation-not-found");
                return result.getString("status");
            }
        }
    }

    private void updateInvitationStatus(Connection connection, long invitationId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE Project_Invitations SET status = ?, responded_at = CURRENT_TIMESTAMP WHERE invitation_id = ?")) {
            statement.setString(1, status); statement.setLong(2, invitationId); statement.executeUpdate();
        }
    }

    private List<RequestView> loadRequests(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT a.application_id, a.project_id, p.title, a.application_date, a.status, a.request_message "
                + "FROM Project_Applications a JOIN Projects p ON p.project_id = a.project_id "
                + "WHERE a.student_id = ? AND p.status <> 'REMOVED' ORDER BY a.application_date DESC, a.application_id DESC";
        List<RequestView> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new RequestView(result.getLong("application_id"),
                        result.getLong("project_id"), result.getString("title"), result.getString("status"),
                        formatDate(result.getTimestamp("application_date")), result.getString("request_message")));
            }
        }
        return rows;
    }

    private List<InvitationView> loadInvitations(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT i.invitation_id, i.project_id, p.title, leader.full_name AS leader_name, "
                + "i.invitation_message, i.status, i.invited_at FROM Project_Invitations i "
                + "JOIN Projects p ON p.project_id = i.project_id "
                + "JOIN Students leader ON leader.student_id = i.invited_by "
                + "WHERE i.student_id = ? AND p.status <> 'REMOVED' ORDER BY i.invited_at DESC, i.invitation_id DESC";
        List<InvitationView> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new InvitationView(result.getLong("invitation_id"),
                        result.getLong("project_id"), result.getString("title"), result.getString("leader_name"),
                        result.getString("invitation_message"), result.getString("status"),
                        formatDate(result.getTimestamp("invited_at"))));
            }
        }
        return rows;
    }

    private List<LeaderProjectView> loadLeaderProjects(Connection connection, long ownerId) throws SQLException {
        String sql = "SELECT project_id, title, status, required_team_size FROM Projects "
                + "WHERE creator_id = ? AND status <> 'REMOVED' ORDER BY created_at DESC, project_id DESC";
        List<LeaderProjectView> projects = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) projects.add(new LeaderProjectView(result.getLong("project_id"),
                        result.getString("title"), result.getString("status"), result.getInt("required_team_size")));
            }
        }
        for (LeaderProjectView project : projects) {
            project.members.addAll(loadMembers(connection, project.projectId, ownerId, true));
            project.occupiedSlots = project.members.size();
            project.applicants.addAll(loadApplicants(connection, project.projectId));
        }
        return projects;
    }

    private List<ApplicantView> loadApplicants(Connection connection, long projectId) throws SQLException {
        String sql = "SELECT a.application_id, a.student_id, a.status, a.application_date, a.request_message, "
                + "s.full_name, s.department, s.year_of_study, "
                + "COUNT(DISTINCT ps.skill_id) AS required_count, "
                + "COALESCE(SUM(CASE WHEN ss.skill_id IS NOT NULL THEN CASE "
                + "WHEN ss.proficiency_level <= 2 THEN 1 WHEN ss.proficiency_level <= 4 THEN 3 ELSE 5 END "
                + "ELSE 0 END), 0) AS matched_weight, "
                + "GROUP_CONCAT(DISTINCT CASE WHEN ss.skill_id IS NOT NULL THEN CONCAT(sk.skill_name, ' (', "
                + "CASE WHEN ss.proficiency_level <= 2 THEN 'Beginner' WHEN ss.proficiency_level <= 4 "
                + "THEN 'Intermediate' ELSE 'Advanced' END, ')') END ORDER BY sk.skill_name SEPARATOR ', ') AS matched_skills "
                + "FROM Project_Applications a JOIN Students s ON s.student_id = a.student_id "
                + "LEFT JOIN Project_Skills ps ON ps.project_id = a.project_id "
                + "LEFT JOIN Skills sk ON sk.skill_id = ps.skill_id "
                + "LEFT JOIN Student_Skills ss ON ss.student_id = a.student_id AND ss.skill_id = ps.skill_id "
                + "WHERE a.project_id = ? GROUP BY a.application_id, a.student_id, a.status, a.application_date, "
                + "a.request_message, s.full_name, s.department, s.year_of_study "
                + "ORDER BY CASE a.status WHEN 'PENDING' THEN 1 WHEN 'SHORTLISTED' THEN 2 ELSE 3 END, "
                + "matched_weight DESC, s.full_name";
        List<ApplicantView> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int required = result.getInt("required_count");
                    int weight = result.getInt("matched_weight");
                    double score = required == 0 ? 0 : (100.0 * weight) / (required * 5.0);
                    rows.add(new ApplicantView(result.getLong("application_id"), result.getLong("student_id"),
                            result.getString("full_name"), result.getString("department"),
                            result.getInt("year_of_study"), result.getString("status"),
                            formatDate(result.getTimestamp("application_date")), result.getString("request_message"),
                            result.getString("matched_skills"), score));
                }
            }
        }
        return rows;
    }

    private List<MemberView> loadMembers(Connection connection, long projectId, long ownerId, boolean includeLeader)
            throws SQLException {
        String sql = "SELECT DISTINCT s.student_id, s.full_name, s.department, s.year_of_study, tm.member_role "
                + "FROM Teams t JOIN Team_Members tm ON tm.team_id = t.team_id "
                + "JOIN Students s ON s.student_id = tm.student_id WHERE t.project_id = ? ORDER BY "
                + "CASE tm.member_role WHEN 'LEADER' THEN 0 ELSE 1 END, s.full_name";
        Map<Long, MemberView> byStudent = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long memberId = result.getLong("student_id");
                    byStudent.putIfAbsent(memberId, new MemberView(memberId, result.getString("full_name"),
                            result.getString("department"), result.getInt("year_of_study"),
                            memberId == ownerId ? "LEADER" : result.getString("member_role")));
                }
            }
        }
        if (includeLeader && !byStudent.containsKey(ownerId)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT student_id, full_name, department, year_of_study FROM Students WHERE student_id = ?")) {
                statement.setLong(1, ownerId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) byStudent.put(ownerId, new MemberView(ownerId, result.getString("full_name"),
                            result.getString("department"), result.getInt("year_of_study"), "LEADER"));
                }
            }
        }
        return new ArrayList<>(byStudent.values());
    }

    private List<JoinedTeamView> loadStudentTeams(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT DISTINCT p.project_id, p.title, p.creator_id, leader.full_name AS leader_name, "
                + "p.required_team_size, tm.member_role, tm.joined_at, "
                + "(SELECT COUNT(DISTINCT tm2.student_id) FROM Teams t2 "
                + "JOIN Team_Members tm2 ON tm2.team_id = t2.team_id WHERE t2.project_id = p.project_id) "
                + "+ CASE WHEN EXISTS (SELECT 1 FROM Teams t3 JOIN Team_Members tm3 ON tm3.team_id = t3.team_id "
                + "WHERE t3.project_id = p.project_id AND tm3.student_id = p.creator_id) THEN 0 ELSE 1 END AS occupied "
                + "FROM Teams t "
                + "JOIN Projects p ON p.project_id = t.project_id "
                + "JOIN Team_Members tm ON tm.team_id = t.team_id "
                + "JOIN Students leader ON leader.student_id = p.creator_id "
                + "WHERE tm.student_id = ? AND p.status <> 'REMOVED' ORDER BY tm.joined_at DESC, p.title";
        Map<Long, JoinedTeamView> byProject = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long projectId = result.getLong("project_id");
                    int capacity = result.getInt("required_team_size");
                    int occupied = result.getInt("occupied");
                    String role = studentId == result.getLong("creator_id")
                            ? "LEADER" : result.getString("member_role");
                    byProject.putIfAbsent(projectId, new JoinedTeamView(projectId, result.getString("title"),
                            result.getString("leader_name"), role, formatDate(result.getTimestamp("joined_at")),
                            capacity, Math.max(0, capacity - occupied)));
                }
            }
        }
        return new ArrayList<>(byProject.values());
    }

    private String validateMessage(String rawMessage) throws WorkflowException {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.codePointCount(0, message.length()) > 1000) throw new WorkflowException("message-too-long");
        for (int i = 0; i < message.length(); i++) {
            if (Character.isISOControl(message.charAt(i)) && message.charAt(i) != '\n' && message.charAt(i) != '\t') {
                throw new WorkflowException("invalid-message");
            }
        }
        return message.isEmpty() ? null : message;
    }

    /** Read committed makes post-lock membership and capacity reads observe prior approvals. */
    private void beginTransaction(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
    }

    private long positiveId(String value) {
        if (value == null) return -1;
        try { long parsed = Long.parseLong(value); return parsed > 0 ? parsed : -1; }
        catch (NumberFormatException exception) { return -1; }
    }

    private String formatDate(Timestamp value) { return value == null ? "" : DATE_FORMAT.format(value.toLocalDateTime()); }

    public static void applyMessage(HttpServletRequest request) {
        String key = request.getParameter("result");
        String message = switch (key == null ? "" : key) {
            case "requested" -> "Your request has been sent.";
            case "withdrawn" -> "Your pending request has been withdrawn.";
            case "invited" -> "The invitation has been sent.";
            case "joined" -> "You joined the project team.";
            case "declined" -> "The invitation was declined.";
            case "shortlisted" -> "The applicant has been shortlisted.";
            case "member-added" -> "The applicant joined the team.";
            case "rejected" -> "The request has been rejected.";
            case "cannot-withdraw" -> "Only your own pending request can be withdrawn.";
            case "own-project" -> "You cannot join or invite yourself to your own project.";
            case "already-requested" -> "You already have an active request for this project.";
            case "already-member" -> "This student is already a member of the project team.";
            case "has-invitation" -> "You already have an invitation for this project. Respond to it first.";
            case "has-request" -> "This student already has an active request for this project.";
            case "already-invited" -> "This student already has an active invitation for this project.";
            case "team-full" -> "The team has no remaining seat, so no invitation or team change was made.";
            case "invalid-csrf" -> "This invitation form expired. Refresh the page and try again.";
            case "student-suspended" -> "That student cannot receive an invitation right now.";
            case "project-closed" -> "This project is closed and cannot accept new requests or members.";
            case "project-removed" -> "This project has been removed and is no longer available for team actions.";
            case "not-owner" -> "Only this project's creator can manage its requests and invitations.";
            case "not-recommended" -> "Invitations can be sent only to students with at least one matching skill.";
            case "request-not-pending" -> "This request is no longer pending or shortlisted.";
            case "invitation-not-pending" -> "This invitation is no longer pending.";
            case "invitation-not-found" -> "That invitation was not found.";
            case "request-not-found" -> "That request was not found.";
            case "project-not-found" -> "That project was not found.";
            case "message-too-long" -> "Messages must be no longer than 1,000 characters.";
            case "invalid-message" -> "The message contains unsupported control characters.";
            case "invalid-decision", "invalid-project" -> "Choose a valid action and project.";
            case "failed" -> "The operation could not be completed. Please try again.";
            default -> null;
        };
        if (message != null) {
            boolean success = "requested".equals(key) || "withdrawn".equals(key) || "invited".equals(key)
                    || "joined".equals(key) || "declined".equals(key) || "shortlisted".equals(key)
                    || "member-added".equals(key) || "rejected".equals(key);
            request.setAttribute(success ? "notice" : "error", message);
        }
    }

    private void redirectWith(HttpServletResponse response, HttpServletRequest request, String path, String result)
            throws IOException {
        if ("/invitations".equals(path) && "invite".equals(request.getParameter("action"))) {
            long projectId = positiveId(request.getParameter("project_id"));
            String selection = projectId > 0 ? "?project_id=" + projectId + "&result=" + result
                    : "?result=" + result;
            response.sendRedirect(request.getContextPath() + "/teammates" + selection);
            return;
        }
        String target = switch (path) {
            case "/join" -> "/projects";
            case "/my-requests" -> "/my-requests";
            case "/invitations" -> "/invitations";
            case "/team-management" -> "/team-management";
            default -> "/projects";
        };
        response.sendRedirect(request.getContextPath() + target + "?result=" + result);
    }

    /** Create one per-session token for invitation POSTs from the recommendations page. */
    public static String issueInviteCsrfToken(HttpSession session) {
        if (session == null) return "";
        synchronized (session) {
            Object existing = session.getAttribute(INVITE_CSRF_KEY);
            if (existing instanceof String token && !token.isBlank()) return token;
            byte[] bytes = new byte[32];
            CSRF_RANDOM.nextBytes(bytes);
            StringBuilder token = new StringBuilder(64);
            for (byte value : bytes) token.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            String generated = token.toString();
            session.setAttribute(INVITE_CSRF_KEY, generated);
            return generated;
        }
    }

    private boolean validInviteCsrfToken(HttpSession session, String submitted) {
        if (session == null || submitted == null) return false;
        Object expected = session.getAttribute(INVITE_CSRF_KEY);
        if (!(expected instanceof String token) || token.isBlank()) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }

    private void rollbackQuietly(Connection connection, Throwable original) {
        if (connection == null) return;
        try { connection.rollback(); } catch (SQLException exception) { original.addSuppressed(exception); }
    }

    private static final class ProjectState {
        private final long projectId; private final long creatorId; private final int capacity; private final String status;
        private ProjectState(long projectId, long creatorId, int capacity, String status) {
            this.projectId = projectId; this.creatorId = creatorId; this.capacity = capacity; this.status = status;
        }
    }
    private static final class WorkflowException extends Exception {
        private static final long serialVersionUID = 1L; private final String key;
        private WorkflowException(String key) { this.key = key; }
    }

    public static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String displayStatus(String status) {
        if (status == null || status.isEmpty()) return "";
        String lower = status.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static final class RequestView {
        private final long applicationId, projectId; private final String title, status, date, message;
        private RequestView(long applicationId, long projectId, String title, String status, String date, String message) {
            this.applicationId = applicationId; this.projectId = projectId; this.title = title;
            this.status = status; this.date = date; this.message = message;
        }
        public long getApplicationId() { return applicationId; } public long getProjectId() { return projectId; }
        public String getTitle() { return title; } public String getStatus() { return status; }
        public String getDate() { return date; } public String getMessage() { return message; }
    }
    public static final class InvitationView {
        private final long invitationId, projectId; private final String title, leader, message, status, date;
        private InvitationView(long invitationId, long projectId, String title, String leader, String message,
                               String status, String date) {
            this.invitationId = invitationId; this.projectId = projectId; this.title = title; this.leader = leader;
            this.message = message; this.status = status; this.date = date;
        }
        public long getInvitationId() { return invitationId; } public long getProjectId() { return projectId; }
        public String getTitle() { return title; } public String getLeader() { return leader; }
        public String getMessage() { return message; } public String getStatus() { return status; }
        public String getDate() { return date; }
    }
    public static final class LeaderProjectView {
        private final long projectId; private final String title, status; private final int capacity;
        private int occupiedSlots; private final List<MemberView> members = new ArrayList<>();
        private final List<ApplicantView> applicants = new ArrayList<>();
        private LeaderProjectView(long projectId, String title, String status, int capacity) {
            this.projectId = projectId; this.title = title; this.status = status; this.capacity = capacity;
        }
        public long getProjectId() { return projectId; } public String getTitle() { return title; }
        public String getStatus() { return status; } public int getCapacity() { return capacity; }
        public int getOccupiedSlots() { return occupiedSlots; } public int getOpenSlots() { return Math.max(0, capacity - occupiedSlots); }
        public List<MemberView> getMembers() { return members; } public List<ApplicantView> getApplicants() { return applicants; }
    }
    public static final class ApplicantView {
        private final long applicationId, studentId; private final String fullName, department, status, date, message, skills;
        private final int year; private final double matchPercent;
        private ApplicantView(long applicationId, long studentId, String fullName, String department, int year,
                              String status, String date, String message, String skills, double matchPercent) {
            this.applicationId = applicationId; this.studentId = studentId; this.fullName = fullName;
            this.department = department; this.year = year; this.status = status; this.date = date;
            this.message = message; this.skills = skills; this.matchPercent = matchPercent;
        }
        public long getApplicationId() { return applicationId; } public long getStudentId() { return studentId; }
        public String getFullName() { return fullName; } public String getDepartment() { return department; }
        public int getYear() { return year; } public String getStatus() { return status; }
        public String getDate() { return date; } public String getMessage() { return message; }
        public String getSkills() { return skills; } public double getMatchPercent() { return matchPercent; }
    }
    public static final class MemberView {
        private final long studentId; private final String fullName, department, role; private final int year;
        private MemberView(long studentId, String fullName, String department, int year, String role) {
            this.studentId = studentId; this.fullName = fullName; this.department = department;
            this.year = year; this.role = role;
        }
        public long getStudentId() { return studentId; } public String getFullName() { return fullName; }
        public String getDepartment() { return department; } public int getYear() { return year; }
        public String getRole() { return role; }
    }
    public static final class JoinedTeamView {
        private final long projectId; private final String title, leader, role, date; private final int capacity, openSlots;
        private JoinedTeamView(long projectId, String title, String leader, String role, String date,
                               int capacity, int openSlots) {
            this.projectId = projectId; this.title = title; this.leader = leader; this.role = role;
            this.date = date; this.capacity = capacity; this.openSlots = openSlots;
        }
        public long getProjectId() { return projectId; } public String getTitle() { return title; }
        public String getLeader() { return leader; } public String getRole() { return role; }
        public String getDate() { return date; } public int getCapacity() { return capacity; }
        public int getOpenSlots() { return openSlots; }
    }
}

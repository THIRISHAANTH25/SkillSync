<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="edu.college.smartmatcher.web.AdminServlet" %>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Admin Dashboard | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body>
<div class="shell"><header class="topbar"><strong class="topbar-title"><img class="topbar-logo" src="${pageContext.request.contextPath}/assets/skillsync-logo-symbol-transparent.png?v=20261018-transparent1" alt="">SkillSync</strong><span class="topbar-context">Admin workspace · System overview</span><div class="topbar-actions"><span class="topbar-role-pill">ADMIN</span></div></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="dashboard"/></jsp:include>
    <main class="admin-dashboard">
        <section class="dashboard-hero admin-hero"><div><p class="eyebrow">SKILLSYNC CONTROL CENTER</p><h1>Admin Dashboard</h1><p class="intro">A live overview of student activity, project formation, and moderation.</p></div><a class="quiet-link" href="${pageContext.request.contextPath}/admin/history">Review moderation history <span aria-hidden="true">→</span></a></section>
        <% if (request.getAttribute("adminError") != null) { %><p class="error" role="alert"><%= AdminServlet.escapeHtml(String.valueOf(request.getAttribute("adminError"))) %></p><% } %>
        <section class="dashboard-stat-grid admin-stat-grid" aria-label="System totals">
            <article class="dashboard-stat"><span class="stat-icon stat-projects" aria-hidden="true">♙</span><div><strong>${totalStudents}</strong><span>Registered students</span></div><small>All registered accounts</small></article>
            <article class="dashboard-stat"><span class="stat-icon stat-teams" aria-hidden="true">▦</span><div><strong>${activeProjects}</strong><span>Active projects</span></div><small>Open or in progress</small></article>
            <article class="dashboard-stat"><span class="stat-icon stat-invitations" aria-hidden="true">⊘</span><div><strong>${bannedStudents}</strong><span>Suspended students</span></div><small>Account restrictions</small></article>
            <article class="dashboard-stat"><span class="stat-icon stat-complete" aria-hidden="true">◎</span><div><strong>${totalTeams}</strong><span>Teams formed</span></div><small>${projectsWithTeams} active projects with teams</small></article>
        </section>

        <div class="admin-analytics-grid">
            <section class="dashboard-panel" aria-labelledby="registration-chart-heading">
                <div class="dashboard-section-heading"><div><p class="eyebrow">LAST 7 DAYS</p><h2 id="registration-chart-heading">Student registrations</h2></div><span class="chart-caption">Daily new accounts</span></div>
                <% java.util.List<AdminServlet.MetricBar> registrationTrend = (java.util.List<AdminServlet.MetricBar>) request.getAttribute("registrationTrend"); %>
                <div class="mini-chart" role="list" aria-label="Student registrations in the last seven days">
                    <% if (registrationTrend == null || registrationTrend.isEmpty()) { %><p class="dashboard-empty compact">Registration history is not available.</p><% } else { for (AdminServlet.MetricBar bar : registrationTrend) { %>
                        <div class="mini-chart-column" role="listitem"><div class="mini-chart-value"><%= bar.getValue() %></div><div class="mini-chart-track" aria-hidden="true"><span style="height:<%= bar.getPercent() %>%"></span></div><small><%= bar.getLabel() %></small></div>
                    <% } } %>
                </div>
            </section>
            <section class="dashboard-panel" aria-labelledby="project-health-heading">
                <div class="dashboard-section-heading"><div><p class="eyebrow">PROJECT HEALTH</p><h2 id="project-health-heading">Projects by status</h2></div><a href="${pageContext.request.contextPath}/admin/projects">Moderate projects <span aria-hidden="true">→</span></a></div>
                <% java.util.List<AdminServlet.MetricBar> projectMetrics = (java.util.List<AdminServlet.MetricBar>) request.getAttribute("projectStatusMetrics"); %>
                <% if (projectMetrics == null || projectMetrics.isEmpty()) { %><div class="dashboard-empty compact"><strong>No projects yet</strong><p>Project status totals will appear as students create projects.</p></div><% } else { %><div class="metric-bar-list"><% for (AdminServlet.MetricBar bar : projectMetrics) { %>
                    <div class="metric-bar-row"><div><strong><%= bar.getLabel() %></strong><span><%= bar.getValue() %></span></div><div class="metric-bar-track"><span style="width:<%= bar.getPercent() %>%"></span></div></div>
                <% } %></div><% } %>
            </section>
            <section class="dashboard-panel" aria-labelledby="popular-skills-heading">
                <div class="dashboard-section-heading"><div><p class="eyebrow">SKILL ECOSYSTEM</p><h2 id="popular-skills-heading">Popular student skills</h2></div></div>
                <% java.util.List<AdminServlet.MetricBar> popularSkills = (java.util.List<AdminServlet.MetricBar>) request.getAttribute("popularSkills"); %>
                <% if (popularSkills == null || popularSkills.isEmpty()) { %><div class="dashboard-empty compact"><strong>No skills recorded yet</strong><p>Popular skills are based on students' saved profiles.</p></div><% } else { %><div class="metric-bar-list"><% for (AdminServlet.MetricBar bar : popularSkills) { %>
                    <div class="metric-bar-row"><div><strong><%= bar.getLabel() %></strong><span><%= bar.getValue() %> students</span></div><div class="metric-bar-track"><span style="width:<%= bar.getPercent() %>%"></span></div></div>
                <% } %></div><% } %>
            </section>
            <section class="dashboard-panel admin-team-analytics" aria-labelledby="team-formation-heading">
                <div class="dashboard-section-heading"><div><p class="eyebrow">TEAM FORMATION</p><h2 id="team-formation-heading">Project-to-team activity</h2></div></div>
                <div class="formation-summary"><strong>${projectsWithTeams}</strong><span>active projects have a forming or active team</span></div>
                <div class="formation-summary"><strong>${totalTeams}</strong><span>teams currently recorded across all projects</span></div>
                <p class="data-note">Counts are read from Projects and Teams. Task completion analytics will be added with project tracking.</p>
            </section>
        </div>

        <section class="admin-section dashboard-panel" aria-labelledby="registrations-heading">
            <div class="dashboard-section-heading"><div><p class="eyebrow">NEW ACCOUNTS</p><h2 id="registrations-heading">Recent student registrations</h2></div><a href="${pageContext.request.contextPath}/admin/students">Manage students <span aria-hidden="true">→</span></a></div>
            <% java.util.List<AdminServlet.StudentRow> students = (java.util.List<AdminServlet.StudentRow>) request.getAttribute("recentStudents"); %>
            <% if (students == null || students.isEmpty()) { %><div class="dashboard-empty compact">No registrations yet.</div><% } else { %>
            <div class="admin-table-wrap"><table><thead><tr><th>Student</th><th>Department</th><th>Year</th><th>Registered</th></tr></thead><tbody>
                <% for (AdminServlet.StudentRow student : students) { %><tr><td><span class="student-table-avatar"><%= AdminServlet.initialFor(student.getName()) %></span><strong><%= student.getName() %></strong><br><span class="muted"><%= student.getEmail() %></span></td><td><%= student.getDepartment() %></td><td><%= student.getYear() %></td><td><%= student.getRegistered() %></td></tr><% } %>
            </tbody></table></div><% } %>
        </section>
        <section class="admin-section dashboard-panel" aria-labelledby="recent-projects-heading">
            <div class="dashboard-section-heading"><div><p class="eyebrow">PROJECT ACTIVITY</p><h2 id="recent-projects-heading">Recently created projects</h2></div><a href="${pageContext.request.contextPath}/admin/projects">Manage projects <span aria-hidden="true">→</span></a></div>
            <% java.util.List<AdminServlet.ProjectRow> projects = (java.util.List<AdminServlet.ProjectRow>) request.getAttribute("recentProjects"); %>
            <% if (projects == null || projects.isEmpty()) { %><div class="dashboard-empty compact">No projects found.</div><% } else { %>
            <div class="admin-table-wrap"><table><thead><tr><th>Project</th><th>Creator</th><th>Status</th><th>Created</th><th>Team</th></tr></thead><tbody>
                <% for (AdminServlet.ProjectRow project : projects) { %><tr><td><strong><%= project.getTitle() %></strong></td><td><%= project.getCreator() %></td><td><span class="status-badge"><%= project.getStatus().replace('_',' ') %></span></td><td><%= project.getCreated() %></td><td><%= project.getTeamName().isEmpty() ? "No team" : project.getTeamName() + " · " + project.getMembers() + "/" + project.getCapacity() %></td></tr><% } %>
            </tbody></table></div><% } %>
        </section>
        <section class="admin-section dashboard-panel" aria-labelledby="recent-actions-heading">
            <div class="dashboard-section-heading"><div><p class="eyebrow">AUDIT TRAIL</p><h2 id="recent-actions-heading">Recent moderation actions</h2></div><a href="${pageContext.request.contextPath}/admin/history">Moderation history <span aria-hidden="true">→</span></a></div>
            <% java.util.List<AdminServlet.AuditRow> actions = (java.util.List<AdminServlet.AuditRow>) request.getAttribute("recentActions"); %>
            <% if (actions == null || actions.isEmpty()) { %><div class="dashboard-empty compact">No moderation actions recorded yet.</div><% } else { %>
            <div class="admin-table-wrap"><table><thead><tr><th>Action</th><th>Target</th><th>Reason</th><th>Administrator</th><th>Date</th></tr></thead><tbody>
                <% for (AdminServlet.AuditRow action : actions) { %><tr><td><%= action.getAction().replace('_', ' ') %></td><td><%= action.getTargetType() %> #<%= action.getTargetId() %></td><td><%= action.getReason().isEmpty() ? "—" : action.getReason() %></td><td><%= action.getActor() %></td><td><%= action.getCreated() %></td></tr><% } %>
            </tbody></table></div><% } %>
        </section>
    </main>
</div></body></html>

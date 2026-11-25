<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="edu.college.smartmatcher.web.AdminServlet" %>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Manage Projects | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body>
<div class="shell"><header class="topbar"><strong>SkillSync</strong><span>Project moderation</span></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="projects"/></jsp:include>
    <main><p class="eyebrow">PROJECT MODERATION</p><h1>Manage Projects</h1>
        <p class="intro">Review every project, its creator, lifecycle, and current team. Removal hides it from student-facing workflows while preserving its records.</p>
        <% if (request.getAttribute("adminError") != null) { %><p class="error" role="alert"><%= AdminServlet.escapeHtml(String.valueOf(request.getAttribute("adminError"))) %></p><% } %>
        <% String notice = request.getParameter("notice"); if ("removed".equals(notice)) { %><p class="success" role="status">Project removed from student workflows.</p><% } else if ("restored".equals(notice)) { %><p class="success" role="status">Project restored to its previous status.</p><% } %>
        <% java.util.List<AdminServlet.ProjectRow> projects = (java.util.List<AdminServlet.ProjectRow>) request.getAttribute("projects"); %>
        <% if (projects == null || projects.isEmpty()) { %><div class="card admin-empty">No projects found.</div><% } else { %>
        <div class="admin-project-list">
        <% for (AdminServlet.ProjectRow project : projects) { %><article class="card admin-project-card" id="project-<%= project.getId() %>">
            <div class="admin-project-heading"><div><p class="eyebrow">PROJECT #<%= project.getId() %></p><h2><%= project.getTitle() %></h2><p class="muted">Created by <strong><%= project.getCreator() %></strong> · <%= project.getCreated() %></p></div><span class="badge <%= project.isRemoved() ? "status-rejected" : "" %>"><%= project.getStatus() %></span></div>
            <div class="admin-team-detail"><strong>Team details</strong><% if (project.getTeamName().isEmpty()) { %><span>No team has been formed · capacity <%= project.getCapacity() %></span><% } else { %><span><%= project.getTeamName() %> · <%= project.getTeamStatus() %> · <%= project.getMembers() %> / <%= project.getCapacity() %> members</span><% } %></div>
            <% if (project.isRemoved()) { %><% if (!project.getRemovalReason().isEmpty()) { %><p class="admin-reason"><strong>Removal reason:</strong> <%= project.getRemovalReason() %></p><% } %>
                <form method="post" action="${pageContext.request.contextPath}/admin/projects"><input type="hidden" name="csrf_token" value="${csrfToken}"><input type="hidden" name="action" value="restore-project"><input type="hidden" name="project_id" value="<%= project.getId() %>"><button type="submit">Restore Project</button></form>
            <% } else { %><form class="admin-remove-form" method="post" action="${pageContext.request.contextPath}/admin/projects" onsubmit="return confirm('Remove this project from all student workflows?');"><input type="hidden" name="csrf_token" value="${csrfToken}"><input type="hidden" name="action" value="remove-project"><input type="hidden" name="project_id" value="<%= project.getId() %>"><label for="remove-reason-<%= project.getId() %>">Reason for removal</label><input id="remove-reason-<%= project.getId() %>" type="text" name="reason" maxlength="1000" placeholder="Enter reason" required><button class="danger" type="submit">Remove Project</button></form><% } %>
        </article><% } %>
        </div><% } %>
    </main>
</div></body></html>

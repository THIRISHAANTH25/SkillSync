<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="edu.college.smartmatcher.web.AdminServlet" %>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Moderation History | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body>
<div class="shell"><header class="topbar"><strong>SkillSync</strong><span>Persistent audit log</span></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="history"/></jsp:include>
    <main><p class="eyebrow">AUDIT TRAIL</p><h1>Moderation History</h1>
        <p class="intro">Ban, unban, project removal, and restoration events are recorded with their actor, target, reason, and timestamp.</p>
        <% if (request.getAttribute("adminError") != null) { %><p class="error" role="alert"><%= AdminServlet.escapeHtml(String.valueOf(request.getAttribute("adminError"))) %></p><% } %>
        <% java.util.List<AdminServlet.AuditRow> actions = (java.util.List<AdminServlet.AuditRow>) request.getAttribute("actions"); %>
        <% if (actions == null || actions.isEmpty()) { %><div class="card admin-empty">No moderation actions recorded yet.</div><% } else { %>
        <div class="admin-table-wrap"><table><thead><tr><th>Action</th><th>Target</th><th>Reason</th><th>Administrator</th><th>Date</th></tr></thead><tbody>
            <% for (AdminServlet.AuditRow action : actions) { %><tr><td><span class="badge"><%= action.getAction().replace('_', ' ') %></span></td><td><%= action.getTargetType() %> #<%= action.getTargetId() %></td><td><%= action.getReason().isEmpty() ? "—" : action.getReason() %></td><td><%= action.getActor() %></td><td><%= action.getCreated() %></td></tr><% } %>
        </tbody></table></div><% } %>
    </main>
</div></body></html>

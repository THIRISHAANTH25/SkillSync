<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="edu.college.smartmatcher.web.AdminServlet" %>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Manage Students | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body>
<div class="shell"><header class="topbar"><strong>SkillSync</strong><span>Student moderation</span></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="students"/></jsp:include>
    <main><p class="eyebrow">ACCOUNT MODERATION</p><h1>Manage Students</h1>
        <p class="intro">Search registered accounts and suspend or restore access. Bans take effect on the user's next server request.</p>
        <% if (request.getAttribute("adminError") != null) { %><p class="error" role="alert"><%= AdminServlet.escapeHtml(String.valueOf(request.getAttribute("adminError"))) %></p><% } %>
        <% String notice = request.getParameter("notice"); if ("banned".equals(notice)) { %><p class="success" role="status">Student account suspended.</p><% } else if ("unbanned".equals(notice)) { %><p class="success" role="status">Student account restored.</p><% } %>
        <form class="admin-search" method="get" action="${pageContext.request.contextPath}/admin/students">
            <label for="student-search">Search by name, email, or department</label><div class="admin-search-row"><input id="student-search" type="search" name="q" maxlength="100" value="${search}" placeholder="Search students"><button type="submit">Search</button></div>
        </form>
        <% java.util.List<AdminServlet.StudentRow> students = (java.util.List<AdminServlet.StudentRow>) request.getAttribute("students"); %>
        <% if (students == null || students.isEmpty()) { %><div class="card admin-empty">No student accounts match this search.</div><% } else { %>
        <div class="admin-table-wrap"><table><thead><tr><th>Student</th><th>Department / Year</th><th>Registered</th><th>Account</th><th>Moderation</th></tr></thead><tbody>
        <% for (AdminServlet.StudentRow student : students) { %><tr>
            <td><strong><%= student.getName() %></strong><br><span class="muted"><%= student.getEmail() %></span><br><span class="muted">ID #<%= student.getId() %></span></td>
            <td><%= student.getDepartment() %><br><span class="muted">Year <%= student.getYear() %></span></td><td><%= student.getRegistered() %></td>
            <td><span class="badge <%= student.isBanned() ? "status-rejected" : "status-active" %>"><%= student.isBanned() ? "Banned" : "Active" %></span><% if (student.isBanned() && !student.getReason().isEmpty()) { %><p class="admin-reason"><%= student.getReason() %></p><% } %></td>
            <td><% if (student.getId() == ((Number) session.getAttribute("studentId")).longValue()) { %><span class="muted">Your admin account</span>
                <% } else if (student.isBanned()) { %><form method="post" action="${pageContext.request.contextPath}/admin/students"><input type="hidden" name="csrf_token" value="${csrfToken}"><input type="hidden" name="action" value="unban-student"><input type="hidden" name="student_id" value="<%= student.getId() %>"><button class="secondary" type="submit">Unban</button></form>
                <% } else { %><form method="post" action="${pageContext.request.contextPath}/admin/students" onsubmit="return confirm('Ban this student account?');"><input type="hidden" name="csrf_token" value="${csrfToken}"><input type="hidden" name="action" value="ban-student"><input type="hidden" name="student_id" value="<%= student.getId() %>"><label class="visually-hidden" for="ban-reason-<%= student.getId() %>">Reason for banning <%= student.getName() %></label><input id="ban-reason-<%= student.getId() %>" type="text" name="reason" maxlength="1000" placeholder="Ban reason (required)" required><button class="danger" type="submit">Ban</button></form><% } %>
            </td></tr><% } %>
        </tbody></table></div><% } %>
        <p class="admin-footnote">Account records and their project data are retained when banned.</p>
    </main>
</div></body></html>

<%@ page pageEncoding="UTF-8" import="edu.college.smartmatcher.web.AdminServlet" %>
<nav aria-label="Main navigation">
    <a class="sidebar-brand" href="${pageContext.request.contextPath}${sessionScope.isAdmin ? '/admin' : '/account'}" aria-label="SkillSync dashboard">
        <img class="sidebar-logo-symbol" src="${pageContext.request.contextPath}/assets/skillsync-logo-symbol-transparent.png?v=20261018-transparent1" alt="">
        <img class="sidebar-logo-wordmark" data-theme-logo data-logo-dark-src="${pageContext.request.contextPath}/assets/skillsync-wordmark-transparent.png?v=20261018-transparent1" data-logo-light-src="${pageContext.request.contextPath}/assets/skillsync-wordmark-light.png?v=20261018-transparent1" src="${pageContext.request.contextPath}/assets/skillsync-wordmark-transparent.png?v=20261018-transparent1" alt="SkillSync">
        <span class="sidebar-brand-label">SkillSync</span>
    </a>
    <% if (Boolean.TRUE.equals(session.getAttribute("isAdmin"))) { %>
    <a href="${pageContext.request.contextPath}/admin" aria-current="${param.active eq 'dashboard' ? 'page' : 'false'}">Admin Dashboard</a>
    <a href="${pageContext.request.contextPath}/admin/students" aria-current="${param.active eq 'students' ? 'page' : 'false'}">Manage Students</a>
    <a href="${pageContext.request.contextPath}/admin/projects" aria-current="${param.active eq 'projects' ? 'page' : 'false'}">Manage Projects</a>
    <a href="${pageContext.request.contextPath}/admin/history" aria-current="${param.active eq 'history' ? 'page' : 'false'}">Moderation History</a>
    <a href="${pageContext.request.contextPath}/settings" aria-current="${param.active eq 'settings' ? 'page' : 'false'}">Settings</a>
    <% } else { %>
    <a href="${pageContext.request.contextPath}/account" aria-current="${param.active eq 'account' ? 'page' : 'false'}">Dashboard</a>
    <a href="${pageContext.request.contextPath}/skills" aria-current="${param.active eq 'skills' ? 'page' : 'false'}">My Skills</a>
    <a href="${pageContext.request.contextPath}/projects" aria-current="${param.active eq 'projects' ? 'page' : 'false'}">Projects</a>
    <a href="${pageContext.request.contextPath}/teammates" aria-current="${param.active eq 'teammates' ? 'page' : 'false'}">Find Teammates</a>
    <a href="${pageContext.request.contextPath}/skill-gaps" aria-current="${param.active eq 'skill-gaps' ? 'page' : 'false'}">Skill Gap Analysis</a>
    <a href="${pageContext.request.contextPath}/team-balance" aria-current="${param.active eq 'team-balance' ? 'page' : 'false'}">Team Balance Analyzer</a>
    <a href="${pageContext.request.contextPath}/my-requests" aria-current="${param.active eq 'my-requests' ? 'page' : 'false'}">My Requests</a>
    <a href="${pageContext.request.contextPath}/invitations" aria-current="${param.active eq 'invitations' ? 'page' : 'false'}">Invitations</a>
    <a href="${pageContext.request.contextPath}/team-management" aria-current="${param.active eq 'team-management' ? 'page' : 'false'}">Team Management</a>
    <a href="${pageContext.request.contextPath}/my-teams" aria-current="${param.active eq 'my-teams' ? 'page' : 'false'}">My Teams</a>
    <a href="${pageContext.request.contextPath}/settings" aria-current="${param.active eq 'settings' ? 'page' : 'false'}">Settings</a>
    <% } %>
    <a href="${pageContext.request.contextPath}/logout" aria-current="false">Sign Out</a>
    <%@ include file="theme-toggle.jspf" %>
    <div class="sidebar-user" aria-label="Signed in account">
        <span class="sidebar-avatar" aria-hidden="true"><%= AdminServlet.initialFor((String) session.getAttribute("studentName")) %></span>
        <span class="sidebar-user-copy"><strong><%= AdminServlet.escapeHtml((String) session.getAttribute("studentName")) %></strong>
            <small><%= Boolean.TRUE.equals(session.getAttribute("isAdmin")) ? "Administrator" : "Student" %></small></span>
    </div>
</nav>

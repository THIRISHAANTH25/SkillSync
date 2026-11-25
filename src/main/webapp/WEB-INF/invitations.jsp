<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, edu.college.smartmatcher.web.TeamWorkflowServlet, edu.college.smartmatcher.web.TeamWorkflowServlet.InvitationView" %>
<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Invitations | Smart Project Team Matcher</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body><div class="shell"><header class="topbar"><strong>Smart Project Team Matcher</strong><span>Student workspace</span></header>
<jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="invitations"/></jsp:include>
<main><h1>Invitations</h1><p class="intro">Project leaders may invite you to join their teams.</p>
<% if(request.getAttribute("notice")!=null){ %><p class="message" role="status"><%= TeamWorkflowServlet.escapeHtml((String)request.getAttribute("notice")) %></p><% } %><% if(request.getAttribute("error")!=null){ %><p class="error" role="alert"><%= TeamWorkflowServlet.escapeHtml((String)request.getAttribute("error")) %></p><% } %>
<% List<InvitationView> invitations=(List<InvitationView>)request.getAttribute("invitations"); if(invitations==null||invitations.isEmpty()){ %><p class="empty">You have no invitations.</p><% } else { for(InvitationView invitation:invitations){ %>
<article class="card workflow-card"><div class="row"><div><h2><%= TeamWorkflowServlet.escapeHtml(invitation.getTitle()) %></h2><p class="meta"><span>From <%= TeamWorkflowServlet.escapeHtml(invitation.getLeader()) %></span><span class="status-badge status-<%= invitation.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= TeamWorkflowServlet.displayStatus(invitation.getStatus()) %></span><span><%= TeamWorkflowServlet.escapeHtml(invitation.getDate()) %></span></p>
<% if(invitation.getMessage()!=null&&!invitation.getMessage().isBlank()){ %><p><strong>Message:</strong> <%= TeamWorkflowServlet.escapeHtml(invitation.getMessage()) %></p><% } %></div>
<% if("PENDING".equals(invitation.getStatus())){ %><div class="actions"><form method="post" action="${pageContext.request.contextPath}/invitations"><input type="hidden" name="invitation_id" value="<%= invitation.getInvitationId() %>"><input type="hidden" name="decision" value="accepted"><button type="submit">Accept invitation</button></form><form method="post" action="${pageContext.request.contextPath}/invitations"><input type="hidden" name="invitation_id" value="<%= invitation.getInvitationId() %>"><input type="hidden" name="decision" value="declined"><button class="secondary" type="submit">Decline</button></form></div><% } %></div></article>
<% } } %></main></div></body></html>

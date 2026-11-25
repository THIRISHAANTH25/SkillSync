<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, edu.college.smartmatcher.web.TeamWorkflowServlet, edu.college.smartmatcher.web.TeamWorkflowServlet.RequestView" %>
<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    
<title>My Requests | Smart Project Team Matcher</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head><body><div class="shell"><header class="topbar"><strong>Smart Project Team Matcher</strong><span>Student workspace</span></header>
<jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="my-requests"/></jsp:include>
<main><h1>My Requests</h1><p class="intro">Track applications you have sent to open projects.</p>
<% if(request.getAttribute("notice")!=null){ %><p class="message" role="status"><%= TeamWorkflowServlet.escapeHtml((String)request.getAttribute("notice")) %></p><% } %>
<% if(request.getAttribute("error")!=null){ %><p class="error" role="alert"><%= TeamWorkflowServlet.escapeHtml((String)request.getAttribute("error")) %></p><% } %>
<% List<RequestView> requests=(List<RequestView>)request.getAttribute("requests"); if(requests==null||requests.isEmpty()){ %><p class="empty">You have not sent any project requests.</p><% } else { for(RequestView item:requests){ %>
<article class="card workflow-card"><div class="row"><div><h2><%= TeamWorkflowServlet.escapeHtml(item.getTitle()) %></h2><p class="meta"><span class="status-badge status-<%= item.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= TeamWorkflowServlet.displayStatus(item.getStatus()) %></span><span>Sent <%= TeamWorkflowServlet.escapeHtml(item.getDate()) %></span></p>
<% if(item.getMessage()!=null&&!item.getMessage().isBlank()){ %><p><strong>Your message:</strong> <%= TeamWorkflowServlet.escapeHtml(item.getMessage()) %></p><% } %></div>
<% if("PENDING".equals(item.getStatus())){ %><form method="post" action="${pageContext.request.contextPath}/my-requests"><input type="hidden" name="application_id" value="<%= item.getApplicationId() %>"><button type="submit">Withdraw pending request</button></form><% } %></div></article>
<% } } %></main></div></body></html>

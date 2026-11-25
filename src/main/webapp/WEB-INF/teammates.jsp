<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet.ProjectView" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet.Recommendation" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet.MatchedSkill" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet.MissingSkill" %>
<%@ page import="edu.college.smartmatcher.web.TeamWorkflowServlet" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Find Teammates | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell">
    <header class="topbar"><p class="brand"><img class="topbar-logo" src="${pageContext.request.contextPath}/assets/skillsync-logo-symbol-transparent.png?v=20261018-transparent1" alt=""><span class="topbar-title">SkillSync</span></p><span class="topbar-context">Student workspace</span></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="teammates"/></jsp:include>
    <main>
        <p class="eyebrow">TEAM DISCOVERY</p>
        <h1>Find Your Perfect Teammates</h1>
        <p class="intro">Discover active students whose saved strengths align with your project priorities.</p>
        <% if (request.getAttribute("notice") != null) { %>
        <p class="message" role="status"><%= TeamWorkflowServlet.escapeHtml((String) request.getAttribute("notice")) %></p>
        <% } %>
        <% if (request.getAttribute("error") != null) { %>
        <p class="error" role="alert"><%= TeamWorkflowServlet.escapeHtml(String.valueOf(request.getAttribute("error"))) %></p>
        <% } %>
        <% List<ProjectView> projects = (List<ProjectView>) request.getAttribute("projects"); %>
        <% if (projects == null || projects.isEmpty()) { %>
        <section class="panel no-owned-projects">
            <span class="empty-state-icon" aria-hidden="true">⌂</span>
            <h2>You haven't created a project yet.</h2>
            <p>Create a project first, then find students whose saved skills fit your requirements.</p>
            <a class="primary-link" href="${pageContext.request.contextPath}/projects">Create Project</a>
        </section>
        <% } else { %>
        <section class="my-projects-section" aria-labelledby="my-projects-heading">
            <div class="section-heading"><div><p class="eyebrow">YOUR WORKSPACE</p><h2 id="my-projects-heading">My Projects</h2></div>
                <a class="action-link" href="${pageContext.request.contextPath}/projects">Create or edit projects <span aria-hidden="true">→</span></a></div>
            <nav class="project-selector" aria-label="Jump to one of your projects">
                <% for (ProjectView project : projects) { %>
                <a href="#owned-project-<%= project.getProjectId() %>"><%= FindTeammatesServlet.escapeHtml(project.getTitle()) %>
                    <span class="status-badge status-<%= project.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= TeamWorkflowServlet.displayStatus(project.getStatus()) %></span></a>
                <% } %>
            </nav>
        </section>
        <% String inviteCsrfToken = (String) request.getAttribute("inviteCsrfToken"); %>
        <% for (ProjectView project : projects) { %>
        <section class="project-card teammate-project" id="owned-project-<%= project.getProjectId() %>" aria-labelledby="project-title-<%= project.getProjectId() %>">
            <div class="teammate-project-heading">
                <div><p class="eyebrow">PROJECT · <%= FindTeammatesServlet.escapeHtml(project.getCreatedDate()) %></p>
                    <h2 id="project-title-<%= project.getProjectId() %>"><%= FindTeammatesServlet.escapeHtml(project.getTitle()) %></h2></div>
                <span class="status-badge status-<%= project.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= TeamWorkflowServlet.displayStatus(project.getStatus()) %></span>
            </div>
            <p class="description"><%= FindTeammatesServlet.escapeHtml(project.getDescription()) %></p>
            <div class="teammate-project-facts">
                <div><span>Team capacity</span><strong><%= project.getTeamSize() %> members</strong></div>
                <div><span>Current team</span><strong><%= project.getOccupiedSlots() %> / <%= project.getTeamSize() %></strong></div>
                <div><span>Available seats</span><strong><%= project.getAvailableSeats() %></strong></div>
                <% if (project.getPendingInvitations() > 0) { %><div><span>Pending invitations</span><strong><%= project.getPendingInvitations() %></strong></div><% } %>
            </div>
            <div class="skills"><strong>Required skills</strong><div class="skill-chips">
                <% if (project.getRequiredSkills().isEmpty()) { %><span class="empty">No required skills listed.</span><% } else { for (String skill : project.getRequiredSkills()) { %>
                <span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(skill) %></span>
                <% } } %>
            </div></div>

            <% if (!project.canShowRecommendationsToOwner()) { %>
            <div class="teammate-unavailable" role="status">
                <% if (!"OPEN".equals(project.getStatus())) { %>
                Recommendations and invitations are available only for open projects.
                <% } else if (project.getOccupiedSlots() >= project.getTeamSize()) { %>
                This project team is full.
                <% } else { %>
                This project has no available seats. Pending invitations reserve seats until students respond.
                <% } %>
            </div>
            <% } else { %>
            <div class="section-heading recommendation-heading"><div><p class="eyebrow">SKILL MATCHES</p><h3>Recommended Teammates for <%= FindTeammatesServlet.escapeHtml(project.getTitle()) %></h3></div></div>
            <% List<Recommendation> recommendations = project.getRecommendations(); %>
            <% if (project.getRequiredSkills().isEmpty()) { %>
            <p class="empty">Add required skills to this project to receive skill-based recommendations.</p>
            <% } else if (recommendations.isEmpty()) { %>
            <p class="empty">No suitable students are available yet. Recommendations appear when eligible students have saved matching skills.</p>
            <% } else { %>
            <div class="teammate-recommendations">
                <% for (Recommendation match : recommendations) { %>
                <article class="recommendation teammate-recommendation">
                    <div class="teammate-student">
                        <div class="teammate-avatar" aria-hidden="true"><%= FindTeammatesServlet.escapeHtml(match.getFullName().substring(0, 1).toUpperCase(java.util.Locale.ROOT)) %></div>
                        <div><h4><%= FindTeammatesServlet.escapeHtml(match.getFullName()) %></h4>
                            <p class="meta"><%= FindTeammatesServlet.escapeHtml(match.getDepartment()) %> · Year <%= match.getYearOfStudy() %></p></div>
                    </div>
                    <div class="teammate-skills"><strong>Matched skills</strong><div class="skill-chips">
                        <% for (MatchedSkill matchedSkill : match.getMatchedSkills()) { %><span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(matchedSkill.getName()) %><span class="proficiency-badge proficiency-<%= matchedSkill.getProficiency().toLowerCase(java.util.Locale.ROOT) %>"><%= FindTeammatesServlet.escapeHtml(matchedSkill.getProficiency()) %></span><small class="skill-priority">Priority <%= matchedSkill.getImportance() %>/5</small></span><% } %>
                    </div></div>
                    <% if (!match.getMissingSkills().isEmpty()) { %>
                    <div class="teammate-skills missing-skills"><strong>Still needed</strong><div class="skill-chips">
                        <% for (MissingSkill missing : match.getMissingSkills()) { %><span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(missing.getName()) %><small class="skill-priority">Priority <%= missing.getImportance() %>/5</small></span><% } %>
                    </div></div>
                    <% } %>
                    <div class="match-score"><span class="score"><%= match.getScore().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() %>%</span><span class="match-caption">compatibility</span>
                        <div class="match-meter" role="progressbar" aria-label="Skill compatibility percentage" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<%= match.getScore().toPlainString() %>" style="--match-value:<%= match.getScore().toPlainString() %>%"><span></span></div></div>
                    <% if (match.isInvitationActive()) { %>
                    <button type="button" class="invitation-state" disabled><%= "ACCEPTED".equals(match.getInvitationStatus()) ? "Invitation Accepted" : "Invitation Sent" %></button>
                    <% } else if (match.hasActiveRequest()) { %>
                    <button type="button" class="invitation-state" disabled>Request Already Active</button>
                    <% } else if (!project.isEligible()) { %>
                    <button type="button" class="invitation-state" disabled>No Seats Available</button>
                    <% } else { %>
                    <form class="invite-teammate-form" method="post" action="${pageContext.request.contextPath}/invitations">
                        <input type="hidden" name="action" value="invite">
                        <input type="hidden" name="csrf_token" value="<%= FindTeammatesServlet.escapeHtml(inviteCsrfToken) %>">
                        <input type="hidden" name="project_id" value="<%= project.getProjectId() %>">
                        <input type="hidden" name="student_id" value="<%= match.getStudentId() %>">
                        <label>Optional message <input type="text" name="message" maxlength="1000" placeholder="Add a short note"></label>
                        <button type="submit">Invite Teammate <span aria-hidden="true">→</span></button>
                    </form>
                    <% } %>
                </article>
                <% } %>
            </div>
            <p class="matching-note">Match % = sum(importance × proficiency fraction) ÷ sum of requirement importance × 100. Project importance is 1–5. Proficiency levels 1–2 contribute 20%, levels 3–4 contribute 60%, and level 5 contributes 100%. Missing skills contribute 0; scores are rounded to two decimals.</p>
            <% } %>
            <% } %>
        </section>
        <% } %>
        <% } %>
    </main>
</div>
</body>
</html>

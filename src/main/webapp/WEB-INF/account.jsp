<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="edu.college.smartmatcher.web.StudentDashboardService" %>
<%
    StudentDashboardService.DashboardData dashboard = (StudentDashboardService.DashboardData) request.getAttribute("dashboard");
%>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Student Dashboard | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell" id="dashboard">
    <header class="topbar">
        <p class="brand"><img class="topbar-logo" src="${pageContext.request.contextPath}/assets/skillsync-logo-symbol-transparent.png?v=20261018-transparent1" alt=""><span class="topbar-title">SkillSync</span></p>
        <span class="topbar-context">Student workspace · Dashboard</span>
        <div class="topbar-actions"><a class="topbar-profile-link" href="#profile-editor"><span class="topbar-avatar"><%= dashboard == null ? "S" : dashboard.getInitial() %></span><span><%= dashboard == null ? "Profile" : dashboard.getName() %></span></a></div>
    </header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="account"/></jsp:include>
    <main class="student-dashboard">
        <% if (request.getAttribute("dashboardError") != null) { %>
            <p class="error" role="alert"><%= request.getAttribute("dashboardError") %></p>
        <% } else if (dashboard != null) { %>
            <section class="dashboard-hero" aria-labelledby="welcome-heading">
                <div><p class="eyebrow">YOUR COLLABORATION SPACE · <%= dashboard.getToday() %></p>
                    <h1 id="welcome-heading">Welcome back, <%= dashboard.getName() %></h1>
                    <p class="intro">Build your profile, discover compatible teammates, and move your projects forward.</p>
                    <p class="completed-project-summary">Completed projects <strong><%= dashboard.getCompletedProjects() %></strong></p>
                    <div class="dashboard-actions"><a class="primary-link" href="${pageContext.request.contextPath}/projects">Create project <span aria-hidden="true">↗</span></a>
                        <a class="quiet-link" href="${pageContext.request.contextPath}/teammates">Find teammates</a>
                        <a class="quiet-link" href="${pageContext.request.contextPath}/my-teams">My teams</a></div>
                </div>
                <a class="profile-completion-card" href="#profile-editor" aria-label="Profile is <%= dashboard.getProfileCompletion() %> percent complete. Edit profile.">
                    <span class="profile-completion-ring" style="--completion:<%= dashboard.getProfileCompletion() %>%"><strong><%= dashboard.getProfileCompletion() %>%</strong></span>
                    <span><strong>Profile completion</strong><small>Complete your profile to help teams discover you.</small></span>
                </a>
            </section>

            <% if (request.getAttribute("profileNotice") != null) { %><p class="success" role="status"><%= request.getAttribute("profileNotice") %></p><% } %>
            <% if (request.getAttribute("profileError") != null) { %><p class="error" role="alert"><%= request.getAttribute("profileError") %></p><% } %>

            <section class="dashboard-stat-grid" aria-label="Your project statistics">
                <article class="dashboard-stat"><span class="stat-icon stat-projects" aria-hidden="true">▦</span><div><strong><%= dashboard.getActiveProjects() %></strong><span>My active projects</span></div><small>Open or in progress</small></article>
                <article class="dashboard-stat"><span class="stat-icon stat-teams" aria-hidden="true">◎</span><div><strong><%= dashboard.getActiveTeams() %></strong><span>Active teams</span></div><small>Teams you belong to</small></article>
                <article class="dashboard-stat"><span class="stat-icon stat-invitations" aria-hidden="true">✉</span><div><strong><%= dashboard.getPendingInvitations() %></strong><span>Pending invitations</span></div><small><a href="${pageContext.request.contextPath}/invitations">Review invitations</a></small></article>
                <article class="dashboard-stat"><span class="stat-icon stat-complete" aria-hidden="true">✓</span><div><strong aria-label="Not tracked yet">—</strong><span>Completed tasks</span></div><small>Task tracking will be available in Project Management.</small></article>
            </section>

            <div class="dashboard-content-grid">
                <section class="dashboard-panel dashboard-projects" aria-labelledby="active-projects-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">PROJECT WORKSPACE</p><h2 id="active-projects-heading">My active projects</h2></div><a href="${pageContext.request.contextPath}/projects">View projects <span aria-hidden="true">→</span></a></div>
                    <% if (dashboard.getProjects().isEmpty()) { %>
                        <div class="dashboard-empty"><span aria-hidden="true">✦</span><strong>No active projects yet</strong><p>Create a project or explore open opportunities to start building your team.</p><a href="${pageContext.request.contextPath}/projects">Create your first project</a></div>
                    <% } else { %>
                        <div class="dashboard-project-list">
                        <% for (StudentDashboardService.ProjectCard project : dashboard.getProjects()) { %>
                            <article class="dashboard-project-card">
                                <div class="dashboard-project-title"><div><h3><%= project.getTitle() %></h3><p><%= project.getDescription() %></p></div><span class="status-badge status-<%= project.getStatus().toLowerCase(java.util.Locale.ROOT).replace('_','-') %>"><%= project.getStatus().replace('_',' ') %></span></div>
                                <div class="dashboard-project-meta"><span><strong><%= project.getOccupied() %></strong> / <%= project.getCapacity() %> team members</span><span><%= project.getSkills().isBlank() ? "Skills not specified" : project.getSkills() %></span></div>
                                <div class="project-progress-line" role="img" aria-label="<%= project.getPercent() %> percent of team capacity filled"><span style="width:<%= project.getPercent() %>%"></span></div>
                                <a class="text-link" href="${pageContext.request.contextPath}/projects">Manage project <span aria-hidden="true">→</span></a>
                            </article>
                        <% } %></div>
                    <% } %>
                </section>

                <section class="dashboard-panel dashboard-skill-profile" aria-labelledby="skill-profile-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">YOUR STRENGTHS</p><h2 id="skill-profile-heading">Skill profile</h2></div><a href="${pageContext.request.contextPath}/skills">Edit skills <span aria-hidden="true">→</span></a></div>
                    <% if (dashboard.getSkills().isEmpty()) { %><div class="dashboard-empty compact"><strong>Add your first skill</strong><p>Skills help the matcher find relevant teammates and projects.</p><a href="${pageContext.request.contextPath}/skills">Manage my skills</a></div>
                    <% } else { %><div class="dashboard-skill-list"><% for (StudentDashboardService.SkillItem skill : dashboard.getSkills()) { %>
                        <div class="dashboard-skill"><div><strong><%= skill.getName() %></strong><span><%= skill.getLevel() %></span></div><div class="skill-meter" role="img" aria-label="<%= skill.getName() %>: <%= skill.getLevel() %>"><span style="width:<%= skill.getPercent() %>%"></span></div></div>
                    <% } %></div><% } %>
                    <div class="dashboard-interest-list"><strong>Interests</strong><p><%= dashboard.getInterests().isEmpty() ? "Add interests to make your profile more personal." : String.join(" · ", dashboard.getInterests()) %></p></div>
                </section>

                <section class="dashboard-panel" aria-labelledby="recommended-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">SKILL MATCHING</p><h2 id="recommended-heading">Recommended teammates</h2></div><a href="${pageContext.request.contextPath}/teammates">Explore matches <span aria-hidden="true">→</span></a></div>
                    <% if (dashboard.getRecommendations().isEmpty()) { %><div class="dashboard-empty compact"><strong>Ready to find your next teammate?</strong><p>Open Find Teammates to refresh recommendations for projects you own.</p><a href="${pageContext.request.contextPath}/teammates">Find teammates</a></div>
                    <% } else { %><div class="dashboard-recommendation-list"><% for (StudentDashboardService.DashboardRecommendation recommendation : dashboard.getRecommendations()) { %>
                        <article class="dashboard-recommendation"><span class="mini-avatar"><%= recommendation.getName().substring(0,1) %></span><div><strong><%= recommendation.getName() %></strong><small><%= recommendation.getDepartment() %> · Year <%= recommendation.getYear() %></small><small>For <%= recommendation.getProject() %></small></div><b><%= recommendation.getScore() %>%</b></article>
                    <% } %></div><p class="data-note">Scores are from your last Find Teammates calculation.</p><% } %>
                </section>

                <section class="dashboard-panel" aria-labelledby="team-coverage-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">TEAM CAPABILITIES</p><h2 id="team-coverage-heading">Team skill coverage</h2></div><a href="${pageContext.request.contextPath}/my-teams">View teams <span aria-hidden="true">→</span></a></div>
                    <% if (dashboard.getTeamCoverage().isEmpty()) { %><div class="dashboard-empty compact"><strong>Your team insights will appear here</strong><p>Coverage compares project requirements with the skills recorded by current team members.</p></div>
                    <% } else { %><div class="coverage-list"><% for (StudentDashboardService.TeamCoverage coverage : dashboard.getTeamCoverage()) { %><div class="coverage-item"><div><strong><%= coverage.getProject() %></strong><span><%= coverage.getCovered() %> of <%= coverage.getRequired() %> required skills recorded</span></div><strong class="coverage-percent"><%= coverage.getPercent() %>%</strong><div class="coverage-meter" role="img" aria-label="<%= coverage.getPercent() %> percent skill coverage"><span style="width:<%= coverage.getPercent() %>%"></span></div></div><% } %></div><% } %>
                </section>

                <section class="dashboard-panel" aria-labelledby="activity-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">WHAT'S HAPPENING</p><h2 id="activity-heading">Recent activity</h2></div></div>
                    <% if (dashboard.getActivity().isEmpty()) { %><div class="dashboard-empty compact"><strong>Your recent activity will show here</strong><p>Project creation, requests, invitations, and team joins are shown as they happen.</p></div>
                    <% } else { %><ul class="activity-list"><% for (StudentDashboardService.ActivityItem item : dashboard.getActivity()) { %><li><span class="activity-dot" aria-hidden="true"></span><span><strong><%= item.getActivity() %></strong><small><%= item.getDate() %></small></span></li><% } %></ul><% } %>
                </section>

                <section class="dashboard-panel deadline-panel" aria-labelledby="deadlines-heading">
                    <div class="dashboard-section-heading"><div><p class="eyebrow">PROJECT PLANNING</p><h2 id="deadlines-heading">Upcoming deadlines</h2></div></div>
                    <div class="dashboard-empty compact"><strong>No deadline data yet</strong><p>Project milestones and due dates will appear when project tracking is enabled.</p></div>
                </section>
            </div>

            <details class="dashboard-panel profile-editor" id="profile-editor" <%= request.getAttribute("profileError") != null ? "open" : "" %>>
                <summary><span><strong>Edit your profile</strong><small>Keep your information and availability current.</small></span><span aria-hidden="true">＋</span></summary>
                <form action="${pageContext.request.contextPath}/account" method="post" class="profile-edit-form">
                    <input type="hidden" name="csrf_token" value="${profileCsrfToken}">
                    <div class="profile-form-grid">
                        <label>Full name<input name="full_name" maxlength="160" required value="<%= dashboard.getName() %>"></label>
                        <label>Department<input name="department" maxlength="120" required value="<%= dashboard.getDepartment() %>"></label>
                        <label>Year of study<select name="year_of_study" required><% for (int year = 1; year <= 8; year++) { %><option value="<%= year %>" <%= year == dashboard.getYear() ? "selected" : "" %>>Year <%= year %></option><% } %></select></label>
                        <label>College email<input type="email" readonly value="<%= dashboard.getEmail() %>"></label>
                        <label class="profile-bio-field">Short bio<textarea name="bio" maxlength="1000" rows="4" placeholder="Tell teammates what you enjoy building"><%= dashboard.getBio() %></textarea></label>
                        <label>Team availability<select name="availability_status"><option value="" <%= dashboard.getAvailability() == null || dashboard.getAvailability().isBlank() ? "selected" : "" %>>Not specified</option><option value="AVAILABLE" <%= "AVAILABLE".equals(dashboard.getAvailability()) ? "selected" : "" %>>Available</option><option value="LIMITED" <%= "LIMITED".equals(dashboard.getAvailability()) ? "selected" : "" %>>Limited availability</option><option value="UNAVAILABLE" <%= "UNAVAILABLE".equals(dashboard.getAvailability()) ? "selected" : "" %>>Not available</option></select></label>
                    </div>
                    <fieldset><legend>Interests <span class="muted">(choose up to 6)</span></legend><div class="profile-interest-options"><% for (StudentDashboardService.InterestOption interest : dashboard.getInterestOptions()) { %>
                        <label class="interest-choice"><input type="checkbox" name="interest_ids" value="<%= interest.getId() %>" <%= interest.isSelected() ? "checked" : "" %>><span><%= interest.getName() %></span></label>
                    <% } %></div></fieldset>
                    <button type="submit">Save profile</button>
                </form>
            </details>
        <% } %>
    </main>
</div>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var checks = document.querySelectorAll('.profile-interest-options input[type="checkbox"]');
    checks.forEach(function (check) { check.addEventListener("change", function () {
        var selected = document.querySelectorAll('.profile-interest-options input[type="checkbox"]:checked');
        if (selected.length > 6) { check.checked = false; check.closest("label").classList.add("limit-shake"); window.setTimeout(function(){check.closest("label").classList.remove("limit-shake");},220); }
    }); });
});
</script>
</body>
</html>

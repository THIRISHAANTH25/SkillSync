<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, edu.college.smartmatcher.web.ProjectServlet, edu.college.smartmatcher.web.ProjectServlet.ProjectView, edu.college.smartmatcher.web.ProjectServlet.ProjectSkill, edu.college.smartmatcher.web.ProjectServlet.SkillOption" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    
    <title>Projects | Smart Project Team Matcher</title>
    
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell">
    <header class="topbar">
        <p class="brand">Smart Project Team Matcher</p>
        <span>Student workspace</span>
    </header>

    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="projects"/></jsp:include>

    <main>
        <h1>Projects</h1>
        <p class="intro">Create a project, manage your own listings, and find open opportunities from other students.</p>

        <% if (request.getAttribute("notice") != null) { %>
        <p class="message" role="status"><%= request.getAttribute("notice") %></p>
        <% } %>
        <% if (request.getAttribute("error") != null) { %>
        <p class="error" role="alert"><%= request.getAttribute("error") %></p>
        <% } %>

        <% List<SkillOption> skillOptions = (List<SkillOption>) request.getAttribute("skillOptions"); %>
        <% List<ProjectView> ownProjects = (List<ProjectView>) request.getAttribute("ownProjects"); %>
        <% List<ProjectView> openProjects = (List<ProjectView>) request.getAttribute("openProjects"); %>
        <% String statusFilter = (String) request.getAttribute("projectStatusFilter"); if (statusFilter == null) statusFilter = "ALL"; %>
        <% Long skillFilter = (Long) request.getAttribute("projectSkillFilter"); if (skillFilter == null) skillFilter = 0L; %>
        <% String projectSort = (String) request.getAttribute("projectSort"); if (projectSort == null) projectSort = "newest"; %>

        <form class="project-filter-bar" method="get" action="${pageContext.request.contextPath}/projects" aria-label="Filter projects">
            <label>Status
                <select name="status">
                    <option value="ALL" <%= "ALL".equals(statusFilter) ? "selected" : "" %>>All statuses</option>
                    <option value="OPEN" <%= "OPEN".equals(statusFilter) ? "selected" : "" %>>Open</option>
                    <option value="CLOSED" <%= "CLOSED".equals(statusFilter) ? "selected" : "" %>>Closed</option>
                    <option value="IN_PROGRESS" <%= "IN_PROGRESS".equals(statusFilter) ? "selected" : "" %>>In progress</option>
                    <option value="COMPLETED" <%= "COMPLETED".equals(statusFilter) ? "selected" : "" %>>Completed</option>
                    <option value="CANCELLED" <%= "CANCELLED".equals(statusFilter) ? "selected" : "" %>>Cancelled</option>
                </select>
            </label>
            <label>Required skill
                <select name="skill_id">
                    <option value="0">Any skill</option>
                    <% if (skillOptions != null) { for (SkillOption skill : skillOptions) { %>
                    <option value="<%= skill.getSkillId() %>" <%= skillFilter == skill.getSkillId() ? "selected" : "" %>><%= ProjectServlet.escapeHtml(skill.getSkillName()) %></option>
                    <% } } %>
                </select>
            </label>
            <label>Creation date
                <select name="sort">
                    <option value="newest" <%= "newest".equals(projectSort) ? "selected" : "" %>>Newest first</option>
                    <option value="oldest" <%= "oldest".equals(projectSort) ? "selected" : "" %>>Oldest first</option>
                </select>
            </label>
            <div class="project-filter-actions"><button type="submit">Apply filters</button><a class="quiet-link" href="${pageContext.request.contextPath}/projects">Clear</a></div>
        </form>

        <section class="panel" aria-labelledby="create-heading">
            <h2 id="create-heading">Create a project</h2>
            <form class="project-form" method="post" action="${pageContext.request.contextPath}/projects">
                <input type="hidden" name="action" value="create">
                <div class="form-grid">
                    <label>Project title
                        <input name="title" maxlength="180" required>
                    </label>
                    <label>Team members needed
                        <input type="number" name="required_team_size" min="2" max="20" value="3" required>
                    </label>
                    <label>Status
                        <select name="status" required>
                            <option value="OPEN">Open</option>
                            <option value="CLOSED">Closed</option>
                        </select>
                    </label>
                </div>
                <label>Description
                    <textarea name="description" maxlength="5000" required></textarea>
                </label>
                <fieldset>
                    <legend>Required skills (choose at least one)</legend>
                    <% if (skillOptions == null || skillOptions.isEmpty()) { %>
                    <p class="empty">The skill catalog is empty. <a href="${pageContext.request.contextPath}/skills">Add skills</a> before creating a project.</p>
                    <% } else { %>
                    <div class="skill-options">
                        <% for (SkillOption skill : skillOptions) { %>
                        <label class="skill-option"><input type="checkbox" name="skill_ids" value="<%= skill.getSkillId() %>"> <%= ProjectServlet.escapeHtml(skill.getSkillName()) %></label>
                        <% } %>
                    </div>
                    <% } %>
                </fieldset>
                <div><button type="submit">Create project</button></div>
            </form>
        </section>

        <section aria-labelledby="your-projects-heading">
            <h2 id="your-projects-heading">Projects you created</h2>
            <% if (ownProjects == null || ownProjects.isEmpty()) { %>
            <p class="empty">You have not created any projects yet.</p>
            <% } else { %>
            <div class="project-grid">
                <% for (ProjectView project : ownProjects) { %>
                <article class="project-card" id="project-<%= project.getProjectId() %>">
                    <h2><%= ProjectServlet.escapeHtml(project.getTitle()) %></h2>
                    <p class="project-meta"><span class="status-badge status-<%= project.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= ProjectServlet.escapeHtml(project.getStatus()) %></span><span><%= project.getTeamSize() %> team members needed</span><span>Created <%= ProjectServlet.escapeHtml(project.getCreatedDate()) %></span></p>
                    <p class="description"><%= ProjectServlet.escapeHtml(project.getDescription()) %></p>
                    <div class="skill-list"><strong>Required skills</strong><div class="skill-chips">
                        <% if (project.getSkills().isEmpty()) { %><span class="empty">None recorded</span><% } else { %>
                        <% for (ProjectSkill requiredSkill : project.getSkills()) { %><span class="skill-chip"><%= ProjectServlet.escapeHtml(requiredSkill.getSkillName()) %></span><% } %>
                        <% } %>
                    </div></div>
                    <details>
                        <summary>Edit project</summary>
                        <form class="project-form" method="post" action="${pageContext.request.contextPath}/projects">
                            <input type="hidden" name="action" value="update">
                            <input type="hidden" name="project_id" value="<%= project.getProjectId() %>">
                            <label>Project title
                                <input name="title" maxlength="180" value="<%= ProjectServlet.escapeHtml(project.getTitle()) %>" required>
                            </label>
                            <label>Description
                                <textarea name="description" maxlength="5000" required><%= ProjectServlet.escapeHtml(project.getDescription()) %></textarea>
                            </label>
                            <div class="form-grid">
                                <label>Team members needed
                                    <input type="number" name="required_team_size" min="2" max="20" value="<%= project.getTeamSize() %>" required>
                                </label>
                                <label>Status
                                    <select name="status" required>
                                        <option value="OPEN" <%= "OPEN".equals(project.getStatus()) ? "selected" : "" %>>Open</option>
                                        <option value="CLOSED" <%= "CLOSED".equals(project.getStatus()) ? "selected" : "" %>>Closed</option>
                                        <% if ("IN_PROGRESS".equals(project.getStatus()) || "COMPLETED".equals(project.getStatus()) || "CANCELLED".equals(project.getStatus())) { %>
                                        <option value="<%= ProjectServlet.escapeHtml(project.getStatus()) %>" selected>Existing status: <%= ProjectServlet.escapeHtml(project.getStatus()) %></option>
                                        <% } %>
                                    </select>
                                </label>
                            </div>
                            <fieldset>
                                <legend>Required skills</legend>
                                <% if (skillOptions == null || skillOptions.isEmpty()) { %>
                                <p class="empty">Add a skill to the catalog before editing project requirements.</p>
                                <% } else { %>
                                <div class="skill-options">
                                    <% for (SkillOption skill : skillOptions) { %>
                                    <label class="skill-option"><input type="checkbox" name="skill_ids" value="<%= skill.getSkillId() %>" <%= project.hasSkill(skill.getSkillId()) ? "checked" : "" %>> <%= ProjectServlet.escapeHtml(skill.getSkillName()) %></label>
                                    <% } %>
                                </div>
                                <% } %>
                            </fieldset>
                            <div class="project-actions"><button type="submit">Save changes</button></div>
                        </form>
                        <form method="post" action="${pageContext.request.contextPath}/projects" onsubmit="return confirm('Delete this project?');">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="project_id" value="<%= project.getProjectId() %>">
                            <div class="project-actions"><button class="secondary" type="submit">Delete project</button></div>
                        </form>
                    </details>
                </article>
                <% } %>
            </div>
            <% } %>
        </section>

        <section aria-labelledby="open-projects-heading">
            <h2 id="open-projects-heading">Open projects from other students</h2>
            <% if (openProjects == null || openProjects.isEmpty()) { %>
            <p class="empty">There are no open projects from other students right now.</p>
            <% } else { %>
            <div class="project-grid">
                <% for (ProjectView project : openProjects) { %>
                <article class="project-card" id="project-<%= project.getProjectId() %>">
                    <h2><%= ProjectServlet.escapeHtml(project.getTitle()) %></h2>
                    <p class="project-meta"><span class="status-badge status-open">Open</span><span>By <%= ProjectServlet.escapeHtml(project.getCreatorName()) %></span><span><%= project.getTeamSize() %> team members needed</span><span>Posted <%= ProjectServlet.escapeHtml(project.getCreatedDate()) %></span></p>
                    <p class="description"><%= ProjectServlet.escapeHtml(project.getDescription()) %></p>
                    <div class="skill-list"><strong>Required skills</strong><div class="skill-chips">
                        <% if (project.getSkills().isEmpty()) { %><span class="empty">None recorded</span><% } else { %>
                        <% for (ProjectSkill requiredSkill : project.getSkills()) { %><span class="skill-chip"><%= ProjectServlet.escapeHtml(requiredSkill.getSkillName()) %></span><% } %>
                        <% } %>
                    </div></div>
                    <details>
                        <summary>Request to join</summary>
                        <form class="project-form" method="post" action="${pageContext.request.contextPath}/join">
                            <input type="hidden" name="project_id" value="<%= project.getProjectId() %>">
                            <label>Optional message
                                <textarea name="message" maxlength="1000" placeholder="Tell the project leader why you are interested"></textarea>
                            </label>
                            <div class="project-actions"><button type="submit">Send request</button></div>
                        </form>
                    </details>
                </article>
                <% } %>
            </div>
            <% } %>
        </section>
    </main>
</div>
</body>
</html>

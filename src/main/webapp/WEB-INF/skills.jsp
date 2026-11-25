<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List, edu.college.smartmatcher.web.SkillServlet, edu.college.smartmatcher.web.SkillServlet.SkillRow" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    
    <title>My Skills | Smart Project Team Matcher</title>
    
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell">
    <header class="topbar">
        <p class="brand">Smart Project Team Matcher</p>
        <span>Student workspace</span>
    </header>

    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="skills"/></jsp:include>

    <main>
        <h1>My Skills</h1>
        <p class="intro">Add the skills you can bring to a project and keep your proficiency up to date.</p>

        <% if (request.getAttribute("notice") != null) { %>
        <p class="message" role="status"><%= request.getAttribute("notice") %></p>
        <% } %>
        <% if (request.getAttribute("error") != null) { %>
        <p class="error" role="alert"><%= request.getAttribute("error") %></p>
        <% } %>

        <section class="panel" aria-labelledby="add-heading">
            <h2 id="add-heading">Add a skill</h2>
            <form class="add-form" method="post" action="${pageContext.request.contextPath}/skills">
                <input type="hidden" name="action" value="add">
                <label>Skill name
                    <input type="text" name="skill_name" maxlength="100" required autocomplete="off">
                </label>
                <label>Proficiency
                    <select name="proficiency" required>
                        <option value="beginner">Beginner</option>
                        <option value="intermediate" selected>Intermediate</option>
                        <option value="advanced">Advanced</option>
                    </select>
                </label>
                <button type="submit">Add skill</button>
            </form>
        </section>

        <section class="panel" aria-labelledby="saved-heading">
            <h2 id="saved-heading">Your saved skills</h2>
            <% List<SkillRow> skills = (List<SkillRow>) request.getAttribute("skills"); %>
            <% if (skills == null || skills.isEmpty()) { %>
            <p class="empty">You have not added any skills yet.</p>
            <% } else { %>
            <div class="skill-list">
                <% for (SkillRow skill : skills) { %>
                <article class="skill-row">
                    <div class="skill-title">
                        <h3><%= SkillServlet.escapeHtml(skill.getSkillName()) %></h3>
                        <% String proficiencyLabel = skill.getProficiencyLevel() <= 2 ? "Beginner" : (skill.getProficiencyLevel() <= 4 ? "Intermediate" : "Advanced"); %>
                        <span class="proficiency-badge proficiency-<%= proficiencyLabel.toLowerCase(java.util.Locale.ROOT) %>"><%= proficiencyLabel %></span>
                    </div>
                    <div class="skill-actions">
                        <form method="post" action="${pageContext.request.contextPath}/skills">
                            <input type="hidden" name="action" value="update">
                            <input type="hidden" name="skill_id" value="<%= skill.getSkillId() %>">
                            <label>Proficiency
                                <select name="proficiency" required>
                                    <option value="beginner" <%= skill.getProficiencyLevel() <= 2 ? "selected" : "" %>>Beginner</option>
                                    <option value="intermediate" <%= skill.getProficiencyLevel() >= 3 && skill.getProficiencyLevel() <= 4 ? "selected" : "" %>>Intermediate</option>
                                    <option value="advanced" <%= skill.getProficiencyLevel() >= 5 ? "selected" : "" %>>Advanced</option>
                                </select>
                            </label>
                            <button type="submit">Save</button>
                        </form>
                        <form method="post" action="${pageContext.request.contextPath}/skills">
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="skill_id" value="<%= skill.getSkillId() %>">
                            <button class="secondary" type="submit">Remove</button>
                        </form>
                    </div>
                </article>
                <% } %>
            </div>
            <% } %>
        </section>
    </main>
</div>
</body>
</html>

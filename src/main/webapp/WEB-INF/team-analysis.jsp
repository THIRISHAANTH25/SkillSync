<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.ProjectOption" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.SkillRow" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.MemberRow" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.CandidateDisplay" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.CandidateSkill" %>
<%@ page import="edu.college.smartmatcher.web.TeamAnalysisServlet.MemberSkill" %>
<%@ page import="edu.college.smartmatcher.web.TeamWorkflowServlet" %>
<%@ page import="edu.college.smartmatcher.web.FindTeammatesServlet" %>
<%
    String mode = String.valueOf(request.getAttribute("analysisMode"));
    boolean gapPage = "skill-gaps".equals(mode);
    String route = gapPage ? "/skill-gaps" : "/team-balance";
    ProjectOption selected = (ProjectOption) request.getAttribute("selectedProject");
    List<ProjectOption> projects = (List<ProjectOption>) request.getAttribute("projects");
    List<SkillRow> skillRows = selected == null ? java.util.Collections.emptyList() : selected.getSkillRows();
    List<MemberRow> members = selected == null ? java.util.Collections.emptyList() : selected.getMembers();
    List<CandidateDisplay> candidates = selected == null ? java.util.Collections.emptyList() : selected.getCandidates();
%>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title><%= gapPage ? "Skill Gap Analysis" : "Team Balance Analyzer" %> | SkillSync</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell">
    <header class="topbar"><p class="brand"><img class="topbar-logo" src="${pageContext.request.contextPath}/assets/skillsync-logo-symbol-transparent.png?v=20261018-transparent1" alt=""><span class="topbar-title">SkillSync</span></p><span class="topbar-context">Student workspace</span></header>
    <% if (gapPage) { %>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="skill-gaps"/></jsp:include>
    <% } else { %>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="team-balance"/></jsp:include>
    <% } %>
    <main>
        <p class="eyebrow">INTELLIGENT TEAM FORMATION</p>
        <h1><%= gapPage ? "Skill Gap Analysis" : "Team Balance Analyzer" %></h1>
        <p class="intro"><%= gapPage
                ? "See which project skills your team already covers and where a teammate can close a gap."
                : "Review skill coverage and contribution balance for your current project team." %></p>
        <% if (request.getAttribute("notice") != null) { %><p class="message" role="status"><%= TeamWorkflowServlet.escapeHtml(String.valueOf(request.getAttribute("notice"))) %></p><% } %>
        <% if (request.getAttribute("error") != null) { %><p class="error" role="alert"><%= TeamWorkflowServlet.escapeHtml(String.valueOf(request.getAttribute("error"))) %></p><% } %>

        <% if (projects == null || projects.isEmpty()) { %>
        <section class="panel analysis-empty">
            <span class="empty-state-icon" aria-hidden="true"><%= gapPage ? "◈" : "◎" %></span>
            <h2>Create a project to start analyzing your team</h2>
            <p>Analysis uses the required skills, proficiency records, and memberships already saved in SkillSync.</p>
            <a class="primary-link" href="${pageContext.request.contextPath}/projects">Create a project</a>
        </section>
        <% } else { %>
        <form class="analysis-project-picker panel" method="get" action="<%= request.getContextPath() + route %>">
            <label for="analysis-project">Project to analyze</label>
            <select id="analysis-project" name="project_id" required>
                <% for (ProjectOption option : projects) { %>
                <option value="<%= option.getProjectId() %>" <%= selected != null && selected.getProjectId() == option.getProjectId() ? "selected" : "" %>>
                    <%= FindTeammatesServlet.escapeHtml(option.getTitle()) %> · <%= TeamWorkflowServlet.displayStatus(option.getStatus()) %>
                </option>
                <% } %>
            </select>
            <button type="submit">Analyze project</button>
        </form>

        <% if (selected != null) { %>
        <section class="analysis-project-hero panel" aria-labelledby="analysis-project-title">
            <div class="analysis-project-heading">
                <div><p class="eyebrow">PROJECT · <%= FindTeammatesServlet.escapeHtml(selected.getCreatedDate()) %></p>
                    <h2 id="analysis-project-title"><%= FindTeammatesServlet.escapeHtml(selected.getTitle()) %></h2></div>
                <span class="status-badge status-<%= selected.getStatus().toLowerCase(java.util.Locale.ROOT) %>"><%= TeamWorkflowServlet.displayStatus(selected.getStatus()) %></span>
            </div>
            <div class="analysis-stat-grid">
                <% if (selected.isHasRequirements()) { %>
                <article><span>Team balance score</span><strong><%= selected.getBalanceScore().toPlainString() %>%</strong><small>Coverage, breadth, and contribution spread</small></article>
                <article><span>Weighted skill coverage</span><strong><%= selected.getCoveragePercent().toPlainString() %>%</strong><small>Best saved proficiency for each requirement</small></article>
                <article><span>Requirements covered</span><strong><%= selected.getFullCount() %> / <%= skillRows.size() %></strong><small><%= selected.getPartialCount() %> partial · <%= selected.getMissingCount() %> missing</small></article>
                <% } else { %>
                <article><span>Skill requirements</span><strong>Not set</strong><small>Add required skills to enable skill analysis</small></article>
                <article><span>Team members</span><strong><%= selected.getMembers().size() %></strong><small>Includes the project creator as team leader</small></article>
                <% } %>
                <article><span>Team capacity</span><strong><%= selected.getOccupiedSlots() %> / <%= selected.getTeamSize() %></strong><small><%= selected.getAvailableSeats() %> available after pending invitations</small></article>
            </div>
            <% if (!selected.isOpen()) { %><p class="analysis-state-note" role="status">This project is <%= TeamWorkflowServlet.displayStatus(selected.getStatus()).toLowerCase(java.util.Locale.ROOT) %>. You can review analysis, but team invitations are available only while it is open.</p>
            <% } else if (selected.getAvailableSeats() == 0) { %><p class="analysis-state-note" role="status">No seats are currently available. Pending invitations reserve seats until students respond.</p><% } %>
        </section>

        <% if (!selected.isHasRequirements()) { %>
        <section class="panel analysis-empty"><h2>No required skills are recorded</h2><p>Add project skills to see weighted coverage, skill gaps, and candidate impact.</p><a class="action-link" href="${pageContext.request.contextPath}/projects">Update project requirements →</a></section>
        <% } else if (gapPage) { %>
        <section class="analysis-panel panel" aria-labelledby="skill-gaps-heading">
            <div class="section-heading"><div><p class="eyebrow">PROJECT REQUIREMENTS</p><h2 id="skill-gaps-heading">Coverage by required skill</h2></div><span class="analysis-legend">Importance is weighted 1–5</span></div>
            <div class="analysis-skills">
                <% for (SkillRow row : skillRows) { %>
                <article class="analysis-skill-row">
                    <div class="analysis-skill-title"><div><strong><%= FindTeammatesServlet.escapeHtml(row.getName()) %></strong><span>Importance <%= row.getImportance() %> / 5</span></div>
                        <span class="analysis-status status-<%= row.getLevelClass() %>"><%= row.getLevel() %></span></div>
                    <div class="analysis-skill-meter" role="img" aria-label="<%= FindTeammatesServlet.escapeHtml(row.getName()) %>: <%= row.getLevel() %> coverage"><span style="width:<%= row.getBarPercent() %>%"></span></div>
                    <p><%= row.getContributorCount() == 0 ? "No current team member has this skill saved." : "Saved by " + row.getContributorsText() + " · strongest recorded proficiency " + edu.college.smartmatcher.intelligence.TeamIntelligenceService.proficiencyLabel(row.getProficiency()) %></p>
                </article>
                <% } %>
            </div>
        </section>
        <% } else { %>
        <section class="analysis-panel panel" aria-labelledby="balance-heading">
            <div class="section-heading"><div><p class="eyebrow">CURRENT TEAM</p><h2 id="balance-heading">Member strengths and contribution</h2></div><span class="analysis-legend">Contribution shares are based on saved required skills</span></div>
            <% if (members.isEmpty()) { %><p class="empty">No active team member profiles are available for this project.</p><% } else { %>
            <div class="analysis-member-grid">
                <% for (MemberRow member : members) { %>
                <article class="analysis-member-card">
                    <div class="teammate-student"><div class="teammate-avatar" aria-hidden="true"><%= FindTeammatesServlet.escapeHtml(member.getName().substring(0, 1).toUpperCase(java.util.Locale.ROOT)) %></div>
                        <div><h3><%= FindTeammatesServlet.escapeHtml(member.getName()) %></h3><p class="meta"><%= FindTeammatesServlet.escapeHtml(member.getDepartment()) %> · Year <%= member.getYearOfStudy() %> · <%= TeamWorkflowServlet.displayStatus(member.getRole()) %></p></div></div>
                    <div class="analysis-contribution"><strong><%= member.getContribution().toPlainString() %>%</strong><span>skill contribution share</span></div>
                    <div class="skill-chips"><% if (member.getSkills().isEmpty()) { %><span class="empty">No required skills saved</span><% } else { for (MemberSkill skill : member.getSkills()) { %>
                        <span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(skill.getName()) %><span class="proficiency-badge proficiency-<%= skill.getProficiencyClass() %>"><%= skill.getProficiency() %></span></span>
                    <% } } %></div>
                </article>
                <% } %>
            </div>
            <% } %>
            <div class="section-heading analysis-subheading"><div><p class="eyebrow">COVERAGE SIGNALS</p><h2>Skills the team can deliver</h2></div></div>
            <div class="analysis-skills compact-analysis-skills">
                <% for (SkillRow row : skillRows) { %>
                <article class="analysis-skill-row"><div class="analysis-skill-title"><div><strong><%= FindTeammatesServlet.escapeHtml(row.getName()) %></strong><span>Importance <%= row.getImportance() %> / 5</span></div><span class="analysis-status status-<%= row.getLevelClass() %>"><%= row.getLevel() %></span></div>
                    <div class="analysis-skill-meter" role="img" aria-label="<%= FindTeammatesServlet.escapeHtml(row.getName()) %>: <%= row.getLevel() %> coverage"><span style="width:<%= row.getBarPercent() %>%"></span></div></article>
                <% } %>
            </div>
        </section>
        <% } %>

        <% if (selected.isHasRequirements()) { %>
        <section class="analysis-panel panel" aria-labelledby="candidate-impact-heading">
            <div class="section-heading"><div><p class="eyebrow"><%= gapPage ? "GAP CLOSERS" : "TEAM IMPACT PREVIEW" %></p><h2 id="candidate-impact-heading"><%= gapPage ? "Students who can strengthen coverage" : "Preview a potential teammate" %></h2></div></div>
            <p class="analysis-explainer"><%= gapPage ? "Candidates are ordered by weighted coverage gain, then the number of newly covered gaps and weighted skill match." : "The preview adds one student's saved required skills to the current team and recalculates coverage and balance." %> Candidate details are limited to name, department, year, and project-related skills.</p>
            <% if (candidates.isEmpty()) { %><div class="dashboard-empty compact"><strong>No matching active student profiles yet</strong><p>Recommendations need saved skills that overlap this project's requirements.</p><a href="${pageContext.request.contextPath}/skills">Manage your skills</a></div>
            <% } else { %>
            <div class="analysis-candidate-grid">
                <% String csrf = (String) request.getAttribute("inviteCsrfToken");
                   for (CandidateDisplay candidate : candidates) { %>
                <article class="analysis-candidate-card">
                    <div class="teammate-student"><div class="teammate-avatar" aria-hidden="true"><%= FindTeammatesServlet.escapeHtml(candidate.getFullName().substring(0, 1).toUpperCase(java.util.Locale.ROOT)) %></div>
                        <div><h3><%= FindTeammatesServlet.escapeHtml(candidate.getFullName()) %></h3><p class="meta"><%= FindTeammatesServlet.escapeHtml(candidate.getDepartment()) %> · Year <%= candidate.getYearOfStudy() %></p></div>
                        <span class="analysis-match-score"><%= candidate.getMatchPercent().toPlainString() %>%<small>skill match</small></span></div>
                    <div class="analysis-impact-stats"><div><span>Team coverage</span><strong><%= candidate.getCurrentCoverage().toPlainString() %>% → <%= candidate.getProjectedCoverage().toPlainString() %>%</strong><small>+<%= candidate.getCoverageGain().toPlainString() %> points</small></div><div><span>Balance score</span><strong><%= candidate.getCurrentBalance().toPlainString() %>% → <%= candidate.getProjectedBalance().toPlainString() %>%</strong></div></div>
                    <div class="analysis-candidate-gaps"><strong>Matched required skills</strong><div class="skill-chips"><% for (CandidateSkill skill : candidate.getMatchedSkills()) { %><span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(skill.getName()) %><span class="proficiency-badge proficiency-<%= skill.getProficiencyClass() %>"><%= skill.getProficiency() %></span><small class="skill-priority">Priority <%= skill.getImportance() %>/5</small></span><% } %></div></div>
                    <% if (!candidate.getFillsMissingSkills().isEmpty()) { %><div class="analysis-candidate-gaps"><strong>Newly covered gaps</strong><div class="skill-chips"><% for (String skill : candidate.getFillsMissingSkills()) { %><span class="skill-chip"><%= FindTeammatesServlet.escapeHtml(skill) %></span><% } %></div></div><% } %>
                    <details class="analysis-explanation"><summary>Why this candidate?</summary><ul><% for (String explanation : candidate.getExplanations()) { %><li><%= FindTeammatesServlet.escapeHtml(explanation) %></li><% } %><li>Project match score weights each skill by its importance and saved proficiency.</li></ul></details>
                    <% if (candidate.isInvitationActive()) { %><button type="button" class="invitation-state" disabled><%= "ACCEPTED".equals(candidate.getInvitationStatus()) ? "Invitation accepted" : "Invitation sent" %></button>
                    <% } else if (candidate.hasActiveRequest()) { %><button type="button" class="invitation-state" disabled>Active request already exists</button>
                    <% } else if (candidate.isInviteAvailable()) { %>
                    <form class="analysis-invite-form" method="post" action="${pageContext.request.contextPath}/invitations">
                        <input type="hidden" name="action" value="invite"><input type="hidden" name="csrf_token" value="<%= FindTeammatesServlet.escapeHtml(csrf) %>">
                        <input type="hidden" name="project_id" value="<%= selected.getProjectId() %>"><input type="hidden" name="student_id" value="<%= candidate.getStudentId() %>">
                        <label>Optional message <input type="text" name="message" maxlength="1000" placeholder="Add a short note"></label>
                        <button type="submit">Invite student <span aria-hidden="true">→</span></button>
                    </form>
                    <% } else { %><button type="button" class="invitation-state" disabled><%= FindTeammatesServlet.escapeHtml(candidate.getInviteUnavailableReason()) %></button><% } %>
                </article>
                <% } %>
            </div>
            <% } %>
        </section>
        <% } %>
        <% } %>
        <% } %>
        <p class="analysis-footnote">Scores are transparent decision aids based on saved database records. They do not predict performance, availability, or interpersonal fit.</p>
    </main>
</div>
</body>
</html>

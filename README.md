# Smart Project Team Matcher

Java 17 Maven web application using Jakarta Servlets, JDBC, MySQL 8.x, and Apache Tomcat 10.1. It includes student registration and login, a data-backed student portfolio dashboard, My Skills, Projects, explainable teammate matching, team requests and invitations, Settings, admin moderation and analytics, logout, and a database health endpoint.

## Project structure

```text
database/
  schema.sql
  sample_data.sql
  projects_migration.sql
  team_join_migration.sql
  admin_moderation_migration.sql
  profile_foundation_migration.sql
src/main/java/edu/college/smartmatcher/
  config/DatabaseConnection.java
  web/AuthServlet.java
  web/StudentDashboardService.java
  web/HealthCheckServlet.java
  web/SkillServlet.java
  web/ProjectServlet.java
  web/FindTeammatesServlet.java
  web/TeamAnalysisServlet.java
  intelligence/TeamIntelligenceService.java
  web/TeamWorkflowServlet.java
  web/SettingsServlet.java
  web/AuthenticationFilter.java
  web/AdminSecurity.java
  web/AdminServlet.java
  web/GlobalSearchServlet.java
src/main/webapp/assets/
  theme.css
  theme.js
  skillsync-logo-original.png
  skillsync-logo-lockup-transparent.png
  skillsync-logo-lockup-light.png
  skillsync-logo-symbol-transparent.png
  skillsync-wordmark-transparent.png
  skillsync-wordmark-light.png
  skillsync-favicon.png
src/main/webapp/WEB-INF/
  account.jsp
  login.jsp
  register.jsp
  skills.jsp
  projects.jsp
  teammates.jsp
  team-analysis.jsp
  my-requests.jsp
  invitations.jsp
  team-management.jsp
  my-teams.jsp
  settings.jsp
  admin-dashboard.jsp
  admin-students.jsp
  admin-projects.jsp
  admin-history.jsp
  sidebar.jsp
  theme-toggle.jspf
  web.xml
pom.xml
```

The dashboard and module pages read the signed-in student ID from the existing HTTP session. Project creation, editing, and deletion enforce project ownership in SQL. JSP pages stay inside `WEB-INF` and are served through authenticated servlet routes.

## Database setup

## SkillSync 2.0 rollout status

### Phase 1 — Dashboard and profile foundation (working subset)

Implemented: responsive student and administrator dashboards use shared theme tokens, transparent SkillSync branding, and live MySQL records. The student dashboard shows owned active projects, active teams, invitations, recorded activity, saved skills, team skill coverage, and profile completion. Students can edit their own name, department, year, short bio, interests, and teamwork availability through a prepared, transactional, session-authenticated, CSRF-protected profile form. Email remains read-only. Admin charts use actual registration dates, project statuses, saved student skills, and team/project counts. The Phase 1 appearance and usability work includes authenticated global search, project filters and sorting, shared toasts, a search loading skeleton, and responsive reduced-motion-aware transitions.

The schema currently has no task, milestone, or due-date records, so those cards explain that tracking is not available yet instead of displaying invented values. Dashboard portfolio coverage remains a simple count; the new Phase 2 analysis routes provide weighted team coverage and balance based on saved project requirements and member skills. Dashboard teammate cards use the most recently saved `Match_Results` calculation and identify it as such.

### Phase 2 — Intelligent team formation (implemented)

Implemented: project recommendations now weight each required skill by its saved `Project_Skills.importance_level`; skill proficiency 1–2 contributes 20%, 3–4 contributes 60%, and 5 contributes 100% of that requirement's weight. Missing skills contribute zero. The result is normalized against total requirement importance, rounded to two decimals, and saved to the existing `Match_Results` table. Recommendation cards keep matched proficiency visible.

`/skill-gaps` shows each requirement's importance, strongest saved team proficiency, current contributors, and whether coverage is complete (intermediate or advanced), partial (beginner), or missing. Candidate cards sort by weighted coverage gain (so high-importance gaps matter more), then the number of newly covered gaps and match score. `/team-balance` shows current team member skill contributions and coverage. Both pages include an impact preview that adds one active candidate's existing skill profile in memory and recalculates the team metrics; it does not alter membership or project data. When the current project is open and has capacity, the existing CSRF-protected invitation workflow is available from a candidate card.

The team balance score is an explainable heuristic: 75% importance-weighted maximum team proficiency coverage, 15% importance-weighted requirement breadth represented at intermediate proficiency or above, and 10% normalized evenness of members' skill contribution shares (inverse Herfindahl concentration). An unrepresented requirement has zero coverage. Multiple members with the same skill do not raise the maximum coverage, though they are included in contributor and contribution distribution details. Projects with no requirements show an unavailable analysis state instead of a fabricated score. The score is a comparison aid and not a prediction of team performance.

Phase 3: project workspaces, tasks, milestones, Kanban, and computed progress. Phase 4: user-scoped notifications and project activity history, additional analytics, and accessibility polish. These later phases are not represented by mock screens or fabricated data.

## SkillSync branding and UI verification

The UI uses the supplied original SkillSync PNG at `src/main/webapp/assets/skillsync-logo-original.png`. Transparent symbol, lockup, wordmark, and favicon files are cropped from that artwork. Light-theme lockup and wordmark variants preserve the exact lettering and gradients while changing the original white text to dark ink for contrast. Shared JSPs load the assets through the application context path. CSS and JavaScript references use the `20261029-universal-login` cache version; increment that query value when publishing a later asset update.

The login page uses one universal email/password form for students and administrators. After BCrypt verification and the suspension check, `AuthServlet` compares the authenticated email with the server-configured `ADMIN_EMAIL` and redirects to `/admin` for that account or `/account` for other students. The page does not submit a role selector, and the administrator email is not hardcoded in the page or JavaScript.

For UI verification, build the WAR with `mvn clean package`, deploy `target/smart-project-team-matcher.war` to Tomcat 10.1, and open the application at `/smart-project-team-matcher/login`. Check the transparent logo on login and both dashboards, sidebar expanded/collapsed branding, favicon, the universal student/admin login form, show/hide password control, mobile layout, and each saved theme. Confirm that an administrator account redirects to `/admin` only after successful password verification and a student account redirects to `/account`.

For a fresh database, run `database/schema.sql`, then `database/admin_moderation_migration.sql`, followed by `database/sample_data.sql` in MySQL Workbench. The current schema includes bio, nullable availability, interests, and student-interest relationships. For an existing database, run `database/projects_migration.sql`, `database/team_join_migration.sql`, `database/admin_moderation_migration.sql`, and `database/profile_foundation_migration.sql` before deploying this version. These are incremental, one-time migrations; back up first and do not rerun successful `ALTER TABLE` migrations. They preserve existing records and related data. Existing `Teams` and `Team_Members` are reused for membership.

The Java application reads `DB_USER` and `DB_PASSWORD` from the environment. `DB_URL` is optional and defaults to `jdbc:mysql://localhost:3306/smart_project_matcher?serverTimezone=UTC`. Set these variables for the Tomcat process; do not put credentials in source files.

Set `ADMIN_EMAIL` in the Tomcat process environment to the email of the already existing administrator account. Email comparison is case-insensitive. Only that database account receives admin access; the registration flow reserves this address. Keep this environment variable on the server, never in source control. If it is unset or blank, admin access is disabled.

Project requirements are selected from the shared `Skills` catalog. Add available skills in My Skills first. New project requirements are stored at importance level 3 in `Project_Skills`.

Phase 2 adds no schema changes. It reads `Project_Skills.importance_level`, `Student_Skills.proficiency_level`, existing `Teams` / `Team_Members`, student suspension state, and existing requests/invitations. Do not run or repeat any database migration for this release.

Find Teammates lists only the authenticated student's own non-removed projects. For open projects with available seats, it compares required skills with other active students' saved skills. Suspended students, the owner, and existing project members are excluded. Proficiency levels 1–2 map to Beginner (weight 1/5), levels 3–4 to Intermediate (weight 3/5), and level 5 to Advanced (weight 5/5). The candidate score is `sum(importance × proficiency weight / 5) / sum(required skill importance) × 100`, rounded to two decimal places; missing skills contribute zero. Recommendations are sorted by score, then name. Existing invitations and active requests are shown as non-actionable states, and pending invitations reserve seats. Interest and availability do not currently affect the score. The invitation POST validates project ownership, capacity, target suspension status, active requests, membership and invitation duplicates, and a session CSRF token.

Team joining uses project requests (`Project_Applications`) and owner invitations (`Project_Invitations`). Students track their requests and invitations and view joined teams. Project creators manage applicants and capacity. Accepting either a request or an invitation locks the project row and inserts team membership in the same transaction; the creator counts toward `required_team_size`. Only open projects accept requests, invitations, or new members. The leader views show student profile details needed to evaluate an applicant, but never show email or credential data.

## Settings and accessibility preferences

The protected Settings page offers Midnight Purple (the default), Ocean Blue, Emerald Green, Crimson Red, Light, and AMOLED Black themes, plus Inter, Poppins, Roboto, Montserrat, and Nunito fonts. Preferences are stored in browser `localStorage` and apply across pages, refreshes, and login/logout in the same browser. A selected Google Font is requested with `display=swap`; system font fallbacks keep the site readable when the font service is unavailable. The early shared theme script applies saved values before page styles render. Authenticated pages use a common sidebar JSP fragment and a responsive top bar; the sidebar can collapse on desktop and open as a drawer on mobile. Search is restricted by the authenticated account: students can find their own projects and open projects from other students, while admins can search all non-removed projects. Student profile search omits email and moderation/credential fields and excludes suspended profiles and the signed-in user. Search can match project title, description, creator, and required skills, plus student name, department, bio, availability, interests, and saved skills.

On Projects, filters apply to the owner's projects and the open-project section. The latter remains open-only regardless of selected status. Toasts display server-provided messages; errors remain until dismissed, and successful messages fade after a short interval. The search skeleton appears only while the asynchronous search request is pending. This update reuses existing tables and requires no database migration. Do not rerun any migration for this update.

All JSP pages load the shared appearance assets through the application context path and include the same asset version query (currently `v=20261029-universal-login`) to bypass stale browser or proxy caches after deployment. When changing `theme.css` or `theme.js`, update this version in every JSP asset URL. The shell CSS styles the direct JSP output (`.shell > .topbar`, the included `nav[aria-label="Main navigation"]`, and `.shell > main`) without depending on JavaScript-added classes; JavaScript enhances those same elements with icons, global search, toast notifications, and responsive controls.

Password changes reuse the `Students.password_hash` column and BCrypt. The form requires the current password, validates the same 8-character minimum and 72 UTF-8-byte maximum as registration, verifies the confirmation, rejects reuse of the current password, and updates the hash in a transaction with prepared statements. A random session CSRF token protects the POST. No database migration is needed.

## Administration and moderation

The configured administrator signs in through the regular BCrypt login form and is redirected to `/admin`. The dashboard, student manager, project manager, and moderation history are at `/admin`, `/admin/students`, `/admin/projects`, and `/admin/history`. A shared authentication filter rechecks the account against MySQL on every protected request; suspensions invalidate existing sessions on their next request. Admin routes independently verify the account email against `ADMIN_EMAIL`. All moderation changes use POST, session CSRF tokens, prepared statements, transactions, and a persistent audit row. Project removal changes status to `REMOVED` and retains related data; restoration returns the project's prior lifecycle status.

## Build and run locally

Install Java 17 and Maven, then from the project root run:

```text
mvn clean package
```

Deploy `target/smart-project-team-matcher.war` to Tomcat 10.1's `webapps` folder or add the project to the Tomcat server in Eclipse. After Tomcat starts, open:

- `http://localhost:8080/smart-project-team-matcher/register` to create an account
- `http://localhost:8080/smart-project-team-matcher/login` to sign in
- `http://localhost:8080/smart-project-team-matcher/account` to view and edit the protected dashboard/profile
- `http://localhost:8080/smart-project-team-matcher/skills` to manage your skills
- `http://localhost:8080/smart-project-team-matcher/projects` to create, manage, and discover projects
- `http://localhost:8080/smart-project-team-matcher/teammates` to see your projects and skill-based teammate recommendations
- `http://localhost:8080/smart-project-team-matcher/skill-gaps` to inspect required skill coverage and candidate gap impact
- `http://localhost:8080/smart-project-team-matcher/team-balance` to inspect team balance and candidate impact previews
- `http://localhost:8080/smart-project-team-matcher/my-requests` to track or withdraw pending requests
- `http://localhost:8080/smart-project-team-matcher/invitations` to accept or decline leader invitations
- `http://localhost:8080/smart-project-team-matcher/my-teams` to view joined teams
- `http://localhost:8080/smart-project-team-matcher/team-management` to manage applicants and project capacity
- `http://localhost:8080/smart-project-team-matcher/settings` to personalize appearance and change your password
- `http://localhost:8080/smart-project-team-matcher/search?q=java` to use global search while signed in (JSON response)
- `http://localhost:8080/smart-project-team-matcher/admin` for the configured administrator dashboard
- `http://localhost:8080/smart-project-team-matcher/admin/students` to manage student suspensions
- `http://localhost:8080/smart-project-team-matcher/admin/projects` to remove or restore projects
- `http://localhost:8080/smart-project-team-matcher/admin/history` to review moderation audit records
- `http://localhost:8080/smart-project-team-matcher/health` to check database connectivity

On the student dashboard, edit your name, department, year, bio, interests, and availability. Verify the profile CSRF-protected form updates only the signed-in student's row, updates the sidebar name, and changes profile completion. Try an invalid year, oversized bio, unknown interest ID, signed-out request, and missing CSRF token. Compare active-project, team, invitation, coverage, and activity details with MySQL rows. Confirm task/deadline widgets state that tracking is unavailable until Phase 3. In the admin dashboard, compare the seven-day registration bars, status counts, popular skills, and project/team counts with read-only SQL queries. Recheck all six themes and five fonts on both dashboards and verify a narrow mobile viewport. On Settings, select each theme and font, save preferences, then refresh and navigate to another module to confirm they persist. Use Reset to Default to restore Midnight Purple and Inter. Check the sidebar toggle at desktop and mobile widths, including keyboard focus and Escape to close the mobile drawer. On login and registration, use the Show/Hide password controls and check the theme toggle. For password testing, try a wrong current password, mismatched confirmation, a password shorter than 8 characters, and a new password matching the current password; then change to a valid password and verify the old password fails and the new one works. Confirm that signed-out visits to `/settings` redirect to login and that a password form submission without its session CSRF token is rejected.

After building, deploy the newly generated WAR rather than reusing an old exploded deployment. In Eclipse, use **Clean** on the Tomcat server before publishing; for a standalone Tomcat installation, stop the server, replace the WAR, and start it again. The `theme.css` and `theme.js` query version forces browsers to fetch the updated assets. Verify in browser developer tools that both asset requests return HTTP 200 and include the current version query.

Phase 1 UX checks: sign in as a student and search for known project titles, descriptions, required skills, student names, departments, bio terms, and saved skills. Confirm profile results show only name, department/year, optional bio, and skills; they must not include email, suspended accounts, or the signed-in user. Search for a closed project created by someone else and a removed project; neither should appear. As admin, confirm the search includes non-removed closed projects and project links open their moderation cards. On Projects, apply Open and Closed status, one required skill, and both creation-date sort directions; check that the open-opportunities section never includes a closed project. Verify empty results, success/error toasts, the skeleton during a slow search request, and mobile layout without horizontal scrolling. Recheck all six themes and five fonts. Try `/search` signed out and with a suspended session. A successful Maven build does not verify a live database or browser rendering.

In Projects, sign in as a non-owner and submit an optional-message join request for an open project; check status under My Requests and withdraw it while pending. Sign in as the creator and shortlist/reject/accept requests in Team Leader Management. Find Teammates should list only the creator's projects; try a second student's project ID in the URL and in an invitation POST to confirm the other project is never shown and the server rejects the invitation. Verify matching uses real student skills, excludes the owner, banned students, and current members, shows missing required skills and pending invitation states, and sorts by weighted score. Invite a student, refresh Find Teammates, and confirm the card reads Invitation Sent; attempt the same invitation again, then accept or decline as that student. Check My Teams and confirm the leader is counted as a member and slots never exceed capacity. Try a closed project, a full team, a suspended target, a missing/invalid invitation CSRF token, and signed-out access. The current schema has one team capacity field, not separate minimum and maximum values. Also recheck registration, login, logout, skills, projects, and `/health`.

The dependency-free Phase 2 calculation checks are compiled with the project and can be run on Windows after packaging:

```powershell
mvn clean package
java -cp "target/test-classes;target/classes" edu.college.smartmatcher.intelligence.TeamIntelligenceChecks
```

The 14 checks cover exact and partial scores, no matches, proficiency bands, importance weighting, missing skills, no requirements, empty teams, full coverage, duplicate skill holders, critical gap fills, candidate impact previews, capacity/duplicate workflow eligibility, and project access rules. They are pure Java checks; they do not connect to MySQL.

For Phase 2, compare one project with manually calculated scores: multiply each requirement's importance by the candidate's proficiency fraction (20%, 60%, or 100%), divide by total requirement importance, and confirm two-decimal rounding. Verify missing skills contribute zero and that higher-importance requirements affect the result more. Open `/skill-gaps` and `/team-balance` as the project owner, change the project selector, and compare coverage and contributors with `Project_Skills`, `Student_Skills`, and active `Team_Members` rows. Confirm intermediate/advanced is shown as covered, beginner as partial, and missing as missing. Check candidate previews for newly filled gaps, coverage and balance changes; no membership row should be created by previewing. Test a project with no required skills, no active members, an invite-pending seat, a full/closed project, and candidates who are already members or have active requests/invitations. Try a different student's `project_id` and a removed project in either route and confirm no project data is returned. Invitation forms should appear only for eligible candidates and continue through the existing invitation POST with CSRF protection. `/skill-gaps` and `/team-balance` redirect signed-out visitors to login. These pure Java checks do not replace live MySQL/Tomcat checks.

For moderation tests, verify a normal student receives HTTP 403 on `/admin` and `/admin/students`, while the configured administrator can open the four admin pages. As admin, search for another student, ban them with a reason, confirm a Moderation History entry appears, then verify their next protected request ends their session and login shows the suspension message. Unban them and verify they can sign in again. Confirm the admin's own Ban button is unavailable and a forged self-ban is rejected. Remove a project with a reason and confirm it disappears from student Projects, Find Teammates, My Requests, Invitations, and Team Leader Management while its related rows remain in MySQL. Restore it and confirm its previous status and visibility return. Submit moderation forms without a valid CSRF token and confirm they are rejected.

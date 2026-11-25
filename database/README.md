# Database setup (MySQL 8.x)

For a fresh database, run `schema.sql`, `admin_moderation_migration.sql`, then `sample_data.sql`. The updated fresh schema includes student bio, teamwork availability, the `Interests` catalogue, and `Student_Interests`. For an existing database, run `projects_migration.sql`, `team_join_migration.sql`, `admin_moderation_migration.sql`, and `profile_foundation_migration.sql` in that order; run `sample_data.sql` only if you want demonstration data. These scripts use `smart_project_matcher`; none drops an existing database or table. The project migration adds the `CLOSED` status while preserving current rows. The team-joining migration adds `SHORTLISTED` and an optional request message to `Project_Applications`, and creates `Project_Invitations`; it preserves existing applications and memberships.

For admin moderation on an existing database, first back up `smart_project_matcher`, then run `admin_moderation_migration.sql` once after the project and team-joining migrations. It adds student suspension metadata, the `REMOVED` project status and restoration metadata, and the persistent `Moderation_Audit_Log`. It does not delete students, projects, teams, applications, invitations, or match records. The script uses one-time `ALTER TABLE` statements; do not run it again after it succeeds.

For the SkillSync 2.0 student dashboard on an existing database, back up the schema and run `profile_foundation_migration.sql` once after the moderation migration. It adds nullable student bio and availability fields, the interest catalogue, and the many-to-many `Student_Interests` table. The profile migration preserves all existing rows; existing students' availability remains unspecified until they choose a value. It seeds only shared interest choices, not student profile or activity data. Do not run this `ALTER TABLE` migration a second time. Fresh databases already get these fields from `schema.sql`, so do not apply the profile migration after the updated base schema.

Set the Tomcat environment variable `ADMIN_EMAIL` to the email of the existing administrator account. Only that account can open `/admin`, `/admin/students`, `/admin/projects`, and `/admin/history`. If the variable is unset or blank, admin pages deny access.

## MySQL Workbench steps

1. Start the MySQL Server and connect in Workbench.
2. Choose **File → Open SQL Script**, open `schema.sql`, and click the lightning-bolt **Execute** button.
3. Open `projects_migration.sql` and execute it. It safely adds the `CLOSED` project status for databases where `Projects` already existed. Running it on a fresh database is also safe.
4. Open `team_join_migration.sql` and execute it. It adds the request message/status and invitation table without deleting current records. For a fresh install, `schema.sql` already includes these additions, and this migration is safe to run again.
5. Back up your database. Open `admin_moderation_migration.sql` and execute it once. It adds suspension/removal metadata and the moderation audit table without deleting existing data.
6. For an existing database, back up and run `profile_foundation_migration.sql` once. Skip this step for a fresh database created with the updated `schema.sql`.
7. Open `sample_data.sql` and execute it only when you want the demonstration records.
8. Refresh the **Schemas** panel, expand `smart_project_matcher` → **Tables**, and confirm `Project_Invitations`, `Moderation_Audit_Log`, `Interests`, and `Student_Interests` appear alongside the existing tables.
9. Run the verification queries at the bottom of `sample_data.sql` to inspect example records.

The sample script can be rerun without duplicating its example entities and mappings. The demonstration hash strings in `Students.password_hash` are not login credentials. For real accounts, use an adaptive password hashing algorithm (for example BCrypt or Argon2); never save plain-text passwords.

## Existing Phase 1 database note

The earlier project schema used different singular table names (`Student`, `Skill`, `Project`, and others). This requested schema uses the plural table names in `schema.sql`, plus `Project_Invitations` for this workflow. The scripts do not rename, drop, or delete the old tables, so both sets may appear in an already initialized database. The current Java servlets use the plural names for their queries.

## Relationship overview

- `Students` create `Projects`; project creators are referenced by `Projects.creator_id`.
- `Student_Skills` maps students to skills. A student-skill pair can appear only once.
- `Project_Skills` maps project requirements to skills. A project-skill pair can appear only once.
- Projects use the existing `Project_Skills` relationship for required skills; each selected requirement is saved with importance level 3.
- `Project_Applications` records one application per student/project pair.
- `Project_Applications` stores request messages and supports `PENDING`, `SHORTLISTED`, `ACCEPTED`, `REJECTED`, and `WITHDRAWN`.
- `Project_Invitations` stores owner-to-student invitations and prevents duplicate project/student invitation rows.
- `Teams` belong to projects; `Team_Members` prevents the same student being added twice to one team.
- `Match_Results` stores one score per student/project pair in this initial design.
- `Teams` and `Team_Members` are reused for accepted applicants and invitations; the application locks the project row while checking capacity and inserting a member.
- `Feedback` is constrained to team members and allows one rating per member per team.

MySQL 8 enforces the check constraints for study year, proficiency, importance, score, team size, and rating. Foreign keys keep references valid; deleting a student or project cascades through dependent records where appropriate, while deleting a project creator is restricted.

## Legacy table archive and optional cleanup

The current application uses the newer plural-named tables. The ten legacy tables are `student`, `skill`, `student_skill`, `interest`, `student_interest`, `availability`, `project`, `project_requirement`, `team`, and `team_member`. The cleanup SQL is a manually run script and is never run by the application.

Before considering cleanup, restore the legacy rows into a separate test schema and compare the archive:

1. Configure a MySQL login path outside the repository. From the MySQL Server `bin` directory, run `mysql_config_editor set --login-path=skillsync-local --host=127.0.0.1 --user=YOUR_DB_USER --password` and enter the password at its prompt. The scripts contain no password.
2. From the project folder, run `powershell -File .\database\legacy_archive_restore_verify.ps1`. If MySQL is installed elsewhere, pass its `bin` folder with `-MySqlBinDirectory`.
3. The helper checks for 10 legacy tables, 14 current application tables, 24 total tables, and 48 legacy rows before exporting. It writes `database/legacy_tables_archive.sql`, creates the separate `smart_project_matcher_legacy_archive_test` schema, restores the dump there, then compares per-table row counts, `CHECKSUM TABLE` results, and foreign-key-column counts. It refuses to overwrite an existing dump or test schema.
4. Review `database/legacy_archive_verify.sql` output and the helper's final `ARCHIVE VERIFIED` message. The helper must finish successfully before the cleanup file is considered.
5. Review `database/cleanup_legacy_tables.sql`. Its confirmation token is `NULL` by default; only after checking the archive and dependencies should you manually edit it to `I_VERIFIED_THE_ARCHIVE_AND_APPROVE_THE_10_LEGACY_DROPS` and execute it in Workbench. Its preflight also requires the expected 24-table layout, matching source/archive row counts, no external foreign keys or detected views/routines/triggers/events depending on legacy tables, and a compatible child-before-parent drop order. If a guard fails, it runs a status `SELECT` instead of a `DROP TABLE`.

The archive/restore helper and SQL files have **not** been run against the live database by this project workspace. Treat the 48-row total as an expected count until the helper reports success. The generated archive contains private legacy records and is ignored by the nested `database/.gitignore`; keep it out of source control and store a second backup securely. Do not execute the cleanup script until the helper reports success and you have a separate tested backup. To roll back a completed cleanup, restore `legacy_tables_archive.sql` into `smart_project_matcher` with the MySQL client while connected to that database; do not rerun application migrations. If only some legacy tables were removed, restore the dump into a separate recovery schema first, then reconcile safely. Keep the archive and test schema until the restored legacy tables and their relationships have been validated.

The archive helper intentionally refuses to proceed if a legacy foreign key points outside the ten legacy tables. This preserves a fail-safe boundary: inspect that dependency and decide whether its referenced table also needs to be included before creating the archive. It only reads from `smart_project_matcher`; it creates the archive file and test schema separately. It never drops source tables.

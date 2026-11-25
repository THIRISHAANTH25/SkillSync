-- MANUAL CLEANUP SCRIPT. This file is generated for review and is not executed automatically.
-- Run only after legacy_archive_restore_verify.ps1 reports ARCHIVE VERIFIED and after review.
-- It drops only the ten named legacy tables. No migration or current table is changed.
-- Do not disable FOREIGN_KEY_CHECKS.

USE smart_project_matcher;

-- Deliberate human confirmation gate: edit this exact value only after reviewing the archive.
SET @confirm_legacy_cleanup = NULL;

-- Report the preflight facts without exposing student/project records.
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = 'smart_project_matcher' AND TABLE_TYPE = 'BASE TABLE'
       AND TABLE_NAME IN ('Students','Skills','Student_Skills','Interests','Student_Interests',
          'Projects','Project_Skills','Project_Applications','Project_Invitations','Teams',
          'Team_Members','Match_Results','Feedback','Moderation_Audit_Log')) AS current_tables_expected_14,
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = 'smart_project_matcher' AND TABLE_TYPE = 'BASE TABLE'
       AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
          'availability','project','project_requirement','team','team_member')) AS legacy_tables_expected_10,
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = 'smart_project_matcher' AND TABLE_TYPE = 'BASE TABLE') AS source_base_table_count,
    @confirm_legacy_cleanup AS confirmation_token;

-- Compare per-table counts. The restore helper additionally compares CHECKSUM TABLE values.
SELECT 'student' AS table_name,
       (SELECT COUNT(*) FROM smart_project_matcher.student) AS source_rows,
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student) AS archive_rows
UNION ALL SELECT 'skill',
       (SELECT COUNT(*) FROM smart_project_matcher.skill),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.skill)
UNION ALL SELECT 'student_skill',
       (SELECT COUNT(*) FROM smart_project_matcher.student_skill),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_skill)
UNION ALL SELECT 'interest',
       (SELECT COUNT(*) FROM smart_project_matcher.interest),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.interest)
UNION ALL SELECT 'student_interest',
       (SELECT COUNT(*) FROM smart_project_matcher.student_interest),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_interest)
UNION ALL SELECT 'availability',
       (SELECT COUNT(*) FROM smart_project_matcher.availability),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.availability)
UNION ALL SELECT 'project',
       (SELECT COUNT(*) FROM smart_project_matcher.project),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project)
UNION ALL SELECT 'project_requirement',
       (SELECT COUNT(*) FROM smart_project_matcher.project_requirement),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project_requirement)
UNION ALL SELECT 'team',
       (SELECT COUNT(*) FROM smart_project_matcher.team),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team)
UNION ALL SELECT 'team_member',
       (SELECT COUNT(*) FROM smart_project_matcher.team_member),
       (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team_member)
ORDER BY table_name;

-- Review the dependency graph. The ordered drop below assumes children precede parents.
SELECT TABLE_NAME AS child_table, REFERENCED_TABLE_NAME AS parent_table,
       CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'smart_project_matcher'
  AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
      'availability','project','project_requirement','team','team_member')
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;

-- These metadata checks catch database views and programmable objects that may still
-- mention a legacy table. Any match blocks cleanup; inspect it before proceeding.
SELECT VIEW_SCHEMA, VIEW_NAME, TABLE_SCHEMA AS referenced_schema, TABLE_NAME AS referenced_table
FROM information_schema.VIEW_TABLE_USAGE
WHERE TABLE_SCHEMA = 'smart_project_matcher'
  AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
      'availability','project','project_requirement','team','team_member')
ORDER BY VIEW_SCHEMA, VIEW_NAME;

SELECT TRIGGER_SCHEMA, TRIGGER_NAME, EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE
FROM information_schema.TRIGGERS
WHERE LOWER(COALESCE(ACTION_STATEMENT, '')) REGEXP
  '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)'
ORDER BY TRIGGER_SCHEMA, TRIGGER_NAME;

SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE
FROM information_schema.ROUTINES
WHERE LOWER(COALESCE(ROUTINE_DEFINITION, '')) REGEXP
  '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)'
ORDER BY ROUTINE_SCHEMA, ROUTINE_NAME;

SELECT EVENT_SCHEMA, EVENT_NAME
FROM information_schema.EVENTS
WHERE LOWER(COALESCE(EVENT_DEFINITION, '')) REGEXP
  '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)'
ORDER BY EVENT_SCHEMA, EVENT_NAME;

-- DROP runs only when every guard passes AND the human confirmation token is set.
-- Any unexpected current-table count, row count, table count, FK dependency, or missing
-- archive makes the prepared statement a harmless SELECT instead of a DROP.
SET @cleanup_ready = (
    @confirm_legacy_cleanup = 'I_VERIFIED_THE_ARCHIVE_AND_APPROVE_THE_10_LEGACY_DROPS'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA='smart_project_matcher' AND TABLE_TYPE='BASE TABLE'
           AND TABLE_NAME IN ('Students','Skills','Student_Skills','Interests','Student_Interests',
             'Projects','Project_Skills','Project_Applications','Project_Invitations','Teams',
             'Team_Members','Match_Results','Feedback','Moderation_Audit_Log')) = 14
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA='smart_project_matcher' AND TABLE_TYPE='BASE TABLE'
           AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')) = 10
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA='smart_project_matcher' AND TABLE_TYPE='BASE TABLE') = 24
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA='smart_project_matcher_legacy_archive_test' AND TABLE_TYPE='BASE TABLE'
           AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')) = 10
    AND (SELECT COUNT(*) FROM smart_project_matcher.student) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student)
    AND (SELECT COUNT(*) FROM smart_project_matcher.skill) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.skill)
    AND (SELECT COUNT(*) FROM smart_project_matcher.student_skill) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_skill)
    AND (SELECT COUNT(*) FROM smart_project_matcher.interest) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.interest)
    AND (SELECT COUNT(*) FROM smart_project_matcher.student_interest) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_interest)
    AND (SELECT COUNT(*) FROM smart_project_matcher.availability) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.availability)
    AND (SELECT COUNT(*) FROM smart_project_matcher.project) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project)
    AND (SELECT COUNT(*) FROM smart_project_matcher.project_requirement) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project_requirement)
    AND (SELECT COUNT(*) FROM smart_project_matcher.team) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team)
    AND (SELECT COUNT(*) FROM smart_project_matcher.team_member) = (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team_member)
    AND ((SELECT COUNT(*) FROM smart_project_matcher.student) +
         (SELECT COUNT(*) FROM smart_project_matcher.skill) +
         (SELECT COUNT(*) FROM smart_project_matcher.student_skill) +
         (SELECT COUNT(*) FROM smart_project_matcher.interest) +
         (SELECT COUNT(*) FROM smart_project_matcher.student_interest) +
         (SELECT COUNT(*) FROM smart_project_matcher.availability) +
         (SELECT COUNT(*) FROM smart_project_matcher.project) +
         (SELECT COUNT(*) FROM smart_project_matcher.project_requirement) +
         (SELECT COUNT(*) FROM smart_project_matcher.team) +
         (SELECT COUNT(*) FROM smart_project_matcher.team_member)) = 48
    AND ((SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.skill) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_skill) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.interest) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.student_interest) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.availability) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.project_requirement) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team) +
         (SELECT COUNT(*) FROM smart_project_matcher_legacy_archive_test.team_member)) = 48
    AND (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
         WHERE REFERENCED_TABLE_SCHEMA='smart_project_matcher'
           AND REFERENCED_TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')
           AND (TABLE_SCHEMA <> 'smart_project_matcher'
                OR TABLE_NAME NOT IN ('student','skill','student_skill','interest','student_interest',
                  'availability','project','project_requirement','team','team_member'))) = 0
    AND (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA='smart_project_matcher'
           AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')
           AND REFERENCED_TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')
           AND FIELD(TABLE_NAME,'student_skill','student_interest','availability','project_requirement',
             'team_member','team','project','skill','interest','student')
               >= FIELD(REFERENCED_TABLE_NAME,'student_skill','student_interest','availability',
             'project_requirement','team_member','team','project','skill','interest','student')) = 0
    AND (SELECT COUNT(*) FROM information_schema.VIEW_TABLE_USAGE
         WHERE TABLE_SCHEMA='smart_project_matcher'
           AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
             'availability','project','project_requirement','team','team_member')) = 0
    AND (SELECT COUNT(*) FROM information_schema.TRIGGERS
         WHERE LOWER(COALESCE(ACTION_STATEMENT,'')) REGEXP
           '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)') = 0
    AND (SELECT COUNT(*) FROM information_schema.ROUTINES
         WHERE LOWER(COALESCE(ROUTINE_DEFINITION,'')) REGEXP
             '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)') = 0
    AND (SELECT COUNT(*) FROM information_schema.EVENTS
         WHERE LOWER(COALESCE(EVENT_DEFINITION,'')) REGEXP
             '(^|[^[:alnum:]_])(student|skill|student_skill|interest|student_interest|availability|project|project_requirement|team|team_member)([^[:alnum:]_]|$)') = 0
);

SET @cleanup_sql = IF(
    @cleanup_ready,
    'DROP TABLE `smart_project_matcher`.`student_skill`, `smart_project_matcher`.`student_interest`, `smart_project_matcher`.`availability`, `smart_project_matcher`.`project_requirement`, `smart_project_matcher`.`team_member`, `smart_project_matcher`.`team`, `smart_project_matcher`.`project`, `smart_project_matcher`.`skill`, `smart_project_matcher`.`interest`, `smart_project_matcher`.`student`',
    'SELECT ''ABORTED: preflight failed or confirmation token was not set; no legacy table was dropped.'' AS cleanup_status'
);
PREPARE cleanup_statement FROM @cleanup_sql;
EXECUTE cleanup_statement;
DEALLOCATE PREPARE cleanup_statement;

-- Expected only if cleanup was approved and completed: current table count 14, legacy count 0.
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA='smart_project_matcher' AND TABLE_TYPE='BASE TABLE'
       AND TABLE_NAME IN ('Students','Skills','Student_Skills','Interests','Student_Interests',
          'Projects','Project_Skills','Project_Applications','Project_Invitations','Teams',
          'Team_Members','Match_Results','Feedback','Moderation_Audit_Log')) AS current_tables_remaining,
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA='smart_project_matcher' AND TABLE_TYPE='BASE TABLE'
       AND TABLE_NAME IN ('student','skill','student_skill','interest','student_interest',
          'availability','project','project_requirement','team','team_member')) AS legacy_tables_remaining;

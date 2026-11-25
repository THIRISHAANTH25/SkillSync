-- Run against smart_project_matcher_legacy_archive_test after restoring the dump.
-- These queries show counts and foreign-key metadata only; they do not display student data.

SELECT
    COUNT(*) AS expected_legacy_tables_found,
    CASE WHEN COUNT(*) = 10 THEN 'PASS' ELSE 'FAIL' END AS table_check
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
      'student', 'skill', 'student_skill', 'interest', 'student_interest',
      'availability', 'project', 'project_requirement', 'team', 'team_member'
  );

SELECT 'student' AS table_name, COUNT(*) AS archived_rows FROM student
UNION ALL SELECT 'skill', COUNT(*) FROM skill
UNION ALL SELECT 'student_skill', COUNT(*) FROM student_skill
UNION ALL SELECT 'interest', COUNT(*) FROM interest
UNION ALL SELECT 'student_interest', COUNT(*) FROM student_interest
UNION ALL SELECT 'availability', COUNT(*) FROM availability
UNION ALL SELECT 'project', COUNT(*) FROM project
UNION ALL SELECT 'project_requirement', COUNT(*) FROM project_requirement
UNION ALL SELECT 'team', COUNT(*) FROM team
UNION ALL SELECT 'team_member', COUNT(*) FROM team_member
ORDER BY table_name;

SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;

SELECT
    (SELECT COUNT(*) FROM student) +
    (SELECT COUNT(*) FROM skill) +
    (SELECT COUNT(*) FROM student_skill) +
    (SELECT COUNT(*) FROM interest) +
    (SELECT COUNT(*) FROM student_interest) +
    (SELECT COUNT(*) FROM availability) +
    (SELECT COUNT(*) FROM project) +
    (SELECT COUNT(*) FROM project_requirement) +
    (SELECT COUNT(*) FROM team) +
    (SELECT COUNT(*) FROM team_member) AS total_legacy_rows,
    CASE WHEN
        (SELECT COUNT(*) FROM student) +
        (SELECT COUNT(*) FROM skill) +
        (SELECT COUNT(*) FROM student_skill) +
        (SELECT COUNT(*) FROM interest) +
        (SELECT COUNT(*) FROM student_interest) +
        (SELECT COUNT(*) FROM availability) +
        (SELECT COUNT(*) FROM project) +
        (SELECT COUNT(*) FROM project_requirement) +
        (SELECT COUNT(*) FROM team) +
        (SELECT COUNT(*) FROM team_member) = 48
    THEN 'PASS' ELSE 'FAIL' END AS row_check;

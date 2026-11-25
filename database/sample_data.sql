-- Example records for the Smart Project Team Matcher schema.
-- Safe to rerun: natural keys and NOT EXISTS checks prevent duplicate sample rows.
USE smart_project_matcher;

-- These SHA-256 strings are demonstration-only values, not usable login credentials.
-- A real registration flow should use a password hashing library such as BCrypt/Argon2.
INSERT INTO Students (full_name, email, password_hash, department, year_of_study) VALUES
('Aarav Shah', 'aarav.shah@example.edu', 'cb3aa94fb31a724b68fe8142b7c58056176e97d6d5012705349fb5d50e364191', 'Computer Science', 3),
('Mira Nair', 'mira.nair@example.edu', 'ac396244b11f706e2c0a68128595bebfe3bf79b3a5266c5da977206a5391811e', 'Computer Science', 3),
('Kabir Rao', 'kabir.rao@example.edu', 'ee7f090f6cf08a9b1b58d2656c6393d8c025f7d256e3e18884ff07c79a6f52c9', 'Information Technology', 2),
('Ananya Iyer', 'ananya.iyer@example.edu', '26a6147cf7b171d1cf7b93359708be274c5d6bd955c9b1f4349cbb7d7cd78a6c', 'Computer Science', 4),
('Dev Mehta', 'dev.mehta@example.edu', '4aedf80a51549eb80a86a80a726171c9e5c7e2bd5fa43b099a2096d9c4163194', 'Information Technology', 3),
('Sara Khan', 'sara.khan@example.edu', '2d9b8a6af86b12e52c76718b8507a00d72880c4f960792b27921653d0ebf2d77', 'Computer Science', 2)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), department = VALUES(department), year_of_study = VALUES(year_of_study);

INSERT INTO Skills (skill_name, description) VALUES
('Java', 'Object-oriented programming with the Java language.'),
('Python', 'Programming for scripting, data work, and prototypes.'),
('SQL', 'Relational database querying and schema design.'),
('UI/UX Design', 'User interface design and usability evaluation.'),
('Data Analysis', 'Cleaning, exploring, and interpreting data.'),
('Project Management', 'Planning, coordinating, and tracking project work.')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO Student_Skills (student_id, skill_id, proficiency_level, experience_years)
SELECT s.student_id, k.skill_id, x.proficiency_level, x.experience_years
FROM (
    SELECT 'aarav.shah@example.edu' email, 'Java' skill_name, 4 proficiency_level, 2.0 experience_years UNION ALL
    SELECT 'aarav.shah@example.edu', 'SQL', 3, 1.0 UNION ALL
    SELECT 'mira.nair@example.edu', 'UI/UX Design', 5, 2.5 UNION ALL
    SELECT 'mira.nair@example.edu', 'Python', 3, 1.0 UNION ALL
    SELECT 'kabir.rao@example.edu', 'Python', 4, 1.5 UNION ALL
    SELECT 'kabir.rao@example.edu', 'Data Analysis', 4, 1.0 UNION ALL
    SELECT 'ananya.iyer@example.edu', 'Project Management', 4, 2.0 UNION ALL
    SELECT 'ananya.iyer@example.edu', 'Java', 3, 1.0 UNION ALL
    SELECT 'dev.mehta@example.edu', 'SQL', 4, 2.0 UNION ALL
    SELECT 'dev.mehta@example.edu', 'Data Analysis', 3, 1.0 UNION ALL
    SELECT 'sara.khan@example.edu', 'UI/UX Design', 3, 1.0 UNION ALL
    SELECT 'sara.khan@example.edu', 'Java', 3, 0.5
) AS x
JOIN Students s ON s.email = x.email
JOIN Skills k ON k.skill_name = x.skill_name
ON DUPLICATE KEY UPDATE proficiency_level = VALUES(proficiency_level), experience_years = VALUES(experience_years);

-- The sample project creator is a student because the requested schema has no separate faculty table.
INSERT INTO Projects (title, description, creator_id, required_team_size, status)
SELECT 'Accessible Campus Project Finder',
       'Build an accessible web tool that helps students discover campus projects and find teammates.',
       s.student_id, 3, 'OPEN'
FROM Students s
WHERE s.email = 'ananya.iyer@example.edu'
  AND NOT EXISTS (
      SELECT 1 FROM Projects p
      WHERE p.creator_id = s.student_id AND p.title = 'Accessible Campus Project Finder'
  );

INSERT INTO Project_Skills (project_id, skill_id, importance_level)
SELECT p.project_id, k.skill_id, x.importance_level
FROM (
    SELECT 'Java' skill_name, 5 importance_level UNION ALL
    SELECT 'UI/UX Design', 5 UNION ALL
    SELECT 'SQL', 3 UNION ALL
    SELECT 'Project Management', 2
) AS x
JOIN Projects p ON p.title = 'Accessible Campus Project Finder'
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
JOIN Skills k ON k.skill_name = x.skill_name
ON DUPLICATE KEY UPDATE importance_level = VALUES(importance_level);

INSERT INTO Project_Applications (student_id, project_id, application_date, status)
SELECT s.student_id, p.project_id, x.application_date, x.status
FROM (
    SELECT 'aarav.shah@example.edu' email, '2026-10-10 10:00:00' application_date, 'ACCEPTED' status UNION ALL
    SELECT 'mira.nair@example.edu', '2026-10-10 11:30:00', 'ACCEPTED' UNION ALL
    SELECT 'kabir.rao@example.edu', '2026-10-11 09:15:00', 'ACCEPTED' UNION ALL
    SELECT 'dev.mehta@example.edu', '2026-10-12 14:00:00', 'PENDING'
) AS x
JOIN Students s ON s.email = x.email
JOIN Projects p ON p.title = 'Accessible Campus Project Finder'
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
WHERE NOT EXISTS (
    SELECT 1 FROM Project_Applications existing
    WHERE existing.student_id = s.student_id AND existing.project_id = p.project_id
);

INSERT INTO Teams (project_id, team_name, created_at, status)
SELECT p.project_id, 'Campus Access Team', '2026-10-13 12:00:00', 'ACTIVE'
FROM Projects p
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
WHERE p.title = 'Accessible Campus Project Finder'
  AND NOT EXISTS (
      SELECT 1 FROM Teams t WHERE t.project_id = p.project_id AND t.team_name = 'Campus Access Team'
  );

INSERT INTO Team_Members (team_id, student_id, member_role)
SELECT t.team_id, s.student_id, x.member_role
FROM (
    SELECT 'aarav.shah@example.edu' email, 'LEADER' member_role UNION ALL
    SELECT 'mira.nair@example.edu', 'MEMBER' UNION ALL
    SELECT 'kabir.rao@example.edu', 'MEMBER'
) AS x
JOIN Students s ON s.email = x.email
JOIN Teams t ON t.team_name = 'Campus Access Team'
JOIN Projects p ON p.project_id = t.project_id AND p.title = 'Accessible Campus Project Finder'
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
ON DUPLICATE KEY UPDATE member_role = VALUES(member_role);

INSERT INTO Match_Results (student_id, project_id, matching_score, recommendation_date)
SELECT s.student_id, p.project_id, x.matching_score, x.recommendation_date
FROM (
    SELECT 'aarav.shah@example.edu' email, 94.50 matching_score, '2026-10-09 09:00:00' recommendation_date UNION ALL
    SELECT 'mira.nair@example.edu', 91.25, '2026-10-09 09:00:00' UNION ALL
    SELECT 'kabir.rao@example.edu', 87.00, '2026-10-09 09:00:00' UNION ALL
    SELECT 'sara.khan@example.edu', 76.75, '2026-10-09 09:00:00'
) AS x
JOIN Students s ON s.email = x.email
JOIN Projects p ON p.title = 'Accessible Campus Project Finder'
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
ON DUPLICATE KEY UPDATE matching_score = VALUES(matching_score), recommendation_date = VALUES(recommendation_date);

INSERT INTO Feedback (team_id, student_id, rating, comments, submission_date)
SELECT t.team_id, s.student_id, x.rating, x.comments, x.submission_date
FROM (
    SELECT 'aarav.shah@example.edu' email, 5 rating, 'Clear goals and a helpful mix of skills.' comments, '2026-10-16 16:00:00' submission_date UNION ALL
    SELECT 'mira.nair@example.edu', 4, 'Good collaboration; weekly check-ins helped.', '2026-10-16 16:30:00' UNION ALL
    SELECT 'kabir.rao@example.edu', 5, 'The team balanced design and technical work well.', '2026-10-16 17:00:00'
) AS x
JOIN Students s ON s.email = x.email
JOIN Team_Members tm ON tm.student_id = s.student_id
JOIN Teams t ON t.team_id = tm.team_id AND t.team_name = 'Campus Access Team'
JOIN Projects p ON p.project_id = t.project_id AND p.title = 'Accessible Campus Project Finder'
JOIN Students creator ON creator.student_id = p.creator_id AND creator.email = 'ananya.iyer@example.edu'
ON DUPLICATE KEY UPDATE rating = VALUES(rating), comments = VALUES(comments), submission_date = VALUES(submission_date);

-- Verification examples: expected to return records from every part of the workflow.
SELECT COUNT(*) AS student_count FROM Students;
SELECT COUNT(*) AS skill_count FROM Skills;

SELECT s.full_name, k.skill_name, ss.proficiency_level, ss.experience_years
FROM Students s
JOIN Student_Skills ss ON ss.student_id = s.student_id
JOIN Skills k ON k.skill_id = ss.skill_id
ORDER BY s.full_name, k.skill_name;

SELECT p.title, creator.full_name AS creator, k.skill_name, ps.importance_level
FROM Projects p
JOIN Students creator ON creator.student_id = p.creator_id
JOIN Project_Skills ps ON ps.project_id = p.project_id
JOIN Skills k ON k.skill_id = ps.skill_id
WHERE p.title = 'Accessible Campus Project Finder';

SELECT s.full_name, p.title, pa.status AS application_status, mr.matching_score
FROM Project_Applications pa
JOIN Students s ON s.student_id = pa.student_id
JOIN Projects p ON p.project_id = pa.project_id
LEFT JOIN Match_Results mr ON mr.student_id = s.student_id AND mr.project_id = p.project_id
ORDER BY mr.matching_score DESC;

SELECT t.team_name, s.full_name, tm.member_role, f.rating, f.comments
FROM Teams t
JOIN Team_Members tm ON tm.team_id = t.team_id
JOIN Students s ON s.student_id = tm.student_id
LEFT JOIN Feedback f ON f.team_id = tm.team_id AND f.student_id = tm.student_id
ORDER BY t.team_name, tm.member_role DESC, s.full_name;

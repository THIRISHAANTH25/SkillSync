-- Smart Project Team Matcher database schema (MySQL 8.x).
-- Safe to run more than once: this script does not drop existing databases or tables.

CREATE DATABASE IF NOT EXISTS smart_project_matcher
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE smart_project_matcher;

-- Base entities are created first so that dependent tables can reference them.
CREATE TABLE IF NOT EXISTS Students (
    student_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL COMMENT 'Hash only; never store a plain-text password.',
    department VARCHAR(120) NOT NULL,
    year_of_study TINYINT UNSIGNED NOT NULL,
    bio VARCHAR(1000) NULL,
    availability_status ENUM('AVAILABLE', 'LIMITED', 'UNAVAILABLE') NULL DEFAULT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    UNIQUE KEY uq_students_email (email),
    KEY idx_students_department_year (department, year_of_study),
    CONSTRAINT chk_students_year CHECK (year_of_study BETWEEN 1 AND 8)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Skills (
    skill_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    skill_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    PRIMARY KEY (skill_id),
    UNIQUE KEY uq_skills_name (skill_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Interests (
    interest_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    interest_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (interest_id),
    UNIQUE KEY uq_interests_name (interest_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Student_Interests (
    student_id BIGINT UNSIGNED NOT NULL,
    interest_id BIGINT UNSIGNED NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, interest_id),
    KEY idx_student_interests_interest (interest_id, student_id),
    CONSTRAINT fk_student_interests_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_student_interests_interest FOREIGN KEY (interest_id)
        REFERENCES Interests (interest_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO Interests (interest_name) VALUES
    ('Artificial Intelligence'), ('Web Development'), ('Mobile Applications'),
    ('Cybersecurity'), ('Data Science'), ('Cloud Computing'),
    ('Design and Accessibility'), ('Internet of Things'), ('Education Technology'),
    ('Health Technology'), ('Sustainability'), ('Game Development');

CREATE TABLE IF NOT EXISTS Projects (
    project_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    creator_id BIGINT UNSIGNED NOT NULL,
    required_team_size TINYINT UNSIGNED NOT NULL,
    status ENUM('OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id),
    UNIQUE KEY uq_projects_creator_title (creator_id, title),
    KEY idx_projects_status_created (status, created_at),
    KEY idx_projects_creator (creator_id),
    CONSTRAINT fk_projects_creator FOREIGN KEY (creator_id)
        REFERENCES Students (student_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_projects_team_size CHECK (required_team_size BETWEEN 2 AND 20)
) ENGINE=InnoDB;

-- Link tables model many-to-many relationships and use composite primary keys
-- to prevent duplicate associations.
CREATE TABLE IF NOT EXISTS Student_Skills (
    student_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    proficiency_level TINYINT UNSIGNED NOT NULL,
    experience_years DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    PRIMARY KEY (student_id, skill_id),
    KEY idx_student_skills_skill (skill_id, student_id),
    CONSTRAINT fk_student_skills_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_student_skills_skill FOREIGN KEY (skill_id)
        REFERENCES Skills (skill_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_student_skills_proficiency CHECK (proficiency_level BETWEEN 1 AND 5),
    CONSTRAINT chk_student_skills_experience CHECK (experience_years >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Project_Skills (
    project_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    importance_level TINYINT UNSIGNED NOT NULL DEFAULT 3,
    PRIMARY KEY (project_id, skill_id),
    KEY idx_project_skills_skill (skill_id, project_id),
    CONSTRAINT fk_project_skills_project FOREIGN KEY (project_id)
        REFERENCES Projects (project_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_project_skills_skill FOREIGN KEY (skill_id)
        REFERENCES Skills (skill_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_project_skills_importance CHECK (importance_level BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Project_Applications (
    application_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_message VARCHAR(1000) NULL,
    status ENUM('PENDING', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN') NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (application_id),
    UNIQUE KEY uq_project_applications_student_project (student_id, project_id),
    KEY idx_project_applications_project_status (project_id, status),
    KEY idx_project_applications_student_date (student_id, application_date),
    CONSTRAINT fk_project_applications_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_project_applications_project FOREIGN KEY (project_id)
        REFERENCES Projects (project_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- Owners send invitations to recommended students. One row per project/student pair
-- prevents duplicate active invitations; declined/revoked rows can be reused later.
CREATE TABLE IF NOT EXISTS Project_Invitations (
    invitation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    invited_by BIGINT UNSIGNED NOT NULL,
    invitation_message VARCHAR(1000) NULL,
    status ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED') NOT NULL DEFAULT 'PENDING',
    invited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    PRIMARY KEY (invitation_id),
    UNIQUE KEY uq_project_invitations_project_student (project_id, student_id),
    KEY idx_project_invitations_student_status (student_id, status, invited_at),
    KEY idx_project_invitations_project_status (project_id, status),
    CONSTRAINT fk_project_invitations_project FOREIGN KEY (project_id)
        REFERENCES Projects (project_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_project_invitations_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_project_invitations_invited_by FOREIGN KEY (invited_by)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Teams (
    team_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    team_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status ENUM('FORMING', 'ACTIVE', 'COMPLETED', 'DISBANDED') NOT NULL DEFAULT 'FORMING',
    PRIMARY KEY (team_id),
    UNIQUE KEY uq_teams_project_name (project_id, team_name),
    KEY idx_teams_project_status (project_id, status),
    CONSTRAINT fk_teams_project FOREIGN KEY (project_id)
        REFERENCES Projects (project_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Team_Members (
    team_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    member_role ENUM('LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, student_id),
    KEY idx_team_members_student (student_id, team_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id)
        REFERENCES Teams (team_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_team_members_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Match_Results (
    match_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    matching_score DECIMAL(5,2) NOT NULL,
    recommendation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (match_id),
    UNIQUE KEY uq_match_results_student_project (student_id, project_id),
    KEY idx_match_results_project_score (project_id, matching_score),
    KEY idx_match_results_student_date (student_id, recommendation_date),
    CONSTRAINT fk_match_results_student FOREIGN KEY (student_id)
        REFERENCES Students (student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_match_results_project FOREIGN KEY (project_id)
        REFERENCES Projects (project_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_match_results_score CHECK (matching_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Feedback (
    feedback_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    team_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    comments TEXT NULL,
    submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (feedback_id),
    UNIQUE KEY uq_feedback_team_student (team_id, student_id),
    KEY idx_feedback_student_date (student_id, submission_date),
    CONSTRAINT fk_feedback_team_member FOREIGN KEY (team_id, student_id)
        REFERENCES Team_Members (team_id, student_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

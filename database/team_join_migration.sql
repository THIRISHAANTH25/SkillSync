-- Safe migration for requests, owner invitations, and team-member selection.
-- Run against the existing smart_project_matcher database before deploying the new pages.
-- No existing students, skills, projects, applications, or memberships are deleted.
USE smart_project_matcher;

-- SHORTLISTED is added to the existing application lifecycle without changing rows.
ALTER TABLE Project_Applications
    MODIFY COLUMN status ENUM('PENDING', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    NOT NULL DEFAULT 'PENDING';

-- Add the optional student message only when it is not already present.
SET @team_join_message_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Project_Applications'
       AND COLUMN_NAME = 'request_message') = 0,
    'ALTER TABLE Project_Applications ADD COLUMN request_message VARCHAR(1000) NULL AFTER application_date',
    'SELECT 1'
);
PREPARE team_join_message_stmt FROM @team_join_message_ddl;
EXECUTE team_join_message_stmt;
DEALLOCATE PREPARE team_join_message_stmt;

-- One invitation row per project/student pair. Terminal rows may be reused for a later invitation,
-- while the unique key guarantees there can never be two active invitations for that pair.
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

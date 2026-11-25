-- Incremental moderation migration for the existing smart_project_matcher database.
-- Run once after backing up the database. No rows or related records are deleted.
USE smart_project_matcher;

ALTER TABLE Students
    ADD COLUMN is_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN suspension_reason VARCHAR(1000) NULL,
    ADD COLUMN suspended_at TIMESTAMP NULL,
    ADD COLUMN suspended_by BIGINT UNSIGNED NULL,
    ADD KEY idx_students_suspended_registered (is_suspended, registered_at),
    ADD CONSTRAINT fk_students_suspended_by FOREIGN KEY (suspended_by)
        REFERENCES Students (student_id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE Projects
    MODIFY COLUMN status ENUM('OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REMOVED')
        NOT NULL DEFAULT 'OPEN',
    ADD COLUMN moderation_previous_status ENUM('OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NULL,
    ADD COLUMN removed_at TIMESTAMP NULL,
    ADD COLUMN removed_by BIGINT UNSIGNED NULL,
    ADD COLUMN removal_reason VARCHAR(1000) NULL,
    ADD KEY idx_projects_removed_at (status, removed_at),
    ADD CONSTRAINT fk_projects_removed_by FOREIGN KEY (removed_by)
        REFERENCES Students (student_id) ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE IF NOT EXISTS Moderation_Audit_Log (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_student_id BIGINT UNSIGNED NOT NULL,
    action_type ENUM('STUDENT_BAN', 'STUDENT_UNBAN', 'PROJECT_REMOVE', 'PROJECT_RESTORE') NOT NULL,
    target_type ENUM('STUDENT', 'PROJECT') NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_moderation_audit_created (created_at, audit_id),
    KEY idx_moderation_audit_target (target_type, target_id, created_at),
    KEY idx_moderation_audit_actor (actor_student_id, created_at),
    CONSTRAINT fk_moderation_audit_actor FOREIGN KEY (actor_student_id)
        REFERENCES Students (student_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

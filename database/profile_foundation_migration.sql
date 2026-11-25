-- SkillSync 2.0 Phase 1: student profile foundation.
-- Apply once to an existing smart_project_matcher database after schema.sql.
-- This is additive: existing student and project rows are preserved.
USE smart_project_matcher;

ALTER TABLE Students
    ADD COLUMN bio VARCHAR(1000) NULL,
    ADD COLUMN availability_status ENUM('AVAILABLE', 'LIMITED', 'UNAVAILABLE') NULL DEFAULT NULL;

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

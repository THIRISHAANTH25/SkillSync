-- Run once on an existing smart_project_matcher database before deploying the Projects module.
-- This widens the status enum without dropping tables or changing existing status values.
USE smart_project_matcher;

ALTER TABLE Projects
    MODIFY COLUMN status ENUM('OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    NOT NULL DEFAULT 'OPEN';
